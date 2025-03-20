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

import stroom.annotation.impl.AnnotationConfig;
import stroom.annotation.impl.AnnotationTagDao;
import stroom.annotation.shared.AnnotationTag;
import stroom.annotation.shared.AnnotationTagFields;
import stroom.annotation.shared.AnnotationTagType;
import stroom.annotation.shared.CreateAnnotationTagRequest;
import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.JooqUtil;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.v2.ConditionalFormattingStyle;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.jooq.Condition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static stroom.annotation.impl.db.jooq.tables.AnnotationTag.ANNOTATION_TAG;
import static stroom.annotation.impl.db.jooq.tables.AnnotationTagLink.ANNOTATION_TAG_LINK;

// Make this a singleton so we don't keep recreating the mappers.
@Singleton
class AnnotationTagDaoImpl implements AnnotationTagDao, Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnnotationTagDaoImpl.class);

    private static final String CACHE_NAME = "Annotation Tag Cache";

    private final AnnotationDbConnProvider connectionProvider;
    private final ExpressionMapper expressionMapper;
    private final LoadingStroomCache<Integer, Optional<AnnotationTag>> cache;

    @Inject
    AnnotationTagDaoImpl(final AnnotationDbConnProvider connectionProvider,
                         final ExpressionMapperFactory expressionMapperFactory,
                         final CacheManager cacheManager,
                         final Provider<AnnotationConfig> annotationConfigProvider) {
        this.connectionProvider = connectionProvider;
        this.expressionMapper = createExpressionMapper(expressionMapperFactory);
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> annotationConfigProvider.get().getAnnotationTagCache(),
                this::load);
    }

    private Optional<AnnotationTag> load(final int id) {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .select(ANNOTATION_TAG)
                .from(ANNOTATION_TAG)
                .where(ANNOTATION_TAG.ID.eq(id))
                .fetchOptional(r -> AnnotationTag.builder()
                        .id(r.get(ANNOTATION_TAG.ID))
                        .uuid(r.get(ANNOTATION_TAG.UUID))
                        .type(AnnotationTagType.PRIMITIVE_VALUE_CONVERTER
                                .fromPrimitiveValue(r.get(ANNOTATION_TAG.TYPE_ID)))
                        .name(r.get(ANNOTATION_TAG.UUID))
                        .style(ConditionalFormattingStyle.PRIMITIVE_VALUE_CONVERTER
                                .fromPrimitiveValue(r.get(ANNOTATION_TAG.STYLE_ID)))
                        .build()));
    }

    public AnnotationTag get(final int id) {
        return cache.get(id).orElse(null);
    }

    private ExpressionMapper createExpressionMapper(final ExpressionMapperFactory expressionMapperFactory) {
        final ExpressionMapper expressionMapper = expressionMapperFactory.create();

        // Direct fields
        expressionMapper.map(AnnotationTagFields.ID_FIELD, ANNOTATION_TAG.ID, Integer::valueOf);
        expressionMapper.map(AnnotationTagFields.UUID_FIELD, ANNOTATION_TAG.UUID, value -> value);
        expressionMapper.map(AnnotationTagFields.NAME_FIELD, ANNOTATION_TAG.NAME, value -> value);
        expressionMapper.map(AnnotationTagFields.TYPE_ID_FIELD, ANNOTATION_TAG.TYPE_ID, value -> {
            try {
                return NullSafe.get(value,
                        String::toUpperCase,
                        AnnotationTagType::valueOf,
                        AnnotationTagType::getPrimitiveValue);
            } catch (final RuntimeException e) {
                LOGGER.debug(e::getMessage, e);
            }
            return null;
        });

        return expressionMapper;
    }

    @Override
    public AnnotationTag createAnnotationTag(final CreateAnnotationTagRequest request) {
        final String uuid = UUID.randomUUID().toString();
        final Integer id = JooqUtil.contextResult(connectionProvider, context -> context
                .insertInto(ANNOTATION_TAG,
                        ANNOTATION_TAG.UUID,
                        ANNOTATION_TAG.TYPE_ID,
                        ANNOTATION_TAG.NAME)
                .values(uuid,
                        request.getType().getPrimitiveValue(),
                        request.getName())
                .returning(ANNOTATION_TAG.ID)
                .fetchOne(ANNOTATION_TAG.ID));
        return AnnotationTag.builder().id(id).uuid(uuid).name(request.getName()).build();
    }

    @Override
    public AnnotationTag updateAnnotationGroup(final AnnotationTag annotationTag) {
        JooqUtil.context(connectionProvider, context -> context
                .update(ANNOTATION_TAG)
                .set(ANNOTATION_TAG.NAME, annotationTag.getName())
                .set(ANNOTATION_TAG.STYLE_ID,
                        NullSafe.get(annotationTag.getStyle(), ConditionalFormattingStyle::getPrimitiveValue))
                .where(ANNOTATION_TAG.UUID.eq(annotationTag.getUuid()))
                .execute());
        return annotationTag;
    }

    @Override
    public Boolean deleteAnnotationTag(final AnnotationTag annotationTag) {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .update(ANNOTATION_TAG)
                .set(ANNOTATION_TAG.DELETED, true)
                .where(ANNOTATION_TAG.UUID.eq(annotationTag.getUuid()))
                .execute()) > 0;
    }

    @Override
    public ResultPage<AnnotationTag> findAnnotationTags(final ExpressionCriteria request) {
        final Condition condition = expressionMapper.apply(request.getExpression());
        final int limit = JooqUtil.getLimit(request.getPageRequest(), true);
        final int offset = JooqUtil.getOffset(request.getPageRequest());
        final List<AnnotationTag> list = JooqUtil.contextResult(connectionProvider, context -> context
                        .select(ANNOTATION_TAG.ID,
                                ANNOTATION_TAG.UUID,
                                ANNOTATION_TAG.NAME,
                                ANNOTATION_TAG.STYLE_ID)
                        .from(ANNOTATION_TAG)
                        .where(ANNOTATION_TAG.DELETED.isFalse())
                        .and(condition)
                        .offset(offset)
                        .limit(limit)
                        .fetch())
                .map(r -> AnnotationTag
                        .builder()
                        .id(r.get(ANNOTATION_TAG.ID))
                        .uuid(r.get(ANNOTATION_TAG.UUID))
                        .name(r.get(ANNOTATION_TAG.NAME))
                        .style(ConditionalFormattingStyle.PRIMITIVE_VALUE_CONVERTER
                                .fromPrimitiveValue(r.get(ANNOTATION_TAG.STYLE_ID)))
                        .build());
        return ResultPage.createCriterialBasedList(list, request);
    }

    @Override
    public Optional<AnnotationTag> findAnnotationTag(final AnnotationTagType annotationTagType, final String name) {
        if (NullSafe.isBlankString(name)) {
            return Optional.empty();
        }

        return JooqUtil.contextResult(connectionProvider, context -> context
                        .select(ANNOTATION_TAG.ID,
                                ANNOTATION_TAG.UUID,
                                ANNOTATION_TAG.NAME,
                                ANNOTATION_TAG.STYLE_ID)
                        .from(ANNOTATION_TAG)
                        .where(ANNOTATION_TAG.TYPE_ID.eq(annotationTagType.getPrimitiveValue()))
                        .and(ANNOTATION_TAG.NAME.eq(name))
                        .and(ANNOTATION_TAG.DELETED.isFalse())
                        .limit(1)
                        .fetchOptional())
                .map(r -> AnnotationTag
                        .builder()
                        .id(r.get(ANNOTATION_TAG.ID))
                        .uuid(r.get(ANNOTATION_TAG.UUID))
                        .name(r.get(ANNOTATION_TAG.NAME))
                        .style(ConditionalFormattingStyle.PRIMITIVE_VALUE_CONVERTER
                                .fromPrimitiveValue(r.get(ANNOTATION_TAG.STYLE_ID)))
                        .build());
    }

    @Override
    public void clear() {
        JooqUtil.context(connectionProvider, context -> context.deleteFrom(ANNOTATION_TAG_LINK).execute());
        JooqUtil.context(connectionProvider, context -> context.deleteFrom(ANNOTATION_TAG).execute());
        cache.clear();
    }
}
