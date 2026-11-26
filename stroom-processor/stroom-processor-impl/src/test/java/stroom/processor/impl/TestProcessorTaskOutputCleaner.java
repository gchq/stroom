/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.processor.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.processor.shared.TaskStatus;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestProcessorTaskOutputCleaner {

    private static final long TASK_ID = 1_000;
    private static final long META_ID = 20;

    @Mock
    private MetaService metaService;
    @Mock
    private ProcessorTaskDao processorTaskDao;

    private final ProcessorTask task = ProcessorTask.builder()
            .id(TASK_ID)
            .metaId(META_ID)
            .status(TaskStatus.COMPLETE)
            .build();
    private final Meta meta = Meta.builder().id(META_ID).build();
    private final Processor processor = Processor.builder().id(1).pipelineUuid("pipeline").build();
    // The streams that there are, other than the one that is processed.
    private final Map<Long, Meta> streams = new TreeMap<>();

    private ProcessorTaskOutputCleaner cleaner;

    @BeforeEach
    void beforeEach() {
        Mockito.when(metaService.find(Mockito.any())).thenAnswer(invocation ->
                ResultPage.createUnboundedList(streams
                        .values()
                        .stream()
                        .filter(stream -> !Status.DELETED.equals(stream.getStatus()))
                        .toList()));
        Mockito.when(metaService.getMeta(Mockito.anyLong(), Mockito.eq(true))).thenAnswer(invocation ->
                streams.get(invocation.<Long>getArgument(0)));
        Mockito.when(metaService.delete(Mockito.anyLong(), Mockito.anyBoolean())).thenAnswer(invocation -> {
            assertThat(invocation.<Boolean>getArgument(1))
                    .describedAs("locked streams are deleted too")
                    .isFalse();
            final Meta stream = streams.get(invocation.<Long>getArgument(0));
            if (stream == null || Status.DELETED.equals(stream.getStatus())) {
                return 0;
            }
            streams.put(stream.getId(), stream.copy().status(Status.DELETED).build());
            return 1;
        });
        givenTasks();
        cleaner = new ProcessorTaskOutputCleaner(metaService, processorTaskDao);
    }

    @Test
    void earlierRunsOutputIsDeletedWhetherLockedOrNot() {
        givenStream(1, TASK_ID, Status.LOCKED);
        givenStream(2, TASK_ID, Status.UNLOCKED);

        assertThat(cleaner.clearEarlierRuns(task, () -> true)).isTrue();

        assertThat(getDeleted()).containsExactly(1L, 2L);
        final ArgumentCaptor<FindMetaCriteria> captor = ArgumentCaptor.forClass(FindMetaCriteria.class);
        Mockito.verify(metaService, Mockito.atLeastOnce()).find(captor.capture());
        assertThat(captor.getValue().getExpression().toString())
                .contains(MetaFields.PARENT_ID.getFldName() + " = " + META_ID)
                .contains(MetaFields.META_PROCESSOR_TASK_ID.getFldName() + " = " + TASK_ID);
    }

    @Test
    void earlierRunsThatWroteNothing() {
        assertThat(cleaner.clearEarlierRuns(task, () -> true)).isTrue();

        assertThat(getDeleted()).isEmpty();
    }

    @Test
    void runThatHasLostItsTaskDeletesNothing() {
        givenStream(1, TASK_ID, Status.UNLOCKED);

        assertThat(cleaner.clearEarlierRuns(task, () -> false)).isFalse();

        assertThat(getDeleted()).isEmpty();
    }

    @Test
    void runThatHasLostItsTaskIsFoundOutEvenIfThereIsNothingToDelete() {
        assertThat(cleaner.clearEarlierRuns(task, () -> false)).isFalse();
    }

    @Test
    void runThatStallsNeverDeletesOutputOfTheRunThatReplacedIt() {
        givenStream(1, TASK_ID, Status.UNLOCKED);

        // It still has its task when it looks, but then stalls while the run that replaces it opens a stream.
        final AtomicInteger count = new AtomicInteger();
        assertThat(cleaner.clearEarlierRuns(task, () -> {
            if (count.incrementAndGet() == 1) {
                givenStream(2, TASK_ID, Status.LOCKED);
                return true;
            }
            return false;
        })).isFalse();

        assertThat(getDeleted()).containsExactly(1L);
    }

    @Test
    void failureToDeleteEarlierOutputIsNotIgnored() {
        givenStream(1, TASK_ID, Status.UNLOCKED);
        Mockito.doThrow(new RuntimeException("Expected")).when(metaService).delete(1, false);

        assertThatThrownBy(() -> cleaner.clearEarlierRuns(task, () -> true)).hasMessage("Expected");
    }

    @Test
    void earlierOutputThatThereIsNoPermissionToDeleteIsNotIgnored() {
        givenStream(1, TASK_ID, Status.UNLOCKED);
        Mockito.doReturn(0).when(metaService).delete(1, false);

        assertThatThrownBy(() -> cleaner.clearEarlierRuns(task, () -> true))
                .hasMessage("Unable to delete stream 1");
    }

    @Test
    void deletingStreamsThatHaveGone() {
        givenStream(1, TASK_ID, Status.DELETED);

        cleaner.delete(List.of(1L, 2L));

        assertThat(getDeleted()).containsExactly(1L);
    }

    @Test
    void failureToDeleteOneStreamDoesNotKeepTheOthers() {
        givenStream(1, TASK_ID, Status.UNLOCKED);
        givenStream(2, TASK_ID, Status.UNLOCKED);
        givenStream(3, TASK_ID, Status.UNLOCKED);
        Mockito.doThrow(new RuntimeException("First")).when(metaService).delete(1, false);
        Mockito.doThrow(new RuntimeException("Second")).when(metaService).delete(2, false);

        assertThatThrownBy(() -> cleaner.delete(List.of(1L, 2L, 3L)))
                .hasMessage("First")
                .satisfies(e -> assertThat(e.getSuppressed()).hasSize(1));

        assertThat(getDeleted()).containsExactly(3L);
    }

    @Test
    void outputOfOlderTasksThatHaveFinishedIsSuperseded() {
        final Set<TaskStatus> finished = EnumSet.of(TaskStatus.COMPLETE, TaskStatus.ABORTED, TaskStatus.DELETED);
        final List<ProcessorTask> tasks = new ArrayList<>();
        final List<Long> expected = new ArrayList<>();
        for (final TaskStatus status : TaskStatus.values()) {
            final long id = status.ordinal() + 1;
            tasks.add(task(id, status));
            givenStream(id, id, Status.UNLOCKED);
            if (finished.contains(status)) {
                expected.add(id);
            }
        }
        givenStream(TASK_ID, TASK_ID, Status.UNLOCKED);
        givenTasks(tasks.toArray(new ProcessorTask[0]));

        cleaner.clearSuperseded(task, meta, processor);

        assertThat(getDeleted()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void outputOfANewerTaskThatHasFinishedWellIsKeptInsteadOfOwn() {
        givenStream(1, TASK_ID, Status.UNLOCKED);
        givenStream(2, TASK_ID + 1, Status.UNLOCKED);
        givenStream(3, TASK_ID + 2, Status.UNLOCKED);
        givenTasks(task(TASK_ID + 1, TaskStatus.COMPLETE), task(TASK_ID + 2, TaskStatus.COMPLETE));

        cleaner.clearSuperseded(task, meta, processor);

        assertThat(getDeleted()).containsExactly(1L, 2L);
    }

    @Test
    void outputOfAHeldTaskIsKeptButDoesNotMakeOthersOutOfDate() {
        givenStream(1, TASK_ID, Status.UNLOCKED);
        givenStream(2, TASK_ID - 1, Status.UNLOCKED);
        givenStream(3, TASK_ID + 1, Status.UNLOCKED);
        givenTasks(task(TASK_ID - 1, TaskStatus.COMMITTING), task(TASK_ID + 1, TaskStatus.COMMITTING));

        cleaner.clearSuperseded(task, meta, processor);

        // The newer one may yet be put back, so this task's output is kept as well.
        assertThat(getDeleted()).isEmpty();
    }

    @Test
    void heldTaskThatIsCompleteClearsWhatItHasMadeOutOfDate() {
        final ProcessorTask held = task(TASK_ID + 1, TaskStatus.COMPLETE)
                .copy()
                .processorFilter(ProcessorFilter.builder().id(1).processor(processor).build())
                .build();
        givenStream(1, TASK_ID, Status.UNLOCKED);
        givenStream(2, held.getId(), Status.UNLOCKED);
        // The held task is found first when it is looked up.
        givenTasks(held, task(TASK_ID, TaskStatus.COMPLETE));
        Mockito.when(metaService.getMeta(META_ID, true)).thenReturn(meta);

        cleaner.clearSuperseded(held.getId());

        assertThat(getDeleted()).containsExactly(1L);
    }

    @Test
    void heldTaskThatHasGoneHasNothingToClear() {
        givenStream(1, TASK_ID, Status.UNLOCKED);
        givenTasks();

        cleaner.clearSuperseded(TASK_ID + 1);

        assertThat(getDeleted()).isEmpty();
    }

    @Test
    void outputOfANewerTaskThatHasNotFinishedWellIsNotKeptInsteadOfOwn() {
        givenStream(1, TASK_ID, Status.UNLOCKED);
        givenStream(2, TASK_ID + 1, Status.UNLOCKED);
        givenStream(3, TASK_ID + 2, Status.UNLOCKED);
        givenTasks(task(TASK_ID + 1, TaskStatus.ABORTED), task(TASK_ID + 2, TaskStatus.RETRY));

        cleaner.clearSuperseded(task, meta, processor);

        assertThat(getDeleted()).containsExactly(2L);
    }

    @Test
    void tasksThatFinishTogetherKeepTheOutputOfTheNewer() {
        final ProcessorTask newer = task(TASK_ID + 1, TaskStatus.COMPLETE);
        givenStream(1, TASK_ID, Status.UNLOCKED);
        givenStream(2, newer.getId(), Status.UNLOCKED);
        // Both have been set to complete before either clears up.
        givenTasks(task(TASK_ID, TaskStatus.COMPLETE), newer);

        cleaner.clearSuperseded(task, meta, processor);
        cleaner.clearSuperseded(newer, meta, processor);

        assertThat(getDeleted()).containsExactly(1L);
    }

    @Test
    void outputOfARunningTaskOfAnotherProcessorIsKept() {
        givenStream(1, TASK_ID - 1, Status.LOCKED);
        // It is found, although it is of another processor.
        givenTasks(task(TASK_ID - 1, TaskStatus.PROCESSING));

        cleaner.clearSuperseded(task, meta, processor);

        assertThat(getDeleted()).isEmpty();
        final ArgumentCaptor<ExpressionCriteria> captor = ArgumentCaptor.forClass(ExpressionCriteria.class);
        Mockito.verify(processorTaskDao).find(captor.capture());
        assertThat(captor.getValue().getExpression().toString())
                .doesNotContain("Processor")
                .contains(ProcessorTaskFields.PIPELINE.getFldName())
                .contains(processor.getPipelineUuid());
    }

    @Test
    void outputOfTasksThatNoLongerExistIsSuperseded() {
        givenStream(1, TASK_ID + 99, Status.LOCKED);
        givenStream(2, null, Status.UNLOCKED);

        cleaner.clearSuperseded(task, meta, processor);

        assertThat(getDeleted()).containsExactly(1L, 2L);
    }

    @Test
    void ownOutputIsNotSupersededIfThereIsNothingElse() {
        givenStream(1, TASK_ID, Status.UNLOCKED);
        givenTasks(task.copy().status(TaskStatus.COMPLETE).build());

        cleaner.clearSuperseded(task, meta, processor);

        assertThat(getDeleted()).isEmpty();
        // There's no need to look.
        Mockito.verifyNoInteractions(processorTaskDao);
    }

    private ProcessorTask task(final long id, final TaskStatus status) {
        return ProcessorTask.builder().id(id).metaId(META_ID).status(status).build();
    }

    private void givenStream(final long id, final Long taskId, final Status status) {
        streams.put(id, Meta.builder().id(id).processorTaskId(taskId).status(status).build());
    }

    private void givenTasks(final ProcessorTask... tasks) {
        Mockito.when(processorTaskDao.find(Mockito.any(ExpressionCriteria.class)))
                .thenReturn(ResultPage.createUnboundedList(Arrays.asList(tasks)));
        Mockito.clearInvocations(processorTaskDao);
    }

    private List<Long> getDeleted() {
        return streams
                .values()
                .stream()
                .filter(stream -> Status.DELETED.equals(stream.getStatus()))
                .map(Meta::getId)
                .toList();
    }
}
