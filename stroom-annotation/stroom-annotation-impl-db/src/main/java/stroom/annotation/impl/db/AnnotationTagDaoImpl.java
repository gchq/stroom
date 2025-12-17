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
import stroom.db.util.JooqUtil.BooleanOperator;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.ConditionalFormattingStyle;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.Record;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

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
                .select(ANNOTATION_TAG.ID,
                        ANNOTATION_TAG.UUID,
                        ANNOTATION_TAG.TYPE_ID,
                        ANNOTATION_TAG.NAME,
                        ANNOTATION_TAG.STYLE_ID)
                .from(ANNOTATION_TAG)
                .where(ANNOTATION_TAG.ID.eq(id))
                .fetchOptional(this::mapToAnnotationTag));
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
        return AnnotationTag.builder().id(id).uuid(uuid).type(request.getType()).name(request.getName()).build();
    }

    @Override
    public AnnotationTag updateAnnotationTag(final AnnotationTag annotationTag) {
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

    public ResultPage<AnnotationTag> findAnnotationTags(final ExpressionCriteria request) {
        return findAnnotationTags(request, uuid -> true);
    }

    @Override
    public ResultPage<AnnotationTag> findAnnotationTags(final ExpressionCriteria request,
                                                        final Predicate<String> uuidPredicate) {
        final long offset = NullSafe.getOrElse(
                request,
                ExpressionCriteria::getPageRequest,
                PageRequest::getOffset,
                0);
        final int length = NullSafe.getOrElse(
                request,
                ExpressionCriteria::getPageRequest,
                PageRequest::getLength,
                Integer.MAX_VALUE);
        final long maxPos = offset + length;
        final Condition condition = expressionMapper.apply(request.getExpression());
        final List<AnnotationTag> list = new ArrayList<>();
        final AtomicLong count = new AtomicLong();
        JooqUtil.context(connectionProvider, context -> context
                .select(ANNOTATION_TAG.ID,
                        ANNOTATION_TAG.UUID,
                        ANNOTATION_TAG.TYPE_ID,
                        ANNOTATION_TAG.NAME,
                        ANNOTATION_TAG.STYLE_ID)
                .from(ANNOTATION_TAG)
                .where(ANNOTATION_TAG.DELETED.isFalse())
                .and(condition)
                .stream()
                .forEach(record -> {
                    final AnnotationTag annotationTag = mapToAnnotationTag(record);
                    if (uuidPredicate.test(annotationTag.getUuid())) {
                        final long pos = count.getAndIncrement();
                        if (pos >= offset && pos < maxPos) {
                            list.add(annotationTag);
                        }
                    }
                }));
        return new ResultPage<>(list, PageResponse
                .builder()
                .offset(offset)
                .length(list.size())
                .total(count.get())
                .exact(true)
                .build());
    }

    @Override
    public Optional<AnnotationTag> findAnnotationTag(final AnnotationTagType annotationTagType, final String name) {
        if (NullSafe.isBlankString(name)) {
            return Optional.empty();
        }

        return JooqUtil.contextResult(connectionProvider, context -> context
                        .select(ANNOTATION_TAG.ID,
                                ANNOTATION_TAG.UUID,
                                ANNOTATION_TAG.TYPE_ID,
                                ANNOTATION_TAG.NAME,
                                ANNOTATION_TAG.STYLE_ID)
                        .from(ANNOTATION_TAG)
                        .where(ANNOTATION_TAG.TYPE_ID.eq(annotationTagType.getPrimitiveValue()))
                        .and(ANNOTATION_TAG.NAME.eq(name))
                        .and(ANNOTATION_TAG.DELETED.isFalse())
                        .limit(1)
                        .fetchOptional())
                .map(this::mapToAnnotationTag);
    }

    private AnnotationTag mapToAnnotationTag(final Record record) {
        return AnnotationTag
                .builder()
                .id(record.get(ANNOTATION_TAG.ID))
                .uuid(record.get(ANNOTATION_TAG.UUID))
                .type(AnnotationTagType.PRIMITIVE_VALUE_CONVERTER
                        .fromPrimitiveValue(record.get(ANNOTATION_TAG.TYPE_ID)))
                .name(record.get(ANNOTATION_TAG.NAME))
                .style(ConditionalFormattingStyle.PRIMITIVE_VALUE_CONVERTER
                        .fromPrimitiveValue(record.get(ANNOTATION_TAG.STYLE_ID)))
                .build();
    }

    @Override
    public void clear() {
        JooqUtil.context(connectionProvider, context -> context.deleteFrom(ANNOTATION_TAG_LINK).execute());
        JooqUtil.context(connectionProvider, context -> context.deleteFrom(ANNOTATION_TAG).execute());
        cache.clear();
    }


    public List<Integer> getIds(final AnnotationTagType annotationTagType,
                                final List<String> wildCardedTypeNames) {
        if (NullSafe.isEmptyCollection(wildCardedTypeNames)) {
            return Collections.emptyList();
        }
        return find(annotationTagType, wildCardedTypeNames);
    }

    private List<Integer> find(final AnnotationTagType annotationTagType,
                               final List<String> wildCardedNames) {
        if (NullSafe.isEmptyCollection(wildCardedNames)) {
            return Collections.emptyList();
        }

        return fetchWithWildCards(annotationTagType, wildCardedNames).stream().toList();
    }

    private Set<Integer> fetchWithWildCards(final AnnotationTagType annotationTagType,
                                            final List<String> wildCardedTypeNames) {
        final Condition condition = JooqUtil.createWildCardedStringsCondition(
                ANNOTATION_TAG.NAME, wildCardedTypeNames, true, BooleanOperator.OR);

        return JooqUtil.contextResult(connectionProvider, context -> context
                .select(ANNOTATION_TAG.NAME, ANNOTATION_TAG.ID)
                .from(ANNOTATION_TAG)
                .where(condition)
                .and(ANNOTATION_TAG.TYPE_ID.eq(annotationTagType.getPrimitiveValue()))
                .fetchSet(ANNOTATION_TAG.ID));
    }
}
