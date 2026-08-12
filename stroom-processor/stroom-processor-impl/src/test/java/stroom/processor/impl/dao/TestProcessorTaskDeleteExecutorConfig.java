/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.processor.impl.dao;

import stroom.cluster.lock.mock.MockClusterLockService;
import stroom.processor.impl.ProcessorConfig;
import stroom.processor.impl.ProcessorDao;
import stroom.processor.impl.ProcessorFilterDao;
import stroom.processor.impl.ProcessorTaskDao;
import stroom.security.api.DocumentPermissionService;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that the retention task reads {@code stroom.processor.deleteAge} afresh on each run rather than
 * holding on to the value that was current when the singleton was constructed.
 */
@ExtendWith(MockitoExtension.class)
class TestProcessorTaskDeleteExecutorConfig {

    @Mock
    private ProcessorDao mockProcessorDao;
    @Mock
    private ProcessorFilterDao mockProcessorFilterDao;
    @Mock
    private ProcessorTaskDao mockProcessorTaskDao;
    @Mock
    private DocumentPermissionService mockDocumentPermissionService;

    @Captor
    private ArgumentCaptor<Instant> thresholdCaptor;

    @Test
    void deleteAgeChangeIsPickedUpWithoutRestart() {
        // The config object is immutable and is replaced wholesale by ConfigMapper when a property changes,
        // so a changed property is modelled here by swapping the instance the provider returns.
        final AtomicReference<ProcessorConfig> configRef = new AtomicReference<>(config(StroomDuration.ofDays(1)));

        final ProcessorTaskDeleteExecutorImpl executor = new ProcessorTaskDeleteExecutorImpl(
                new MockClusterLockService(),
                configRef::get,
                mockProcessorDao,
                mockProcessorFilterDao,
                mockProcessorTaskDao,
                new SimpleTaskContextFactory(),
                mockDocumentPermissionService);

        final Instant beforeFirstRun = Instant.now();
        executor.lockAndDelete();

        // Now reduce the retention period as an admin would via the properties UI.
        configRef.set(config(StroomDuration.ofHours(1)));

        final Instant beforeSecondRun = Instant.now();
        executor.lockAndDelete();

        Mockito.verify(mockProcessorTaskDao, Mockito.times(2))
                .physicallyDeleteOldTasks(thresholdCaptor.capture());
        final List<Instant> thresholds = thresholdCaptor.getAllValues();

        assertThat(thresholds.get(0))
                .isBetween(beforeFirstRun.minus(Duration.ofDays(1)), Instant.now().minus(Duration.ofDays(1)));
        assertThat(thresholds.get(1))
                .isBetween(beforeSecondRun.minus(Duration.ofHours(1)), Instant.now().minus(Duration.ofHours(1)));
    }

    @Test
    void zeroDeleteAgeDisablesDeletion() {
        final AtomicReference<ProcessorConfig> configRef = new AtomicReference<>(config(StroomDuration.ZERO));

        final ProcessorTaskDeleteExecutorImpl executor = new ProcessorTaskDeleteExecutorImpl(
                new MockClusterLockService(),
                configRef::get,
                mockProcessorDao,
                mockProcessorFilterDao,
                mockProcessorTaskDao,
                new SimpleTaskContextFactory(),
                mockDocumentPermissionService);

        executor.lockAndDelete();

        Mockito.verifyNoInteractions(mockProcessorTaskDao);

        // Setting a non zero age re-enables deletion without a restart.
        configRef.set(config(StroomDuration.ofHours(1)));
        executor.lockAndDelete();

        Mockito.verify(mockProcessorTaskDao, Mockito.times(1))
                .physicallyDeleteOldTasks(Mockito.any());
    }

    private ProcessorConfig config(final StroomDuration deleteAge) {
        final ProcessorConfig processorConfig = Mockito.mock(ProcessorConfig.class);
        Mockito.when(processorConfig.getDeleteAge())
                .thenReturn(deleteAge);
        return processorConfig;
    }
}
