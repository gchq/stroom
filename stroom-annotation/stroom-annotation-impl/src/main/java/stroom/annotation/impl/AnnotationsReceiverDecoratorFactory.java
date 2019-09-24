package stroom.annotation.impl;

import org.springframework.stereotype.Component;
import stroom.annotation.shared.Annotation;
import stroom.annotation.api.AnnotationDataSource;
import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValString;
import stroom.index.shared.IndexConstants;
import stroom.query.api.v2.Query;
import stroom.search.coprocessor.Receiver;
import stroom.search.coprocessor.ReceiverImpl;
import stroom.search.coprocessor.Values;
import stroom.search.extraction.AnnotationsDecoratorFactory;

import javax.inject.Inject;
import java.util.function.Consumer;

@Component
class AnnotationsReceiverDecoratorFactory implements AnnotationsDecoratorFactory {
    private final AnnotationsDao annotationsDao;

    @Inject
    AnnotationsReceiverDecoratorFactory(final AnnotationsDao annotationsDao) {
        this.annotationsDao = annotationsDao;
    }

    @Override
    public Receiver create(final Receiver receiver, final Query query) {
        final FieldIndexMap fieldIndexMap = receiver.getFieldIndexMap();
        final Integer streamIdIndex = fieldIndexMap.getMap().get(IndexConstants.STREAM_ID);
        final Integer eventIdIndex = fieldIndexMap.getMap().get(IndexConstants.EVENT_ID);

        if (streamIdIndex == null || eventIdIndex == null) {
            return receiver;
        }

        final Integer createUserIndex = fieldIndexMap.getMap().get(AnnotationDataSource.CREATE_USER);
//        final Integer commentIndex = fieldIndexMap.getMap().get(AnnotationDataSource.COMMENT);
        final Integer statusIndex = fieldIndexMap.getMap().get(AnnotationDataSource.STATUS);
        final Integer assignedToIndex = fieldIndexMap.getMap().get(AnnotationDataSource.ASSIGNED_TO);

        if (createUserIndex == null && statusIndex == null && assignedToIndex == null) {
            return receiver;
        }

        final Consumer<Values> valuesConsumer = values -> {
            final Long streamId = getLong(values.getValues(), streamIdIndex);
            final Long eventId = getLong(values.getValues(), eventIdIndex);
            final Annotation annotation = annotationsDao.get(streamId, eventId);
            if (annotation == null) {
                setValue(values.getValues(), statusIndex, "None");
            } else {
                setValue(values.getValues(), createUserIndex, annotation.getCreateUser());
//                setValue(values.getValues(), commentIndex, annotation.getComment());
                setValue(values.getValues(), statusIndex, annotation.getStatus());
                setValue(values.getValues(), assignedToIndex, annotation.getAssignedTo());
            }
            receiver.getValuesConsumer().accept(values);
        };

        // TODO : At present we are just going to do this synchronously but in future we may do asynchronously in which
        // case we would increment the completion count after providing values.
        return new ReceiverImpl(valuesConsumer, receiver.getErrorConsumer(), receiver.getCompletionCountConsumer(), fieldIndexMap);
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
