/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.data.store.impl.fs;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.impl.fs.PhysicalDeleteExecutor.Progress;
import stroom.meta.api.MetaService;
import stroom.meta.api.PhysicalDelete;
import stroom.meta.shared.SimpleMeta;
import stroom.meta.shared.SimpleMetaImpl;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.SimpleTaskContext;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.Mockito.when;

@Disabled // may be too complicated to mock
@ExtendWith(MockitoExtension.class)
class TestPhysicalDeleteExecutor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestPhysicalDeleteExecutor.class);

    @Mock
    private ClusterLockService mockClusterLockService;
    @Mock
    private DataStoreServiceConfig mockDataStoreServiceConfig;
    @Mock
    private FsPathHelper mockFileSystemStreamPathHelper;
    @Mock
    private MetaService mockMetaService;
    @Mock
    private PhysicalDelete mockPhysicalDelete;
    @Mock
    private DataVolumeDao mockDataVolumeDao;
    @Mock
    private ExecutorProvider mockExecutorProvider;

    @Spy
    private TaskContext taskContext = new SimpleTaskContext();
    @Spy
    private TaskContextFactory taskContextFactory = new SimpleTaskContextFactory();
    @Mock
    private FsFileDeleter mockFsFileDeleter;

    @Captor
    private ArgumentCaptor<Long> deleteFilesByBaseNameMetaIdCaptor;

    @InjectMocks
    private PhysicalDeleteExecutor physicalDeleteExecutor;

    @Test
    void delete_onePass() {

        LOGGER.info("here");

        final Instant thresholdTime = LocalDateTime.of(
                        2016, 6, 20, 14, 0, 0)
                .toInstant(ZoneOffset.UTC);
        final int batchSize = 10;

        LOGGER.info("before when {}", System.identityHashCode(mockDataStoreServiceConfig));
        when(mockDataStoreServiceConfig.getDeleteBatchSize())
//                .thenReturn(123, batchSize, 20, 30);
                .thenReturn(batchSize);

        when(mockExecutorProvider.get(Mockito.any()))
                .thenReturn(Executors.newSingleThreadExecutor());

        final List<SimpleMeta> metaList1 = buildMetaList(batchSize, thresholdTime);
        final List<SimpleMeta> metaList2 = Collections.emptyList();

        when(mockMetaService.getLogicallyDeleted(
                Mockito.eq(thresholdTime),
                Mockito.eq(batchSize),
                Mockito.eq(Collections.emptySet())))
                .thenReturn(metaList1) // 1st iter - full batch
                .thenReturn(metaList2); // 2nd iter, no data

        Mockito.when(mockFsFileDeleter.deleteFilesByBaseName(
                        deleteFilesByBaseNameMetaIdCaptor.capture(),
                        Mockito.notNull(),
                        Mockito.notNull(),
                        Mockito.notNull()))
                .thenReturn(true);

        physicalDeleteExecutor.delete(
                thresholdTime,
                Progress.start(mockDataStoreServiceConfig));
    }

    private List<SimpleMeta> buildMetaList(final int count, final Instant baseTime) {
        return IntStream.rangeClosed(1, count)
                .boxed()
                .map(i -> {
                    return SimpleMetaImpl.builder()
                            .id(i)
                            .feedName("MY_FEED")
                            .typeName(StreamTypeNames.RAW_EVENTS)
                            .createMs(baseTime.minus(i, ChronoUnit.DAYS).toEpochMilli())
                            .statusMs(baseTime.minus(i, ChronoUnit.DAYS).toEpochMilli())
                            .build();
                })
                .collect(Collectors.toList());
    }
}
