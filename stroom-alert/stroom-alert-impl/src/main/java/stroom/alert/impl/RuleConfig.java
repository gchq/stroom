package stroom.alert.impl;

import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.TableSettings;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

class RuleConfig {
    private final String queryId;
    private final ExpressionOperator expression;
    private final DocRef pipeline;
    private final List<TableSettings> tableSettings;
    private final Map<String, String> params;

    RuleConfig(final String queryId,
               final ExpressionOperator expression,
               final DocRef pipeline,
               final List<TableSettings> tableSettings,
               final Map<String, String> params){
        this.queryId = queryId;
        this.expression = expression;
        this.pipeline = pipeline;
        this.tableSettings = tableSettings;
        this.params = params;
    }

    public String getQueryId() {
        return queryId;
    }

    public DocRef getPipeline() {
        return pipeline;
    }

    public List<TableSettings> getTableSettings() {
        return tableSettings;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public ExpressionOperator getExpression() {
        return expression;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final RuleConfig that = (RuleConfig) o;
        return queryId.equals(that.queryId) &&
                pipeline.equals(that.pipeline);
    }

    @Override
    public int hashCode() {
        return Objects.hash(queryId, pipeline);
    }
}
