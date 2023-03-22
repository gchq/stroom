package stroom.annotation.impl;

import stroom.annotation.api.AnnotationFields;
import stroom.annotation.shared.Annotation;
import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValLong;
import stroom.dashboard.expression.v1.ValNull;
import stroom.dashboard.expression.v1.ValString;
import stroom.dashboard.expression.v1.Values;
import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.expression.matcher.ExpressionMatcher;
import stroom.expression.matcher.ExpressionMatcherFactory;
import stroom.index.shared.IndexConstants;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.Query;
import stroom.search.extraction.AnnotationsDecoratorFactory;
import stroom.search.extraction.ExpressionFilter;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import javax.inject.Inject;

class AnnotationReceiverDecoratorFactory implements AnnotationsDecoratorFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnnotationReceiverDecoratorFactory.class);

    private static final Map<String, Function<Annotation, Val>> VALUE_MAPPING = Map.ofEntries(
            nullSafeEntry(AnnotationFields.ID, Annotation::getId),
            nullSafeEntry(AnnotationFields.CREATED_ON, Annotation::getCreateTime),
            nullSafeEntry(AnnotationFields.CREATED_BY, Annotation::getCreateUser),
            nullSafeEntry(AnnotationFields.UPDATED_ON, Annotation::getUpdateTime),
            nullSafeEntry(AnnotationFields.UPDATED_BY, Annotation::getUpdateTime),
            nullSafeEntry(AnnotationFields.TITLE, Annotation::getTitle),
            nullSafeEntry(AnnotationFields.SUBJECT, Annotation::getSubject),
            nullSafeEntry(AnnotationFields.STATUS, Annotation::getStatus),
            nullSafeEntry(AnnotationFields.ASSIGNED_TO, Annotation::getAssignedTo),
            nullSafeEntry(AnnotationFields.COMMENT, Annotation::getComment),
            nullSafeEntry(AnnotationFields.HISTORY, Annotation::getHistory));

    private static final Map<String, Function<Annotation, Object>> OBJECT_MAPPING = Map.ofEntries(
            Map.entry(AnnotationFields.ID, Annotation::getId),
            Map.entry(AnnotationFields.CREATED_ON, Annotation::getCreateTime),
            Map.entry(AnnotationFields.CREATED_BY, Annotation::getCreateUser),
            Map.entry(AnnotationFields.UPDATED_ON, Annotation::getUpdateTime),
            Map.entry(AnnotationFields.UPDATED_BY, Annotation::getUpdateUser),
            Map.entry(AnnotationFields.TITLE, Annotation::getTitle),
            Map.entry(AnnotationFields.SUBJECT, Annotation::getSubject),
            Map.entry(AnnotationFields.STATUS, Annotation::getStatus),
            Map.entry(AnnotationFields.ASSIGNED_TO, Annotation::getAssignedTo),
            Map.entry(AnnotationFields.COMMENT, Annotation::getComment),
            Map.entry(AnnotationFields.HISTORY, Annotation::getHistory));

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
        final Integer annotationIdIndex = fieldIndex.getPos(AnnotationFields.ID);
        final Integer streamIdIndex = fieldIndex.getPos(IndexConstants.STREAM_ID);
        final Integer eventIdIndex = fieldIndex.getPos(IndexConstants.EVENT_ID);

        if (annotationIdIndex == null && (streamIdIndex == null || eventIdIndex == null)) {
            return valuesConsumer;
        }

        // Do we need to filter based on annotation attributes?
        final Function<Annotation, Boolean> filter = createFilter(query.getExpression());

        final Set<String> usedFields = new HashSet<>(fieldIndex.getFieldNames());
        usedFields.retainAll(AnnotationFields.FIELD_MAP.keySet());

        if (filter == null && usedFields.size() == 0) {
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

            if (annotations.size() == 0) {
                final Long streamId = getLong(values, streamIdIndex);
                final Long eventId = getLong(values, eventIdIndex);
                if (streamId != null && eventId != null) {
                    final List<Annotation> list = annotationDao.getAnnotationsForEvents(streamId, eventId);
                    annotations.addAll(list);
                }
            }

            if (annotations.size() == 0) {
                annotations.add(defaultAnnotation);
            }

            Val[] copy = values.toUnsafeArray();
            for (final Annotation annotation : annotations) {
                try {
                    if (filter == null || filter.apply(annotation)) {
                        // If we have more than one annotation then copy the original values into a new
                        // values object for each new row.
                        if (annotations.size() > 1 || copy.length < fieldIndex.size()) {
                            copy = Arrays.copyOf(values.toUnsafeArray(), fieldIndex.size());
                        }

                        for (final String field : usedFields) {
                            setValue(copy, fieldIndex, field, annotation);
                        }

                        valuesConsumer.add(Values.of(copy));
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
                .addReplacementFilter(AnnotationFields.CURRENT_USER_FUNCTION, securityContext.getUserId())
                .build();

        final ExpressionOperator filteredExpression = expressionFilter.copy(expression);

        final List<String> expressionValues = ExpressionUtil.values(filteredExpression);
        if (expressionValues == null || expressionValues.size() == 0) {
            return null;
        }
        final Set<String> usedFields = new HashSet<>(ExpressionUtil.fields(filteredExpression));
        if (usedFields.size() == 0) {
            return null;
        }

        final ExpressionMatcher expressionMatcher = expressionMatcherFactory.create(AnnotationFields.FIELD_MAP);
        return annotation -> {
            final Map<String, Object> attributeMap = new HashMap<>();
            for (final String field : usedFields) {
                final Object value = OBJECT_MAPPING.get(field).apply(annotation);
                attributeMap.put(field, value);
            }
            return expressionMatcher.match(attributeMap, filteredExpression);
        };
    }

    private Long getLong(final Values values, final int index) {
        Long result = null;
        if (values.size() > index) {
            Val val = values.get(index);
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
                final Val val = VALUE_MAPPING.get(field).apply(annotation);
                values[index] = val;
            }
        }
    }

    private static <T> Entry<String, Function<Annotation, Val>> nullSafeEntry(
            final String fieldName,
            final Function<Annotation, T> getter) {

        return Map.entry(fieldName, annotation -> {
            T value = getter.apply(annotation);

            if (value == null) {
                return ValNull.INSTANCE;
            } else {
                if (value instanceof String) {
                    return ValString.create((String) value);
                }
                if (value instanceof Long) {
                    return ValLong.create((Long) value);
                } else {
                    throw new RuntimeException("Unexpected type " + value.getClass().getName());
                }
            }
        });
    }
}
