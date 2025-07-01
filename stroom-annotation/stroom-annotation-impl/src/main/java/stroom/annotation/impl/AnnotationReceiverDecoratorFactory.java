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

import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationDecorationFields;
import stroom.annotation.shared.AnnotationTag;
import stroom.annotation.shared.EventId;
import stroom.expression.matcher.ExpressionMatcher;
import stroom.expression.matcher.ExpressionMatcherFactory;
import stroom.index.shared.IndexConstants;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.Query;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ParamKeys;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.search.extraction.AnnotationsDecoratorFactory;
import stroom.search.extraction.ExpressionFilter;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Deprecated // Users should no longer use pre decoration
class AnnotationReceiverDecoratorFactory implements AnnotationsDecoratorFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnnotationReceiverDecoratorFactory.class);

    private static final Annotation DEFAULT_ANNOTATION = Annotation.builder().build();
    private static AnnotationConfig ANNOTATION_CONFIG;

    private static final Map<String, Function<Annotation, Val>> VALUE_MAPPING = Map.ofEntries(
            nullSafeEntry(AnnotationDecorationFields.ANNOTATION_ID, Annotation::getId),
            nullSafeEntry(AnnotationDecorationFields.ANNOTATION_UUID, Annotation::getUuid),
            nullSafeEntry(AnnotationDecorationFields.ANNOTATION_CREATED_ON, Annotation::getCreateTimeMs),
            nullSafeEntry(AnnotationDecorationFields.ANNOTATION_CREATED_BY, Annotation::getCreateUser),
            nullSafeEntry(AnnotationDecorationFields.ANNOTATION_UPDATED_ON, Annotation::getUpdateTimeMs),
            nullSafeEntry(AnnotationDecorationFields.ANNOTATION_UPDATED_BY, Annotation::getUpdateUser),
            nullSafeEntry(AnnotationDecorationFields.ANNOTATION_TITLE, Annotation::getName),
            nullSafeEntry(AnnotationDecorationFields.ANNOTATION_SUBJECT, Annotation::getSubject),
            nullSafeEntry(AnnotationDecorationFields.ANNOTATION_STATUS,
                    AnnotationReceiverDecoratorFactory::getStatusString),
            nullSafeEntry(AnnotationDecorationFields.ANNOTATION_LABEL, annotation ->
                    getTagString(annotation.getLabels())),
            nullSafeEntry(AnnotationDecorationFields.ANNOTATION_COLLECTION, annotation ->
                    getTagString(annotation.getCollections())),
            nullSafeEntry(AnnotationDecorationFields.ANNOTATION_ASSIGNED_TO, annotation ->
                    NullSafe.get(annotation.getAssignedTo(), UserRef::toDisplayString)),
            nullSafeEntry(AnnotationDecorationFields.ANNOTATION_COMMENT, Annotation::getComment),
            nullSafeEntry(AnnotationDecorationFields.ANNOTATION_HISTORY, Annotation::getHistory));

    private static final Map<String, Function<Annotation, Object>> OBJECT_MAPPING = Map.ofEntries(
            Map.entry(AnnotationDecorationFields.ANNOTATION_ID, Annotation::getId),
            Map.entry(AnnotationDecorationFields.ANNOTATION_UUID, Annotation::getUuid),
            Map.entry(AnnotationDecorationFields.ANNOTATION_CREATED_ON, Annotation::getCreateTimeMs),
            Map.entry(AnnotationDecorationFields.ANNOTATION_CREATED_BY, Annotation::getCreateUser),
            Map.entry(AnnotationDecorationFields.ANNOTATION_UPDATED_ON, Annotation::getUpdateTimeMs),
            Map.entry(AnnotationDecorationFields.ANNOTATION_UPDATED_BY, Annotation::getUpdateUser),
            Map.entry(AnnotationDecorationFields.ANNOTATION_TITLE, Annotation::getName),
            Map.entry(AnnotationDecorationFields.ANNOTATION_SUBJECT, Annotation::getSubject),
            Map.entry(AnnotationDecorationFields.ANNOTATION_STATUS,
                    AnnotationReceiverDecoratorFactory::getStatusString),
            Map.entry(AnnotationDecorationFields.ANNOTATION_LABEL, annotation ->
                    getTagString(annotation.getLabels())),
            Map.entry(AnnotationDecorationFields.ANNOTATION_COLLECTION, annotation ->
                    getTagString(annotation.getCollections())),
            Map.entry(AnnotationDecorationFields.ANNOTATION_ASSIGNED_TO, annotation ->
                    NullSafe.get(annotation.getAssignedTo(), UserRef::toDisplayString)),
            Map.entry(AnnotationDecorationFields.ANNOTATION_COMMENT, Annotation::getComment),
            Map.entry(AnnotationDecorationFields.ANNOTATION_HISTORY, Annotation::getHistory));

    private final AnnotationService annotationService;
    private final ExpressionMatcherFactory expressionMatcherFactory;

    private final SecurityContext securityContext;

    @Inject
    AnnotationReceiverDecoratorFactory(final AnnotationService annotationService,
                                       final ExpressionMatcherFactory expressionMatcherFactory,
                                       final AnnotationConfig annotationConfig,
                                       final SecurityContext securityContext) {
        this.annotationService = annotationService;
        this.expressionMatcherFactory = expressionMatcherFactory;
        ANNOTATION_CONFIG = annotationConfig;
        this.securityContext = securityContext;
    }

    private static String getTagString(final Collection<AnnotationTag> tags) {
        if (tags == null) {
            return null;
        }
        return tags.stream().map(AnnotationTag::getName).collect(Collectors.joining("|"));
    }

    private static String getStatusString(final Annotation annotation) {
        String value = NullSafe.get(ANNOTATION_CONFIG, AnnotationConfig::getCreateText);
        if (annotation != null) {
            if (annotation.getStatus() != null) {
                value = annotation.getStatus().getName();
            } else if (annotation.getId() != null) {
                value = "None";
            }
        }
        return value;
    }

    @Override
    public ValuesConsumer create(final ValuesConsumer valuesConsumer,
                                 final FieldIndex fieldIndex,
                                 final Query query) {
        final Integer annotationIdIndex = fieldIndex.getPos(AnnotationDecorationFields.ANNOTATION_ID);
        final Integer streamIdIndex = fieldIndex.getPos(IndexConstants.STREAM_ID);
        final Integer eventIdIndex = fieldIndex.getPos(IndexConstants.EVENT_ID);

        if (annotationIdIndex == null && (streamIdIndex == null || eventIdIndex == null)) {
            return valuesConsumer;
        }

        // Do we need to filter based on annotation attributes?
        final Function<Annotation, Boolean> filter = createFilter(query.getExpression());

        final Set<String> usedFields = new HashSet<>(Set.of(fieldIndex.getFields()));
        usedFields.retainAll(AnnotationDecorationFields.DECORATION_FIELD_MAP.keySet());

        if (filter == null && usedFields.isEmpty()) {
            return valuesConsumer;
        }

        return values -> {
            // TODO : At present we are just going to do this synchronously but in future we may do asynchronously
            //  in which case we would increment the completion count after providing values.

            // Filter based on annotation.
            final List<Annotation> annotations = new ArrayList<>();
            if (annotationIdIndex != null) {
                final Long annotationId = getLong(values, annotationIdIndex);
                if (annotationId != null) {
                    annotationService.getAnnotationById(annotationId).ifPresent(annotations::add);
                }
            }

            if (annotations.isEmpty()) {
                final Long streamId = getLong(values, streamIdIndex);
                final Long eventId = getLong(values, eventIdIndex);
                if (streamId != null && eventId != null) {
                    final List<Annotation> list = annotationService
                            .getAnnotationsForEvents(new EventId(streamId, eventId));
                    annotations.addAll(list);
                }
            }

            if (annotations.isEmpty()) {
                annotations.add(DEFAULT_ANNOTATION);
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

                        for (final String field : usedFields) {
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

    private Function<Annotation, Boolean> createFilter(final ExpressionOperator expression) {
        final ExpressionFilter expressionFilter = ExpressionFilter.builder()
                .addPrefixIncludeFilter(AnnotationDecorationFields.ANNOTATION_FIELD_PREFIX)
                .addReplacementFilter(
                        ParamKeys.CURRENT_USER,
                        securityContext.getUserIdentityForAudit())
                .build();

        final ExpressionOperator filteredExpression = expressionFilter.copy(expression);

        final List<String> expressionValues = ExpressionUtil.values(filteredExpression);
        if (expressionValues == null || expressionValues.isEmpty()) {
            return null;
        }
        final Set<String> usedFields = new HashSet<>(ExpressionUtil.fields(filteredExpression));
        if (usedFields.isEmpty()) {
            return null;
        }

        final ExpressionMatcher expressionMatcher = expressionMatcherFactory
                .create(AnnotationDecorationFields.DECORATION_FIELD_MAP);
        return annotation -> {
            final Map<String, Object> attributeMap = new HashMap<>();
            for (final String field : usedFields) {
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
            final Val val = values[index];
            if (val != null) {
                result = val.toLong();
            }
        }
        return result;
    }

    private void setValue(final Val[] values,
                          final FieldIndex fieldIndex,
                          final String field,
                          final Annotation annotation) {
        final Integer index = fieldIndex.getPos(field);
        if (index != null && values.length > index) {
            // Only add values that are missing.
            if (values[index] == null) {
                final Val val;
                try {
                    final Function<Annotation, Val> mapping = VALUE_MAPPING.get(field);
                    if (mapping != null) {
                        val = mapping.apply(annotation);
                    } else {
                        val = ValNull.INSTANCE;
                    }
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }
                values[index] = val;
            }
        }
    }

    private static <T> Entry<String, Function<Annotation, Val>> nullSafeEntry(
            final String fieldName,
            final Function<Annotation, T> getter) {
        return nullSafeEntry(fieldName, getter, null);
    }

    /**
     * @param creator An explicit creator function to use rather than inferring it from the type.
     */
    private static <T> Entry<String, Function<Annotation, Val>> nullSafeEntry(
            final String fieldName,
            final Function<Annotation, T> getter,
            final Function<T, Val> creator) {

        Objects.requireNonNull(fieldName);
        Objects.requireNonNull(getter);
        return Map.entry(fieldName, annotation -> {
            final T value = getter.apply(annotation);

            if (value == null) {
                return ValNull.INSTANCE;
            } else {
                if (creator != null) {
                    return creator.apply(value);
                } else if (value instanceof String) {
                    return ValString.create((String) value);
                } else if (value instanceof Long) {
                    return ValLong.create((Long) value);
                } else if (value instanceof final UserRef userRef) {
                    return ValString.create(userRef.toDisplayString());
                } else {
                    throw new RuntimeException("Unexpected type " + value.getClass().getName());
                }
            }
        });
    }
}
