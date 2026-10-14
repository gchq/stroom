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

package stroom.analytics.impl;

import stroom.analytics.impl.ScheduledExecutorService.ExecutionResult;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionScheduleRequest;
import stroom.analytics.shared.ExecutionTracker;
import stroom.docref.DocRef;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskTerminatedException;
import stroom.util.scheduler.Trigger;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Severity;
import stroom.util.shared.UserRef;
import stroom.util.shared.scheduler.Schedule;
import stroom.util.shared.scheduler.ScheduleType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestScheduledExecutorService {

    private static final String DOC_TYPE = "TestDoc";
    private static final String NODE_NAME = "node1";
    private static final UserRef USER_1 = UserRef.forUserUuid("user1");
    private static final UserRef USER_2 = UserRef.forUserUuid("user2");

    @Mock
    private ExecutionScheduleDao mockExecutionScheduleDao;
    @Mock
    private NodeInfo mockNodeInfo;
    @Mock
    private SecurityContext mockSecurityContext;

    private final Map<String, List<ExecutionSchedule>> schedulesByDocUuid = new HashMap<>();
    private final Map<String, ExecutionSchedule> schedulesByUuid = new HashMap<>();
    private UserRef currentUser;

    private ScheduledExecutorService<String> scheduledExecutorService;

    @BeforeEach
    void setUp() {
        lenient().when(mockNodeInfo.getThisNodeName())
                .thenReturn(NODE_NAME);

        // Track the run-as user so the tests can check each schedule runs as its own user.
        lenient().doAnswer(invocation -> {
            final UserRef previousUser = currentUser;
            currentUser = invocation.getArgument(0);
            try {
                invocation.<Runnable>getArgument(1).run();
            } finally {
                currentUser = previousUser;
            }
            return null;
        }).when(mockSecurityContext).asUser(any(UserRef.class), any(Runnable.class));
        lenient().doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(mockSecurityContext).useAsRead(any(Runnable.class));
        lenient().when(mockSecurityContext.asProcessingUserResult(any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());

        lenient().when(mockExecutionScheduleDao.fetchExecutionSchedule(any()))
                .thenAnswer(invocation -> {
                    final ExecutionScheduleRequest request = invocation.getArgument(0);
                    return new ResultPage<>(schedulesByDocUuid.getOrDefault(
                            request.getOwnerDocRef().getUuid(),
                            List.of()));
                });
        lenient().when(mockExecutionScheduleDao.fetchScheduleByUuid(any()))
                .thenAnswer(invocation ->
                        Optional.ofNullable(schedulesByUuid.get(invocation.<String>getArgument(0))));
        // An instant schedule is disabled once it has run, which stops the execution loop.
        lenient().when(mockExecutionScheduleDao.updateExecutionSchedule(any()))
                .thenAnswer(invocation -> {
                    final ExecutionSchedule executionSchedule = invocation.getArgument(0);
                    schedulesByUuid.put(executionSchedule.getUuid(), executionSchedule);
                    return executionSchedule;
                });

        scheduledExecutorService = new ScheduledExecutorService<>(
                new SimpleTaskContextFactory(),
                mockNodeInfo,
                mockSecurityContext,
                mockExecutionScheduleDao,
                () -> null);
    }

    @Test
    void testExec_runsSequentiallyOnCallingThread() {
        addSchedule("doc1", "schedule1a", USER_1);
        addSchedule("doc1", "schedule1b", USER_2);
        addSchedule("doc2", "schedule2a", USER_2);
        final TestExecutable executable = new TestExecutable(List.of("doc1", "doc2"));

        scheduledExecutorService.exec(executable);

        assertThat(executable.executions)
                .containsExactly(
                        new Execution("doc1", "schedule1a", USER_1, Thread.currentThread()),
                        new Execution("doc1", "schedule1b", USER_2, Thread.currentThread()),
                        new Execution("doc2", "schedule2a", USER_2, Thread.currentThread()));
        assertThat(executable.tidiedUpDocs)
                .containsExactly("doc1", "doc2");
    }

    @Test
    void testExec_noDocs() {
        final TestExecutable executable = new TestExecutable(List.of());

        scheduledExecutorService.exec(executable);

        assertThat(executable.executions)
                .isEmpty();
        assertThat(executable.tidiedUpDocs)
                .isEmpty();
    }

    @Test
    void testExec_failingDocDoesNotStopOtherDocs() {
        addSchedule("doc1", "schedule1", USER_1);
        addSchedule("doc2", "schedule2", USER_2);
        final TestExecutable executable = new TestExecutable(List.of("doc1", "doc2"));
        executable.reloadFailureDoc = "doc1";

        scheduledExecutorService.exec(executable);

        assertThat(executable.executions)
                .containsExactly(new Execution("doc2", "schedule2", USER_2, Thread.currentThread()));
        assertThat(executable.tidiedUpDocs)
                .containsExactly("doc1", "doc2");
    }

    @Test
    void testExec_failingScheduleDoesNotStopOtherSchedules() {
        addSchedule("doc1", "schedule1a", USER_1);
        addSchedule("doc1", "schedule1b", USER_2);
        final TestExecutable executable = new TestExecutable(List.of("doc1"));
        executable.reloadFailureCount = 1;

        scheduledExecutorService.exec(executable);

        assertThat(executable.executions)
                .containsExactly(new Execution("doc1", "schedule1b", USER_2, Thread.currentThread()));
    }

    @Test
    void testExec_terminationStopsProcessing() {
        addSchedule("doc1", "schedule1", USER_1);
        addSchedule("doc2", "schedule2", USER_2);
        final TestExecutable executable = new TestExecutable(List.of("doc1", "doc2"));
        executable.terminateOnDoc = "doc1";

        scheduledExecutorService.exec(executable);

        assertThat(executable.executions)
                .isEmpty();
        assertThat(executable.beforeProcessDocs)
                .containsExactly("doc1");
        // Termination should leave the schedule enabled so that it runs again.
        assertThat(schedulesByUuid.get("schedule1").isEnabled())
                .isTrue();
        assertThat(executable.tidiedUpDocs)
                .isNull();
    }

    @Test
    void testExecuteNow_runsOnCallingThread() {
        final ExecutionSchedule executionSchedule = addSchedule("doc1", "schedule1", USER_2);
        final TestExecutable executable = new TestExecutable(List.of());

        scheduledExecutorService.executeNow(executionSchedule, executable);

        assertThat(executable.executions)
                .containsExactly(new Execution("doc1", "schedule1", USER_2, Thread.currentThread()));
        assertThat(schedulesByUuid.get("schedule1").isEnabled())
                .isFalse();
    }

    @Test
    void testExecuteNow_disabledSchedule() {
        final ExecutionSchedule executionSchedule = addSchedule("doc1", "schedule1", USER_1);
        schedulesByUuid.put("schedule1", executionSchedule.copy().enabled(false).build());
        final TestExecutable executable = new TestExecutable(List.of());

        scheduledExecutorService.executeNow(executionSchedule, executable);

        assertThat(executable.executions)
                .isEmpty();
        verify(mockExecutionScheduleDao, never()).addExecutionHistory(any());
    }

    @Test
    void testExecuteNow_failureIsNotThrown() {
        final ExecutionSchedule executionSchedule = addSchedule("doc1", "schedule1", USER_1);
        when(mockExecutionScheduleDao.fetchScheduleByUuid("schedule1"))
                .thenThrow(new RuntimeException("Boom"));
        final TestExecutable executable = new TestExecutable(List.of());

        scheduledExecutorService.executeNow(executionSchedule, executable);

        assertThat(executable.executions)
                .isEmpty();
    }

    private ExecutionSchedule addSchedule(final String docUuid,
                                          final String scheduleUuid,
                                          final UserRef runAsUser) {
        final ExecutionSchedule executionSchedule = ExecutionSchedule.builder()
                .uuid(scheduleUuid)
                .name(scheduleUuid)
                .enabled(true)
                .nodeName(NODE_NAME)
                .schedule(Schedule.builder().type(ScheduleType.INSTANT).build())
                .contiguous(false)
                .owningDoc(new DocRef(DOC_TYPE, docUuid))
                .runAsUser(runAsUser)
                .build();
        schedulesByDocUuid.computeIfAbsent(docUuid, k -> new ArrayList<>())
                .add(executionSchedule);
        schedulesByUuid.put(scheduleUuid, executionSchedule);
        return executionSchedule;
    }


    // --------------------------------------------------------------------------------


    private record Execution(String doc,
                             String scheduleUuid,
                             UserRef runAsUser,
                             Thread thread) {

    }


    // --------------------------------------------------------------------------------


    private class TestExecutable implements ScheduledExecutable<String> {

        private final List<String> docs;
        private final List<Execution> executions = new ArrayList<>();
        private final List<String> beforeProcessDocs = new ArrayList<>();
        private List<String> tidiedUpDocs;
        private String reloadFailureDoc;
        private int reloadFailureCount;
        private String terminateOnDoc;

        private TestExecutable(final List<String> docs) {
            this.docs = docs;
        }

        @Override
        public ExecutionResult run(final String doc,
                                   final Trigger trigger,
                                   final Instant executionTime,
                                   final Instant effectiveExecutionTime,
                                   final ExecutionSchedule executionSchedule,
                                   final ExecutionTracker currentTracker,
                                   final ExecutionResult executionResult) {
            executions.add(new Execution(doc, executionSchedule.getUuid(), currentUser, Thread.currentThread()));
            return executionResult;
        }

        @Override
        public void beforeProcess(final String doc,
                                  final Trigger trigger,
                                  final Instant executionTime,
                                  final Instant effectiveExecutionTime,
                                  final ExecutionSchedule executionSchedule,
                                  final ExecutionTracker currentTracker,
                                  final TaskContext taskContext,
                                  final Function<TaskContext, String> function) {
            beforeProcessDocs.add(doc);
            if (doc.equals(terminateOnDoc)) {
                throw new TaskTerminatedException();
            }
            function.apply(taskContext);
        }

        @Override
        public DocRef getDocRef(final String doc) {
            return new DocRef(DOC_TYPE, doc);
        }

        @Override
        public String load(final DocRef docRef) {
            return docRef.getUuid();
        }

        @Override
        public String reload(final String doc) {
            if (doc.equals(reloadFailureDoc)) {
                throw new RuntimeException("Failed to reload " + doc);
            }
            if (reloadFailureCount > 0) {
                reloadFailureCount--;
                throw new RuntimeException("Failed to reload " + doc);
            }
            return doc;
        }

        @Override
        public String getIdentity(final String doc) {
            return doc;
        }

        @Override
        public List<String> getDocs() {
            return docs;
        }

        @Override
        public String getProcessType() {
            return "test";
        }

        @Override
        public void log(final Severity severity,
                        final String message,
                        final Throwable e) {
            // Nothing to log in tests.
        }

        @Override
        public void postExecuteTidyUp(final List<String> analyticDocs) {
            tidiedUpDocs = List.copyOf(analyticDocs);
        }
    }
}
