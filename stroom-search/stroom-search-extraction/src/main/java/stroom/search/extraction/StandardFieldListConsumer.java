package stroom.search.extraction;

import stroom.pipeline.filter.FieldValue;
import stroom.query.api.v2.QueryKey;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValuesConsumer;

import java.util.List;


public class StandardFieldListConsumer implements FieldListConsumer {

    private QueryKey queryKey;
    private ValuesConsumer receiver;
    private FieldIndex fieldIndex;

    @Override
    public void accept(final List<FieldValue> fieldValues) {
        final Val[] values = new Val[fieldIndex.size()];
        for (final FieldValue fieldValue : fieldValues) {
            final Integer pos = fieldIndex.getPos(fieldValue.field().getFieldName());
            if (pos != null) {
                values[pos] = fieldValue.value();
            }
        }
        receiver.accept(Val.of(values));
    }

    public QueryKey getQueryKey() {
        return queryKey;
    }

    public void setQueryKey(final QueryKey queryKey) {
        this.queryKey = queryKey;
    }

    public ValuesConsumer getReceiver() {
        return receiver;
    }

    public void setReceiver(final ValuesConsumer receiver) {
        this.receiver = receiver;
    }

    public FieldIndex getFieldIndex() {
        return fieldIndex;
    }

    public void setFieldIndex(final FieldIndex fieldIndex) {
        this.fieldIndex = fieldIndex;
    }
}
