package stroom.search.extraction;

import stroom.query.api.QueryKey;
import stroom.query.language.functions.FieldIndex;
import stroom.util.pipeline.scope.PipelineScoped;

@PipelineScoped
public class QueryInfoHolder {

    private QueryKey queryKey;
    private FieldIndex fieldIndex;

    public QueryKey getQueryKey() {
        return queryKey;
    }

    public void setQueryKey(final QueryKey queryKey) {
        this.queryKey = queryKey;
    }

    public FieldIndex getFieldIndex() {
        return fieldIndex;
    }

    public void setFieldIndex(final FieldIndex fieldIndex) {
        this.fieldIndex = fieldIndex;
    }
}
