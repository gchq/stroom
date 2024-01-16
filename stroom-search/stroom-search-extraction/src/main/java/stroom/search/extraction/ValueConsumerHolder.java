package stroom.search.extraction;

import stroom.query.api.v2.QueryKey;
import stroom.query.common.v2.StringFieldValue;
import stroom.query.language.functions.FieldIndex;
import stroom.util.pipeline.scope.PipelineScoped;

import java.util.List;
import javax.inject.Inject;

@PipelineScoped
public class ValueConsumerHolder {

    private final ExtractionState extractionState;
    private QueryKey queryKey;
    private FieldListConsumer fieldListConsumer;
    private FieldIndex fieldIndex;

    @Inject
    ValueConsumerHolder(final ExtractionState extractionState) {
        this.extractionState = extractionState;
    }

    public void acceptStringValues(final List<StringFieldValue> stringValues) {
        fieldListConsumer.acceptStringValues(stringValues);
        extractionState.incrementCount();
    }

    public QueryKey getQueryKey() {
        return queryKey;
    }

    public void setQueryKey(final QueryKey queryKey) {
        this.queryKey = queryKey;
    }

    public void setFieldListConsumer(final FieldListConsumer fieldListConsumer) {
        this.fieldListConsumer = fieldListConsumer;
    }

    public FieldIndex getFieldIndex() {
        return fieldIndex;
    }

    public void setFieldIndex(final FieldIndex fieldIndex) {
        this.fieldIndex = fieldIndex;
    }
}
