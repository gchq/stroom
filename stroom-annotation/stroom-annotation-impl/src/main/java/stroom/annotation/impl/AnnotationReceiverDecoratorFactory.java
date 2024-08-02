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

package stroom.annotation.impl;

import stroom.annotation.api.AnnotationFields;
import stroom.annotation.shared.Annotation;
import stroom.datasource.api.v2.QueryField;
import stroom.expression.matcher.ExpressionMatcher;
import stroom.expression.matcher.ExpressionMatcherFactory;
import stroom.index.shared.IndexConstants;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.Query;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.search.extraction.AnnotationsDecoratorFactory;
import stroom.search.extraction.ExpressionFilter;
import stroom.security.api.SecurityContext;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.UserName;
import stroom.util.shared.string.CIHashSet;
import stroom.util.shared.string.CIKey;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

class AnnotationReceiverDecoratorFactory implements AnnotationsDecoratorFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnnotationReceiverDecoratorFactory.class);

    private static final Map<CIKey, Function<Annotation, Val>> VALUE_MAPPING = Map.ofEntries(
            nullSafeEntry(AnnotationFields.ID_FIELD, Annotation::getId),
            nullSafeEntry(AnnotationFields.CREATED_ON_FIELD, Annotation::getCreateTime, createTimeEpochMs ->
                    Val.nullSafeCreate(createTimeEpochMs, ValDate::create)),
            nullSafeEntry(AnnotationFields.CREATED_BY_FIELD, Annotation::getCreateUser),
            nullSafeEntry(AnnotationFields.UPDATED_ON_FIELD, Annotation::getUpdateTime, updateTimeEpochMs ->
                    Val.nullSafeCreate(updateTimeEpochMs, ValDate::create)),
            nullSafeEntry(AnnotationFields.UPDATED_BY_FIELD, Annotation::getUpdateUser),
            nullSafeEntry(AnnotationFields.TITLE_FIELD, Annotation::getTitle),
            nullSafeEntry(AnnotationFields.SUBJECT_FIELD, Annotation::getSubject),
            nullSafeEntry(AnnotationFields.STATUS_FIELD, Annotation::getStatus),
            nullSafeEntry(AnnotationFields.ASSIGNED_TO_FIELD, annotation ->
                    NullSafe.get(annotation.getAssignedTo(), UserName::getUserIdentityForAudit)),
            nullSafeEntry(AnnotationFields.COMMENT_FIELD, Annotation::getComment),
            nullSafeEntry(AnnotationFields.HISTORY_FIELD, Annotation::getHistory));

    private static final Map<CIKey, Function<Annotation, Object>> OBJECT_MAPPING = Map.ofEntries(
            Map.entry(AnnotationFields.ID_FIELD.getFldNameAsCIKey(), Annotation::getId),
            Map.entry(AnnotationFields.CREATED_ON_FIELD.getFldNameAsCIKey(), Annotation::getCreateTime),
            Map.entry(AnnotationFields.CREATED_BY_FIELD.getFldNameAsCIKey(), Annotation::getCreateUser),
            Map.entry(AnnotationFields.UPDATED_ON_FIELD.getFldNameAsCIKey(), Annotation::getUpdateTime),
            Map.entry(AnnotationFields.UPDATED_BY_FIELD.getFldNameAsCIKey(), Annotation::getUpdateUser),
            Map.entry(AnnotationFields.TITLE_FIELD.getFldNameAsCIKey(), Annotation::getTitle),
            Map.entry(AnnotationFields.SUBJECT_FIELD.getFldNameAsCIKey(), Annotation::getSubject),
            Map.entry(AnnotationFields.STATUS_FIELD.getFldNameAsCIKey(), Annotation::getStatus),
            Map.entry(AnnotationFields.ASSIGNED_TO_FIELD.getFldNameAsCIKey(), annotation ->
                    NullSafe.get(annotation.getAssignedTo(), UserName::getUserIdentityForAudit)),
            Map.entry(AnnotationFields.COMMENT_FIELD.getFldNameAsCIKey(), Annotation::getComment),
            Map.entry(AnnotationFields.HISTORY_FIELD.getFldNameAsCIKey(), Annotation::getHistory));

    private final AnnotationDao annotationDao;
    private final ExpressionMatcherFactory expressionMatcherFactory;
    private final AnnotationConfig annotationConfig;
    private final SecurityContext securityContext;

    @Inject
    AnnotationReceiverDecoratorFactory(final AnnotationDao annotationDao,
                                       final ExpressionMatcherFactory expressionMatcherFactory,
                                       final AnnotationConfig annotationConfig,
                                       final SecurityContext securityContext) {
        this.annotationDao = annotationDao;
        this.expressionMatcherFactory = expressionMatcherFactory;
        this.annotationConfig = annotationConfig;
        this.securityContext = securityContext;
    }

    @Override
    public ValuesConsumer create(final ValuesConsumer valuesConsumer,
                                 final FieldIndex fieldIndex,
                                 final Query query) {
        final Integer annotationIdIndex = fieldIndex.getPos(AnnotationFields.ID_FIELD.getFldNameAsCIKey());
        final Integer streamIdIndex = fieldIndex.getPos(IndexConstants.STREAM_ID);
        final Integer eventIdIndex = fieldIndex.getPos(IndexConstants.EVENT_ID);

        if (annotationIdIndex == null && (streamIdIndex == null || eventIdIndex == null)) {
            return valuesConsumer;
        }

        // Do we need to filter based on annotation attributes?
        final Function<Annotation, Boolean> filter = createFilter(query.getExpression());

        final Set<CIKey> usedFields = new HashSet<>(fieldIndex.getFieldsAsCIKeys());
        usedFields.retainAll(AnnotationFields.FIELD_MAP.keySet());

        if (filter == null && usedFields.isEmpty()) {
            return valuesConsumer;
        }

        final Annotation defaultAnnotation = createDefaultAnnotation();

        return values -> {
            // TODO : At present we are just going to do this synchronously but in future we may do asynchronously
            //  in which case we would increment the completion count after providing values.

            // Filter based on annotation.
            final List<Annotation> annotations = new ArrayList<>();
            if (annotationIdIndex != null) {
                final Long annotationId = getLong(values, annotationIdIndex);
                if (annotationId != null) {
                    annotations.add(annotationDao.get(annotationId));
                }
            }

            if (annotations.isEmpty()) {
                final Long streamId = getLong(values, streamIdIndex);
                final Long eventId = getLong(values, eventIdIndex);
                if (streamId != null && eventId != null) {
                    final List<Annotation> list = annotationDao.getAnnotationsForEvents(streamId, eventId);
                    annotations.addAll(list);
                }
            }

            if (annotations.isEmpty()) {
                annotations.add(defaultAnnotation);
            }

            Val[] copy = values;
            for (final Annotation annotation : annotations) {
                try {
                    if (filter == null || filter.apply(annotation)) {
                        // If we have more than one annotation then copy the original values into a new
                        // values object for each new row.
                        if (annotations.size() > 1 || copy.length < fieldIndex.size()) {
                            copy = Arrays.copyOf(values, fieldIndex.size());
                        }

                        for (final CIKey field : usedFields) {
                            setValue(copy, fieldIndex, field, annotation);
                        }

                        valuesConsumer.accept(Val.of(copy));
                    }
                } catch (final RuntimeException e) {
                    LOGGER.debug(e::getMessage, e);
                }
            }
        };
    }

    private Annotation createDefaultAnnotation() {
        final Annotation annotation = new Annotation();
        annotation.setStatus(annotationConfig.getCreateText());
        return annotation;
    }

    private Function<Annotation, Boolean> createFilter(final ExpressionOperator expression) {
        final ExpressionFilter expressionFilter = ExpressionFilter.builder()
                .addPrefixIncludeFilter(AnnotationFields.ANNOTATION_FIELD_PREFIX)
                .addReplacementFilter(
                        AnnotationFields.CURRENT_USER_FUNCTION,
                        securityContext.getUserIdentityForAudit())
                .build();

        final ExpressionOperator filteredExpression = expressionFilter.copy(expression);

        final List<String> expressionValues = ExpressionUtil.values(filteredExpression);
        if (NullSafe.isEmptyCollection(expressionValues)) {
            return null;
        }
        final Set<CIKey> usedFields = new CIHashSet(ExpressionUtil.fields(filteredExpression));
        if (usedFields.isEmpty()) {
            return null;
        }

        final ExpressionMatcher expressionMatcher = expressionMatcherFactory.create(AnnotationFields.FIELD_MAP);
        return annotation -> {
            final Map<CIKey, Object> attributeMap = new HashMap<>();
            for (final CIKey field : usedFields) {
                final Object value = OBJECT_MAPPING.get(field)
                        .apply(annotation);
                attributeMap.put(field, value);
            }
            return expressionMatcher.match(attributeMap, filteredExpression);
        };
    }

    private Long getLong(final Val[] values, final int index) {
        Long result = null;
        if (values.length > index) {
            Val val = values[index];
            if (val != null) {
                result = val.toLong();
            }
        }
        return result;
    }

    private void setValue(final Val[] values,
                          final FieldIndex fieldIndex,
                          final CIKey field,
                          final Annotation annotation) {
        final Integer index = fieldIndex.getPos(field);
        if (index != null && values.length > index) {
            // Only add values that are missing.
            if (values[index] == null) {
                final Val val;
                try {
                    val = VALUE_MAPPING.get(field)
                            .apply(annotation);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                values[index] = val;
            }
        }
    }

    private static <T> Entry<CIKey, Function<Annotation, Val>> nullSafeEntry(
            final QueryField field,
            final Function<Annotation, T> getter) {
        return nullSafeEntry(field, getter, null);
    }

    /**
     * @param creator An explicit creator function to use rather than inferring it from the type.
     */
    private static <T> Entry<CIKey, Function<Annotation, Val>> nullSafeEntry(
            final QueryField field,
            final Function<Annotation, T> getter,
            final Function<T, Val> creator) {

        Objects.requireNonNull(field);
        Objects.requireNonNull(getter);
        final CIKey fieldName = field.getFldNameAsCIKey();
        return Map.entry(fieldName, annotation -> {
            T value = getter.apply(annotation);

            if (value == null) {
                return ValNull.INSTANCE;
            } else {
                if (creator != null) {
                    return creator.apply(value);
                } else if (value instanceof String str) {
                    return ValString.create(str);
                } else if (value instanceof Long aLong) {
                    return ValLong.create(aLong);
                } else if (value instanceof final UserName userName) {
                    return ValString.create(userName.getUserIdentityForAudit());
                } else {
                    throw new RuntimeException("Unexpected type " + value.getClass().getName());
                }
            }
        });
    }
}
