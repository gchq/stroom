package stroom.annotation.impl;

import stroom.annotation.api.AnnotationDataSource;
import stroom.annotation.shared.Annotation;
import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValLong;
import stroom.dashboard.expression.v1.ValNull;
import stroom.dashboard.expression.v1.ValString;
import stroom.expression.matcher.ExpressionMatcher;
import stroom.expression.matcher.ExpressionMatcherFactory;
import stroom.index.shared.IndexConstants;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.Query;
import stroom.search.coprocessor.Receiver;
import stroom.search.coprocessor.ReceiverImpl;
import stroom.search.coprocessor.Values;
import stroom.search.extraction.AnnotationsDecoratorFactory;
import stroom.search.extraction.ExpressionFilter;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

class AnnotationReceiverDecoratorFactory implements AnnotationsDecoratorFactory {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnnotationReceiverDecoratorFactory.class);

    private final AnnotationDao annotationDao;
    private final ExpressionMatcherFactory expressionMatcherFactory;
    private final AnnotationConfig annotationConfig;
    private final SecurityContext securityContext;

    private static final Map<String, Function<Annotation, Val>> VALUE_MAPPING = new HashMap<>();

    static {
        VALUE_MAPPING.put(AnnotationDataSource.ID, annotation -> annotation.getId() == null ? ValNull.INSTANCE : ValLong.create(annotation.getId()));
        VALUE_MAPPING.put(AnnotationDataSource.CREATED_ON, annotation -> annotation.getCreateTime() == null ? ValNull.INSTANCE : ValLong.create(annotation.getCreateTime()));
        VALUE_MAPPING.put(AnnotationDataSource.CREATED_BY, annotation -> annotation.getCreateUser() == null ? ValNull.INSTANCE : ValString.create(annotation.getCreateUser()));
        VALUE_MAPPING.put(AnnotationDataSource.UPDATED_ON, annotation -> annotation.getUpdateTime() == null ? ValNull.INSTANCE : ValLong.create(annotation.getUpdateTime()));
        VALUE_MAPPING.put(AnnotationDataSource.UPDATED_BY, annotation -> annotation.getUpdateUser() == null ? ValNull.INSTANCE : ValString.create(annotation.getUpdateUser()));
        VALUE_MAPPING.put(AnnotationDataSource.TITLE, annotation -> annotation.getTitle() == null ? ValNull.INSTANCE : ValString.create(annotation.getTitle()));
        VALUE_MAPPING.put(AnnotationDataSource.SUBJECT, annotation -> annotation.getSubject() == null ? ValNull.INSTANCE : ValString.create(annotation.getSubject()));
        VALUE_MAPPING.put(AnnotationDataSource.STATUS, annotation -> annotation.getStatus() == null ? ValNull.INSTANCE : ValString.create(annotation.getStatus()));
        VALUE_MAPPING.put(AnnotationDataSource.ASSIGNED_TO, annotation -> annotation.getAssignedTo() == null ? ValNull.INSTANCE : ValString.create(annotation.getAssignedTo()));
        VALUE_MAPPING.put(AnnotationDataSource.COMMENT, annotation -> annotation.getComment() == null ? ValNull.INSTANCE : ValString.create(annotation.getComment()));
        VALUE_MAPPING.put(AnnotationDataSource.HISTORY, annotation -> annotation.getHistory() == null ? ValNull.INSTANCE : ValString.create(annotation.getHistory()));
    }

    private static final Map<String, Function<Annotation, Object>> OBJECT_MAPPING = new HashMap<>();

    static {
        OBJECT_MAPPING.put(AnnotationDataSource.ID, Annotation::getId);
        OBJECT_MAPPING.put(AnnotationDataSource.CREATED_ON, Annotation::getCreateTime);
        OBJECT_MAPPING.put(AnnotationDataSource.CREATED_BY, Annotation::getCreateUser);
        OBJECT_MAPPING.put(AnnotationDataSource.UPDATED_ON, Annotation::getUpdateTime);
        OBJECT_MAPPING.put(AnnotationDataSource.UPDATED_BY, Annotation::getUpdateUser);
        OBJECT_MAPPING.put(AnnotationDataSource.TITLE, Annotation::getTitle);
        OBJECT_MAPPING.put(AnnotationDataSource.SUBJECT, Annotation::getSubject);
        OBJECT_MAPPING.put(AnnotationDataSource.STATUS, Annotation::getStatus);
        OBJECT_MAPPING.put(AnnotationDataSource.ASSIGNED_TO, Annotation::getAssignedTo);
        OBJECT_MAPPING.put(AnnotationDataSource.COMMENT, Annotation::getComment);
        OBJECT_MAPPING.put(AnnotationDataSource.HISTORY, Annotation::getHistory);
    }

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
    public Receiver create(final Receiver receiver, final Query query) {
        final FieldIndexMap fieldIndexMap = receiver.getFieldIndexMap();
        final Integer annotationIdIndex = fieldIndexMap.getMap().get(AnnotationDataSource.ID);
        final Integer streamIdIndex = fieldIndexMap.getMap().get(IndexConstants.STREAM_ID);
        final Integer eventIdIndex = fieldIndexMap.getMap().get(IndexConstants.EVENT_ID);

        if (annotationIdIndex == null && (streamIdIndex == null || eventIdIndex == null)) {
            return receiver;
        }

        // Do we need to filter based on annotation attributes.
        final Function<Annotation, Boolean> filter = createFilter(query.getExpression());

        final Set<String> usedFields = new HashSet<>(fieldIndexMap.getMap().keySet());
        usedFields.retainAll(AnnotationDataSource.FIELD_MAP.keySet());

        if (filter == null && usedFields.size() == 0) {
            return receiver;
        }

        final Annotation defaultAnnotation = createDefaultAnnotation();

        final Consumer<Values> valuesConsumer = v -> {
            final List<Annotation> annotations = new ArrayList<>();
            if (annotationIdIndex != null) {
                final Long annotationId = getLong(v.getValues(), annotationIdIndex);
                if (annotationId != null) {
                    annotations.add(annotationDao.get(annotationId));
                }
            }

            if (annotations.size() == 0) {
                final Long streamId = getLong(v.getValues(), streamIdIndex);
                final Long eventId = getLong(v.getValues(), eventIdIndex);
                if (streamId != null && eventId != null) {
                    final List<Annotation> list = annotationDao.getAnnotationsForEvents(streamId, eventId);
                    annotations.addAll(list);
                }
            }

            if (annotations.size() == 0) {
                annotations.add(defaultAnnotation);
            }

            // Filter based on annotation.
            Values values = v;
            for (final Annotation annotation : annotations) {
                try {
                    if (filter == null || filter.apply(annotation)) {
                        // If we have more that one annotation then copy the original values into a new values object for each new row.
                        if (annotations.size() > 1) {
                            final Val[] copy = Arrays.copyOf(v.getValues(), v.getValues().length);
                            values = new Values(copy);
                        }

                        for (final String field : usedFields) {
                            setValue(values.getValues(), fieldIndexMap, field, annotation);
                        }

                        receiver.getValuesConsumer().accept(values);
                    }
                } catch (final RuntimeException e) {
                    LOGGER.debug(e::getMessage, e);
                }
            }
        };

        // TODO : At present we are just going to do this synchronously but in future we may do asynchronously in which
        // case we would increment the completion count after providing values.
        return new ReceiverImpl(valuesConsumer, receiver.getErrorConsumer(), receiver.getCompletionCountConsumer(), fieldIndexMap);
    }

    private Annotation createDefaultAnnotation() {
        final Annotation annotation = new Annotation();
        annotation.setStatus(annotationConfig.getCreateText());
        return annotation;
    }

    private Function<Annotation, Boolean> createFilter(final ExpressionOperator expression) {
        final ExpressionFilter expressionFilter = new ExpressionFilter.Builder()
                .addPrefixIncludeFilter(AnnotationDataSource.ANNOTATION_FIELD_PREFIX)
                .addReplacementFilter(AnnotationDataSource.CURRENT_USER_FUNCTION, securityContext.getUserId())
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

        final ExpressionMatcher expressionMatcher = expressionMatcherFactory.create(AnnotationDataSource.FIELD_MAP);
        return annotation -> {
            final Map<String, Object> attributeMap = new HashMap<>();
            for (final String field : usedFields) {
                final Object value = OBJECT_MAPPING.get(field).apply(annotation);
                attributeMap.put(field, value);
            }
            return expressionMatcher.match(attributeMap, filteredExpression);
        };
    }

    private Long getLong(final Val[] values, final int index) {
        Val val = values[index];
        if (val == null) {
            return null;
        }
        return val.toLong();
    }

    private void setValue(final Val[] values, final FieldIndexMap fieldIndexMap, final String field, final Annotation annotation) {
        final int index = fieldIndexMap.get(field);
        if (index != -1) {
            // Only add values that are missing.
            if (values[index] == null) {
                final Val val = VALUE_MAPPING.get(field).apply(annotation);
                values[index] = val;
            }
        }
    }
}
