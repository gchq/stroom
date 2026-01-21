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

package stroom.annotation.impl;

import stroom.annotation.shared.Annotation;
import stroom.cluster.lock.api.ClusterLockService;
import stroom.cluster.lock.mock.MockClusterLockService;
import stroom.docref.DocRef;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBatch;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.time.StroomDuration;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestAnnotationService {

    @Mock
    private AnnotationDao mockAnnotationDao;
    @Mock
    private AnnotationConfig mockAnnotationConfig;
    @Mock
    private EntityEventBus mockEntityEventBus;
    @Captor
    private ArgumentCaptor<EntityEventBatch> entityEventBatchArgumentCaptor;

    private ClusterLockService clusterLockService = new MockClusterLockService();

    @Test
    void performDataRetention() {
        final LongList logicallyDeletedIds = createLongList(0, 100);
        final LongList physicallyDeletedIds = createLongList(1000, 100);
        Mockito.when(mockAnnotationDao.markDeletedByDataRetention())
                .thenReturn(logicallyDeletedIds);
        Mockito.when(mockAnnotationConfig.getPhysicalDeleteAge())
                .thenReturn(StroomDuration.ofMillis(100));

        Mockito.when(mockAnnotationDao.physicallyDelete(Mockito.any()))
                .thenReturn(physicallyDeletedIds);

        Mockito.when(mockAnnotationDao.idListToDocRefs(Mockito.any()))
                .thenAnswer(invocation -> {
                    final LongList ids = invocation.getArgument(0, LongList.class);
                    final List<DocRef> docRefs = ids.longStream()
                            .boxed()
                            .map(id -> Annotation.buildDocRef()
                                    .uuid(String.valueOf(id))
                                    .build())
                            .collect(Collectors.toList());

                    return docRefs;
                });

        final AnnotationService annotationService = new AnnotationService(
                mockAnnotationDao,
                null,
                null,
                null,
                null,
                () -> mockAnnotationConfig,
                null,
                null,
                mockEntityEventBus,
                clusterLockService);

        final int batchSize = 60;
        annotationService.performDataRetention(batchSize);

        // 60, 40, 60, 40
        Mockito.verify(mockEntityEventBus, Mockito.times(4))
                .fire(entityEventBatchArgumentCaptor.capture());

        final LongList allIds = entityEventBatchArgumentCaptor.getAllValues()
                .stream()
                .flatMap(batch -> batch.getEntityEvents().stream())
                .map(EntityEvent::getDocRef)
                .map(DocRef::getUuid)
                .mapToLong(Long::parseLong)
                .collect(LongArrayList::new,
                        LongArrayList::add,
                        LongArrayList::addAll);

        assertThat(allIds.size())
                .isEqualTo(100 + 100);
        final LongOpenHashSet allIdsSet = new LongOpenHashSet(allIds);
        assertThat(allIdsSet.containsAll(logicallyDeletedIds))
                .isTrue();
        assertThat(allIdsSet.containsAll(physicallyDeletedIds))
                .isTrue();
    }

    @Test
    void performDataRetention_noChanges() {
        final LongList logicallyDeletedIds = LongList.of();
        final LongList physicallyDeletedIds = LongList.of();
        Mockito.when(mockAnnotationDao.markDeletedByDataRetention())
                .thenReturn(logicallyDeletedIds);
        Mockito.when(mockAnnotationConfig.getPhysicalDeleteAge())
                .thenReturn(StroomDuration.ofMillis(100));

        Mockito.when(mockAnnotationDao.physicallyDelete(Mockito.any()))
                .thenReturn(physicallyDeletedIds);

        final AnnotationService annotationService = new AnnotationService(
                mockAnnotationDao,
                null,
                null,
                null,
                null,
                () -> mockAnnotationConfig,
                null,
                null,
                mockEntityEventBus,
                clusterLockService);

        final int batchSize = 60;
        annotationService.performDataRetention(batchSize);

        // No batches
        Mockito.verify(mockEntityEventBus, Mockito.times(0))
                .fire(entityEventBatchArgumentCaptor.capture());
    }

    private LongList createLongList(final int fromInc, final int count) {
        return LongStream.range(fromInc, fromInc + count)
                .collect(LongArrayList::new,
                        LongArrayList::add,
                        LongArrayList::addAll);
    }
}
