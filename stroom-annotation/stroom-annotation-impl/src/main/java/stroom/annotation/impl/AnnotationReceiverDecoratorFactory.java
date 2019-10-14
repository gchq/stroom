package stroom.annotation.impl;

import org.springframework.stereotype.Component;
import stroom.annotation.api.AnnotationDataSource;
import stroom.annotation.shared.Annotation;
import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValLong;
import stroom.dashboard.expression.v1.ValNull;
import stroom.dashboard.expression.v1.ValString;
import stroom.index.shared.IndexConstants;
import stroom.process.shared.ExpressionUtil;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.Query;
import stroom.search.coprocessor.Receiver;
import stroom.search.coprocessor.ReceiverImpl;
import stroom.search.coprocessor.Values;
import stroom.search.extraction.AnnotationsDecoratorFactory;
import stroom.search.extraction.ExpressionReplacer;
import stroom.security.SecurityContext;
import stroom.streamstore.server.ExpressionMatcher;
import stroom.streamstore.server.ExpressionMatcherFactory;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

@Component
class AnnotationReceiverDecoratorFactory implements AnnotationsDecoratorFactory {
    private final AnnotationDao annotationDao;
    private final ExpressionMatcherFactory expressionMatcherFactory;
    private final AnnotationConfig annotationConfig;
    private final SecurityContext securityContext;

    private static final Map<String, Function<Annotation, Val>> VALUE_MAPPING = new HashMap<>();

    static {
        VALUE_MAPPING.put(AnnotationDataSource.ID, annotation -> annotation.getId() == null ? ValNull.INSTANCE : ValLong.create(annotation.getId()));
        VALUE_MAPPING.put(IndexConstants.STREAM_ID, annotation -> annotation.getMetaId() == null ? ValNull.INSTANCE : ValLong.create(annotation.getMetaId()));
        VALUE_MAPPING.put(IndexConstants.EVENT_ID, annotation -> annotation.getEventId() == null ? ValNull.INSTANCE : ValLong.create(annotation.getEventId()));
        VALUE_MAPPING.put(AnnotationDataSource.CREATED_BY, annotation -> annotation.getCreateUser() == null ? ValNull.INSTANCE : ValString.create(annotation.getCreateUser()));
        VALUE_MAPPING.put(AnnotationDataSource.TITLE, annotation -> annotation.getTitle() == null ? ValNull.INSTANCE : ValString.create(annotation.getTitle()));
        VALUE_MAPPING.put(AnnotationDataSource.SUBJECT, annotation -> annotation.getSubject() == null ? ValNull.INSTANCE : ValString.create(annotation.getSubject()));
        VALUE_MAPPING.put(AnnotationDataSource.STATUS, annotation -> annotation.getStatus() == null ? ValNull.INSTANCE : ValString.create(annotation.getStatus()));
        VALUE_MAPPING.put(AnnotationDataSource.ASSIGNED_TO, annotation -> annotation.getAssignedTo() == null ? ValNull.INSTANCE : ValString.create(annotation.getAssignedTo()));
        VALUE_MAPPING.put(AnnotationDataSource.COMMENT, annotation -> annotation.getComment() == null ? ValNull.INSTANCE : ValString.create(annotation.getComment()));
        VALUE_MAPPING.put(AnnotationDataSource.HISTORY, annotation -> annotation.getHistory() == null ? ValNull.INSTANCE : ValString.create(annotation.getHistory()));
    }

    private static final Map<String, Function<Annotation, Object>> OBJECT_MAPPING = new HashMap<>();

    static {
        OBJECT_MAPPING.put(AnnotationDataSource.ID, annotation -> annotation.getId() == null ? -1L : annotation.getId());
        OBJECT_MAPPING.put(IndexConstants.STREAM_ID, annotation -> annotation.getMetaId() == null ? -1L : annotation.getMetaId());
        OBJECT_MAPPING.put(IndexConstants.EVENT_ID, annotation -> annotation.getEventId() == null ? -1L : annotation.getEventId());
        OBJECT_MAPPING.put(AnnotationDataSource.CREATED_BY, annotation -> annotation.getCreateUser() == null ? "" : annotation.getCreateUser());
        OBJECT_MAPPING.put(AnnotationDataSource.TITLE, annotation -> annotation.getTitle() == null ? "" : annotation.getTitle());
        OBJECT_MAPPING.put(AnnotationDataSource.SUBJECT, annotation -> annotation.getSubject() == null ? "" : annotation.getSubject());
        OBJECT_MAPPING.put(AnnotationDataSource.STATUS, annotation -> annotation.getStatus() == null ? "" : annotation.getStatus());
        OBJECT_MAPPING.put(AnnotationDataSource.ASSIGNED_TO, annotation -> annotation.getAssignedTo() == null ? "" : annotation.getAssignedTo());
        OBJECT_MAPPING.put(AnnotationDataSource.COMMENT, annotation -> annotation.getComment() == null ? "" : annotation.getComment());
        OBJECT_MAPPING.put(AnnotationDataSource.HISTORY, annotation -> annotation.getHistory() == null ? "" : annotation.getHistory());
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

        final Consumer<Values> valuesConsumer = values -> {
            Annotation annotation = null;
            if (annotationIdIndex != null) {
                final Long annotationId = getLong(values.getValues(), annotationIdIndex);
                if (annotationId != null) {
                    annotation = annotationDao.get(annotationId);
                }
            }

            if (annotation == null) {
                final Long streamId = getLong(values.getValues(), streamIdIndex);
                final Long eventId = getLong(values.getValues(), eventIdIndex);
                if (streamId != null && eventId != null) {
                    annotation = annotationDao.get(streamId, eventId);
                }
            }

            if (annotation == null) {
                annotation = defaultAnnotation;
            }

            // Filter based on annotation.
            if (filter == null || filter.apply(annotation)) {
                for (final String field : usedFields) {
                    setValue(values.getValues(), fieldIndexMap, field, annotation);
                }
                receiver.getValuesConsumer().accept(values);
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
        final ExpressionReplacer expressionReplacer = new ExpressionReplacer(AnnotationDataSource.ANNOTATION_FIELD_PREFIX, false, securityContext.getUserId());
        final ExpressionOperator filteredExpression = expressionReplacer.copy(expression);

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
