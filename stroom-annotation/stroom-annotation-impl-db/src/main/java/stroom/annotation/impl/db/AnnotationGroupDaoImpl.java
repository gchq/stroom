/*
 * Copyright 2024 Crown Copyright
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

package stroom.annotation.impl.db;

import stroom.annotation.impl.AnnotationGroupDao;
import stroom.annotation.shared.AnnotationGroup;
import stroom.db.util.JooqUtil;
import stroom.entity.shared.ExpressionCriteria;
import stroom.util.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Condition;

import java.util.List;
import java.util.UUID;

import static stroom.annotation.impl.db.jooq.tables.AnnotationGroup.ANNOTATION_GROUP;

// Make this a singleton so we don't keep recreating the mappers.
@Singleton
class AnnotationGroupDaoImpl implements AnnotationGroupDao {

    private final AnnotationDbConnProvider connectionProvider;

    @Inject
    AnnotationGroupDaoImpl(final AnnotationDbConnProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public AnnotationGroup createAnnotationGroup(final String name) {
        final String uuid = UUID.randomUUID().toString();
        final Integer id = JooqUtil.contextResult(connectionProvider, context -> context
                .insertInto(ANNOTATION_GROUP,
                        ANNOTATION_GROUP.UUID,
                        ANNOTATION_GROUP.NAME)
                .values(uuid,
                        name)
                .returning(ANNOTATION_GROUP.ID)
                .fetchOne(ANNOTATION_GROUP.ID));
        return AnnotationGroup.builder().id(id).uuid(uuid).name(name).build();
    }

    @Override
    public AnnotationGroup updateAnnotationGroup(final AnnotationGroup annotationGroup) {
        JooqUtil.context(connectionProvider, context -> context
                .update(ANNOTATION_GROUP)
                .set(ANNOTATION_GROUP.NAME, annotationGroup.getName())
                .where(ANNOTATION_GROUP.UUID.eq(annotationGroup.getUuid()))
                .execute());
        return annotationGroup;
    }

    @Override
    public Boolean deleteAnnotationGroup(final AnnotationGroup annotationGroup) {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .update(ANNOTATION_GROUP)
                .set(ANNOTATION_GROUP.DELETED, true)
                .where(ANNOTATION_GROUP.UUID.eq(annotationGroup.getUuid()))
                .execute()) > 0;
    }

    @Override
    public AnnotationGroup fetchAnnotationGroupByName(final String name) {
        return JooqUtil.contextResult(connectionProvider, context -> context
                        .select(ANNOTATION_GROUP.ID,
                                ANNOTATION_GROUP.UUID,
                                ANNOTATION_GROUP.NAME)
                        .from(ANNOTATION_GROUP)
                        .where(ANNOTATION_GROUP.NAME.eq(name))
                        .fetchOptional())
                .map(r -> AnnotationGroup
                        .builder()
                        .id(r.get(ANNOTATION_GROUP.ID))
                        .uuid(r.get(ANNOTATION_GROUP.UUID))
                        .name(r.get(ANNOTATION_GROUP.NAME))
                        .build())
                .orElse(null);
    }

    @Override
    public ResultPage<AnnotationGroup> findAnnotationGroups(final ExpressionCriteria request) {
        final int limit = JooqUtil.getLimit(request.getPageRequest(), true);
        final int offset = JooqUtil.getOffset(request.getPageRequest());
        final List<AnnotationGroup> list = JooqUtil.contextResult(connectionProvider, context -> context
                        .select(ANNOTATION_GROUP.ID,
                                ANNOTATION_GROUP.UUID,
                                ANNOTATION_GROUP.NAME)
                        .from(ANNOTATION_GROUP)
                        .where(ANNOTATION_GROUP.DELETED.isFalse())
                        .offset(offset)
                        .limit(limit)
                        .fetch())
                .map(r -> AnnotationGroup
                        .builder()
                        .id(r.get(ANNOTATION_GROUP.ID))
                        .uuid(r.get(ANNOTATION_GROUP.UUID))
                        .name(r.get(ANNOTATION_GROUP.NAME))
                        .build());
        return ResultPage.createCriterialBasedList(list, request);
    }

    @Override
    public List<AnnotationGroup> getAnnotationGroups(final String filter) {
        final Condition condition;
        if (NullSafe.isNonBlankString(filter)) {
            condition = ANNOTATION_GROUP.DELETED.isFalse()
                    .and(ANNOTATION_GROUP.NAME.startsWithIgnoreCase(filter));
        } else {
            condition = ANNOTATION_GROUP.DELETED.isFalse();
        }

        return JooqUtil.contextResult(connectionProvider, context -> context
                        .select(ANNOTATION_GROUP.ID,
                                ANNOTATION_GROUP.UUID,
                                ANNOTATION_GROUP.NAME)
                        .from(ANNOTATION_GROUP)
                        .where(condition)
                        .fetch())
                .map(r -> AnnotationGroup
                        .builder()
                        .id(r.get(ANNOTATION_GROUP.ID))
                        .uuid(r.get(ANNOTATION_GROUP.UUID))
                        .name(r.get(ANNOTATION_GROUP.NAME))
                        .build());
    }
}
