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

package stroom.annotation.impl;

import stroom.annotation.shared.AnnotationDecorationFields;
import stroom.annotation.shared.AnnotationIdentity;
import stroom.annotation.shared.AnnotationTag;
import stroom.annotation.shared.AnnotationTagType;
import stroom.cluster.lock.api.ClusterLockService;
import stroom.cluster.lock.mock.MockClusterLockService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBatch;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.shared.HasId;
import stroom.util.time.StroomDuration;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestAnnotationService {

    @Mock
    private AnnotationDao mockAnnotationDao;
    @Mock
    private AnnotationConfig mockAnnotationConfig;
    @Mock
    private AnnotationTagDao mockAnnotationTagDao;
    @Mock
    private SecurityContext mockSecurityContext;
    @Mock
    private EntityEventBus mockEntityEventBus;
    @Captor
    private ArgumentCaptor<EntityEventBatch> entityEventBatchArgumentCaptor;
    @Captor
    private ArgumentCaptor<EntityEvent> entityEventArgumentCaptor;

    private ClusterLockService clusterLockService = new MockClusterLockService();

    @Test
    void performDataRetention() {
        final List<AnnotationIdentity> logicallyDeletedIds = createLongList(0, 100);
        final List<AnnotationIdentity> physicallyDeletedIds = createLongList(1000, 100);
        Mockito.when(mockAnnotationDao.markDeletedByDataRetention())
                .thenReturn(logicallyDeletedIds);
        Mockito.when(mockAnnotationConfig.getPhysicalDeleteAge())
                .thenReturn(StroomDuration.ofMillis(100));

        Mockito.when(mockAnnotationDao.physicallyDelete(Mockito.any()))
                .thenReturn(physicallyDeletedIds);

//        Mockito.when(mockAnnotationDao.idListToDocRefs(Mockito.any()))
//                .thenAnswer(invocation -> {
//                    final LongList ids = invocation.getArgument(0, LongList.class);
//                    return ids.longStream()
//                            .boxed()
//                            .map(id -> new AnnotationIdentity(String.valueOf(id), id))
//                            .collect(Collectors.toList());
//                });

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
                .flatMap(batch ->
                        batch.getEntityEvents().stream())
                .mapToLong(entityEvent -> entityEvent.getDataObjectAs(
                        AnnotationIdEntityEventData.class,
                        AnnotationIdEntityEventData::getAnnotationId))
                .collect(LongArrayList::new,
                        LongArrayList::add,
                        LongArrayList::addAll);

        assertThat(allIds.size())
                .isEqualTo(100 + 100);
        final LongOpenHashSet allIdsSet = new LongOpenHashSet(allIds);
        assertThat(allIdsSet.containsAll(HasId.asIdList(logicallyDeletedIds)))
                .isTrue();
        assertThat(allIdsSet.containsAll(HasId.asIdList(physicallyDeletedIds)))
                .isTrue();
    }

    @Test
    void performDataRetention_noChanges() {
        final List<AnnotationIdentity> logicallyDeletedIds = List.of();
        final List<AnnotationIdentity> physicallyDeletedIds = List.of();
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

    @Test
    void updateAnnotationTag_comment() {
        // Regression test: a comment tag has no decoration field, so firing its event threw an NPE
        // after the update had already been saved.
        final AnnotationTag annotationTag = createAnnotationTag(AnnotationTagType.COMMENT);
        Mockito.when(mockAnnotationTagDao.updateAnnotationTag(annotationTag))
                .thenReturn(annotationTag);

        final AnnotationTag result = createTagService().updateAnnotationTag(annotationTag);

        assertThat(result)
                .isSameAs(annotationTag);
        Mockito.verify(mockEntityEventBus, Mockito.never())
                .fire(Mockito.any(EntityEvent.class));
    }

    @Test
    void deleteAnnotationTag_comment() {
        // Regression test: a comment tag has no decoration field, so firing its event threw an NPE
        // after the delete had already been saved.
        final AnnotationTag annotationTag = createAnnotationTag(AnnotationTagType.COMMENT);
        Mockito.when(mockAnnotationTagDao.deleteAnnotationTag(annotationTag))
                .thenReturn(true);

        final Boolean result = createTagService().deleteAnnotationTag(annotationTag);

        assertThat(result)
                .isTrue();
        Mockito.verify(mockEntityEventBus, Mockito.never())
                .fire(Mockito.any(EntityEvent.class));
    }

    @ParameterizedTest
    @EnumSource(value = AnnotationTagType.class, names = {"LABEL", "STATUS", "COLLECTION"})
    void updateAnnotationTag_decorationField(final AnnotationTagType tagType) {
        final AnnotationTag annotationTag = createAnnotationTag(tagType);
        Mockito.when(mockAnnotationTagDao.updateAnnotationTag(annotationTag))
                .thenReturn(annotationTag);

        createTagService().updateAnnotationTag(annotationTag);

        assertFieldEvent(EntityAction.UPDATE, getExpectedFieldName(tagType));
    }

    @ParameterizedTest
    @EnumSource(value = AnnotationTagType.class, names = {"LABEL", "STATUS", "COLLECTION"})
    void deleteAnnotationTag_decorationField(final AnnotationTagType tagType) {
        final AnnotationTag annotationTag = createAnnotationTag(tagType);
        Mockito.when(mockAnnotationTagDao.deleteAnnotationTag(annotationTag))
                .thenReturn(true);

        createTagService().deleteAnnotationTag(annotationTag);

        assertFieldEvent(EntityAction.DELETE, getExpectedFieldName(tagType));
    }

    private AnnotationService createTagService() {
        Mockito.when(mockSecurityContext.hasAppPermission(AppPermission.ANNOTATIONS))
                .thenReturn(true);
        return new AnnotationService(
                null,
                mockAnnotationTagDao,
                mockSecurityContext,
                null,
                null,
                null,
                null,
                null,
                mockEntityEventBus,
                clusterLockService);
    }

    private AnnotationTag createAnnotationTag(final AnnotationTagType tagType) {
        return AnnotationTag.builder()
                .uuid("tag-uuid")
                .type(tagType)
                .name("X")
                .build();
    }

    private String getExpectedFieldName(final AnnotationTagType tagType) {
        return switch (tagType) {
            case LABEL -> AnnotationDecorationFields.ANNOTATION_LABEL;
            case STATUS -> AnnotationDecorationFields.ANNOTATION_STATUS;
            case COLLECTION -> AnnotationDecorationFields.ANNOTATION_COLLECTION;
            case COMMENT -> throw new IllegalArgumentException("A comment tag has no field");
        };
    }

    private void assertFieldEvent(final EntityAction entityAction, final String fieldName) {
        Mockito.verify(mockEntityEventBus)
                .fire(entityEventArgumentCaptor.capture());
        final EntityEvent entityEvent = entityEventArgumentCaptor.getValue();
        assertThat(entityEvent.getAction())
                .isEqualTo(entityAction);
        assertThat(entityEvent.getDataObjectAs(
                AnnotationFieldsEntityEventData.class,
                AnnotationFieldsEntityEventData::getChangedFields))
                .containsExactly(fieldName);
    }

    private List<AnnotationIdentity> createLongList(final int fromInc, final int count) {
        return LongStream.range(fromInc, fromInc + count)
                .boxed()
                .map(id -> new AnnotationIdentity("uuid-" + id, id))
                .toList();
    }
}
