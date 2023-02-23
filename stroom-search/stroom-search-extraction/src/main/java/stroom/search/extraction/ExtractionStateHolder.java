package stroom.search.extraction;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.query.api.v2.QueryKey;
import stroom.util.pipeline.scope.PipelineScoped;

@PipelineScoped
public class ExtractionStateHolder {
    private QueryKey queryKey;
    private ValuesConsumer receiver;
    private FieldIndex fieldIndex;
    private int count;

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

    public int getCount() {
        return count;
    }

    public void incrementCount() {
        count++;
    }
}
