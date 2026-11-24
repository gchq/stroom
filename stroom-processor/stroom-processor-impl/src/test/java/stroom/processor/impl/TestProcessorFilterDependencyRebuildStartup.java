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

import stroom.cluster.lock.api.ClusterLockService;
import stroom.docstore.api.DocDependencyService;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.security.mock.MockSecurityContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestProcessorFilterDependencyRebuildStartup {

    private static final int CURRENT_VERSION = ProcessorFilterDependencies.CURRENT_DEPENDENCY_VERSION;
    private static final int BATCH_SIZE = ProcessorFilterDependencyRebuildStartup.BATCH_SIZE;

    @Mock
    private ClusterLockService clusterLockService;
    @Mock
    private ProcessorFilterDao processorFilterDao;
    @Mock
    private ProcessorFilterDependencies processorFilterDependencies;
    @Mock
    private DocDependencyService docDependencyService;
    @Captor
    private ArgumentCaptor<ProcessorFilter> filterCaptor;

    private ProcessorFilterDependencyRebuildStartup startup;

    @BeforeEach
    void setUp() {
        startup = new ProcessorFilterDependencyRebuildStartup(
                clusterLockService,
                new MockSecurityContext(),
                processorFilterDao,
                processorFilterDependencies,
                () -> docDependencyService);
    }

    @Test
    void testRun_lockHeldByAnotherNode() {
        // tryLock does not run the runnable when another node holds the lock

        startup.run();

        verify(clusterLockService).tryLock(eq(ProcessorFilterDependencyRebuildStartup.LOCK_NAME), any());
        verifyNoInteractions(processorFilterDao, processorFilterDependencies, docDependencyService);
    }

    @Test
    void testRun_nothingStale() {
        givenLockAcquired();
        when(processorFilterDao.findWithStaleDependencies(CURRENT_VERSION, 0, BATCH_SIZE))
                .thenReturn(List.of());

        startup.run();

        verify(processorFilterDependencies, never()).update(any());
        verify(processorFilterDependencies, never()).removeForDeleted(any());
        // Nothing was stale, so there is no need to look for edges from filters that no longer exist
        verifyNoInteractions(docDependencyService);
    }

    @Test
    void testRun_rebuildsLiveFilters() {
        givenLockAcquired();
        givenNoOrphans();
        final ProcessorFilter filter1 = filter(1, false, false);
        final ProcessorFilter filter2 = filter(2, false, false);
        givenStale(filter1, filter2);
        when(processorFilterDependencies.update(any())).thenReturn(true);

        startup.run();

        verify(processorFilterDependencies).update(filter1);
        verify(processorFilterDependencies).update(filter2);
        verify(processorFilterDependencies, never()).removeForDeleted(any());
        verify(docDependencyService).invalidateBrokenDependencyCache();
    }

    @Test
    void testRun_rebuildsInBatches() {
        givenLockAcquired();
        givenNoOrphans();
        // A full batch means there may be more, so the next batch is found after the last ID
        final List<ProcessorFilter> firstBatch = IntStream.rangeClosed(1, BATCH_SIZE)
                .mapToObj(id -> filter(id, false, false))
                .toList();
        final ProcessorFilter lastFilter = filter(BATCH_SIZE + 1, false, false);
        when(processorFilterDao.findWithStaleDependencies(CURRENT_VERSION, 0, BATCH_SIZE))
                .thenReturn(firstBatch);
        when(processorFilterDao.findWithStaleDependencies(CURRENT_VERSION, BATCH_SIZE, BATCH_SIZE))
                .thenReturn(List.of(lastFilter));
        when(processorFilterDependencies.update(any())).thenReturn(true);

        startup.run();

        verify(processorFilterDependencies, times(BATCH_SIZE + 1)).update(any());
        verify(processorFilterDependencies).update(lastFilter);
        // The second batch was not full, so there is no third
        verify(processorFilterDao, times(2)).findWithStaleDependencies(anyInt(), anyInt(), anyInt());
    }

    @Test
    void testRun_deletedFilterHasEdgesRemoved() {
        // Regression for gh-5979: the 7.13 migration and the pipeline delete path left edges on
        // deleted filters, which then showed their pipeline as missing.
        givenLockAcquired();
        givenNoOrphans();
        final ProcessorFilter filter = filter(1, true, false);
        givenStale(filter);
        when(processorFilterDependencies.removeForDeleted(any())).thenReturn(true);

        startup.run();

        verify(processorFilterDependencies).removeForDeleted(filter);
        verify(processorFilterDependencies, never()).update(any());
    }

    @Test
    void testRun_filterOfDeletedProcessorHasEdgesRemoved() {
        givenLockAcquired();
        givenNoOrphans();
        final ProcessorFilter filter = filter(1, false, true);
        givenStale(filter);
        when(processorFilterDependencies.removeForDeleted(any())).thenReturn(true);

        startup.run();

        verify(processorFilterDependencies).removeForDeleted(filter);
        verify(processorFilterDependencies, never()).update(any());
    }

    @Test
    void testRun_filterChangedDuringRebuild_reReadAndRebuilt() {
        givenLockAcquired();
        givenNoOrphans();
        final ProcessorFilter stale = filter(1, false, false);
        final ProcessorFilter current = stale.copy().version(stale.getVersion() + 1).build();
        givenStale(stale);
        // ProcessorFilter.equals only compares the id, so the two calls are told apart by order
        when(processorFilterDependencies.update(any())).thenReturn(false, true);
        when(processorFilterDao.fetch(1)).thenReturn(Optional.of(current));

        startup.run();

        verify(processorFilterDependencies, times(2)).update(filterCaptor.capture());
        assertThat(filterCaptor.getAllValues())
                .extracting(ProcessorFilter::getVersion)
                .containsExactly(stale.getVersion(), current.getVersion());
    }

    @Test
    void testRun_filterDeletedDuringRebuild() {
        givenLockAcquired();
        givenNoOrphans();
        final ProcessorFilter stale = filter(1, false, false);
        final ProcessorFilter deleted = filter(1, true, false);
        givenStale(stale);
        when(processorFilterDependencies.update(stale)).thenReturn(false);
        when(processorFilterDependencies.removeForDeleted(deleted)).thenReturn(true);
        when(processorFilterDao.fetch(1)).thenReturn(Optional.of(deleted));

        startup.run();

        verify(processorFilterDependencies).removeForDeleted(deleted);
    }

    @Test
    void testRun_filterPhysicallyDeletedDuringRebuild() {
        givenLockAcquired();
        givenNoOrphans();
        final ProcessorFilter stale = filter(1, false, false);
        givenStale(stale);
        when(processorFilterDependencies.update(stale)).thenReturn(false);
        when(processorFilterDao.fetch(1)).thenReturn(Optional.empty());

        startup.run();

        verify(processorFilterDependencies).remove(stale.getUuid());
    }

    @Test
    void testRun_filterKeepsChanging_givesUp() {
        givenLockAcquired();
        givenNoOrphans();
        final ProcessorFilter stale = filter(1, false, false);
        givenStale(stale);
        when(processorFilterDependencies.update(any())).thenReturn(false);
        when(processorFilterDao.fetch(1)).thenReturn(Optional.of(stale));

        startup.run();

        verify(processorFilterDependencies, times(ProcessorFilterDependencyRebuildStartup.MAX_ATTEMPTS))
                .update(stale);
        verify(processorFilterDependencies, never()).remove(anyString());
    }

    @Test
    void testRun_errorOnOneFilterDoesNotStopOthers() {
        givenLockAcquired();
        givenNoOrphans();
        final ProcessorFilter filter1 = filter(1, false, false);
        final ProcessorFilter filter2 = filter(2, false, false);
        givenStale(filter1, filter2);
        when(processorFilterDependencies.update(any()))
                .thenThrow(new RuntimeException("Boom"))
                .thenReturn(true);

        startup.run();

        verify(processorFilterDependencies, times(2)).update(filterCaptor.capture());
        assertThat(filterCaptor.getAllValues())
                .extracting(ProcessorFilter::getUuid)
                .containsExactly(filter1.getUuid(), filter2.getUuid());
        verify(docDependencyService).invalidateBrokenDependencyCache();
    }

    @Test
    void testRun_removesEdgesOfFiltersThatNoLongerExist() {
        givenLockAcquired();
        givenStale(filter(1, false, false));
        when(processorFilterDependencies.update(any())).thenReturn(true);
        final Set<String> sourceUuids = Set.of("exists-uuid", "gone-uuid");
        when(docDependencyService.getSourceUuids(ProcessorFilter.ENTITY_TYPE)).thenReturn(sourceUuids);
        when(processorFilterDao.fetchExistingUuids(sourceUuids)).thenReturn(Set.of("exists-uuid"));

        startup.run();

        verify(processorFilterDependencies).remove(Set.of("gone-uuid"));
        verify(docDependencyService).invalidateBrokenDependencyCache();
    }

    private void givenLockAcquired() {
        doAnswer(invocation -> {
            final Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return null;
        }).when(clusterLockService).tryLock(eq(ProcessorFilterDependencyRebuildStartup.LOCK_NAME), any());
    }

    private void givenNoOrphans() {
        lenient().when(docDependencyService.getSourceUuids(ProcessorFilter.ENTITY_TYPE)).thenReturn(Set.of());
        lenient().when(processorFilterDao.fetchExistingUuids(any())).thenReturn(Set.of());
    }

    private void givenStale(final ProcessorFilter... filters) {
        when(processorFilterDao.findWithStaleDependencies(CURRENT_VERSION, 0, BATCH_SIZE))
                .thenReturn(List.of(filters));
    }

    private static ProcessorFilter filter(final int id,
                                          final boolean deleted,
                                          final boolean processorDeleted) {
        return ProcessorFilter.builder()
                .id(id)
                .version(1)
                .uuid("filter-uuid-" + id)
                .deleted(deleted)
                .processor(Processor.builder()
                        .pipelineUuid("pipeline-uuid")
                        .deleted(processorDeleted)
                        .build())
                .build();
    }
}
