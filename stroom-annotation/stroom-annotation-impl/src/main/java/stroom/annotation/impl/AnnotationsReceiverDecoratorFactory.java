package stroom.annotation.impl;

import org.springframework.stereotype.Component;
import stroom.annotation.shared.Annotation;
import stroom.annotation.api.AnnotationDataSource;
import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValString;
import stroom.datasource.api.v2.DataSourceField;
import stroom.index.shared.IndexConstants;
import stroom.process.shared.ExpressionUtil;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.Query;
import stroom.search.coprocessor.Receiver;
import stroom.search.coprocessor.ReceiverImpl;
import stroom.search.coprocessor.Values;
import stroom.search.extraction.AnnotationsDecoratorFactory;
import stroom.search.extraction.ExpressionFilter;
import stroom.streamstore.server.ExpressionMatcher;
import stroom.streamstore.server.ExpressionMatcherFactory;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

@Component
class AnnotationsReceiverDecoratorFactory implements AnnotationsDecoratorFactory {
    private final AnnotationsDao annotationsDao;
    private final ExpressionMatcherFactory expressionMatcherFactory;

    @Inject
    AnnotationsReceiverDecoratorFactory(final AnnotationsDao annotationsDao, final ExpressionMatcherFactory expressionMatcherFactory) {
        this.annotationsDao = annotationsDao;
        this.expressionMatcherFactory = expressionMatcherFactory;
    }

    @Override
    public Receiver create(final Receiver receiver, final Query query) {
        final FieldIndexMap fieldIndexMap = receiver.getFieldIndexMap();
        final Integer streamIdIndex = fieldIndexMap.getMap().get(IndexConstants.STREAM_ID);
        final Integer eventIdIndex = fieldIndexMap.getMap().get(IndexConstants.EVENT_ID);

        if (streamIdIndex == null || eventIdIndex == null) {
            return receiver;
        }

        // Do we need to filter based on annotation attributes.
        final Function<Annotation, Boolean> filter = createFilter(query.getExpression());

        final Integer createUserIndex = fieldIndexMap.getMap().get(AnnotationDataSource.CREATE_USER);
//        final Integer commentIndex = fieldIndexMap.getMap().get(AnnotationDataSource.COMMENT);
        final Integer statusIndex = fieldIndexMap.getMap().get(AnnotationDataSource.STATUS);
        final Integer assignedToIndex = fieldIndexMap.getMap().get(AnnotationDataSource.ASSIGNED_TO);

        if (filter == null && createUserIndex == null && statusIndex == null && assignedToIndex == null) {
            return receiver;
        }

        final Consumer<Values> valuesConsumer = values -> {
            final Long streamId = getLong(values.getValues(), streamIdIndex);
            final Long eventId = getLong(values.getValues(), eventIdIndex);

            Annotation annotation = annotationsDao.get(streamId, eventId);
            if (annotation == null) {
                annotation = new Annotation();
                annotation.setStatus("None");
            }

            // Filter based on annotation.
            if (filter == null || filter.apply(annotation)) {
                setValue(values.getValues(), createUserIndex, annotation.getCreateUser());
                setValue(values.getValues(), statusIndex, annotation.getStatus());
                setValue(values.getValues(), assignedToIndex, annotation.getAssignedTo());

                receiver.getValuesConsumer().accept(values);
            }
        };

        // TODO : At present we are just going to do this synchronously but in future we may do asynchronously in which
        // case we would increment the completion count after providing values.
        return new ReceiverImpl(valuesConsumer, receiver.getErrorConsumer(), receiver.getCompletionCountConsumer(), fieldIndexMap);
    }

    private Function<Annotation, Boolean> createFilter(final ExpressionOperator expression) {
        final ExpressionOperator filteredExpression = ExpressionFilter.filter(expression, AnnotationDataSource.ANNOTATION_FIELD_PREFIX, false);
        final List<String> expressionValues = ExpressionUtil.values(filteredExpression);
        if (expressionValues == null || expressionValues.size() == 0) {
            return null;
        }

        final ExpressionMatcher expressionMatcher = expressionMatcherFactory.create(AnnotationDataSource.FIELD_MAP);
        return annotation -> {
            final Map<String, Object> attributeMap = new HashMap<>();
            attributeMap.put(AnnotationDataSource.CREATE_USER, annotation.getCreateUser());
            attributeMap.put(AnnotationDataSource.STATUS, annotation.getStatus());
            attributeMap.put(AnnotationDataSource.ASSIGNED_TO, annotation.getAssignedTo());
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

    private void setValue(final Val[] values, final Integer index, final String value) {
        if (index != null && value != null) {
            values[index] = ValString.create(value);
        }
    }
}
