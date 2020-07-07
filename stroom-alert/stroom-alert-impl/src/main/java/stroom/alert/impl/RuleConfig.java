package stroom.alert.impl;

import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.TableSettings;

import java.util.Map;

class RuleConfig {
    private final ExpressionOperator expression;
    private final TableSettings tableSettings;
    private final Map<String, String> params;

    RuleConfig(final ExpressionOperator expression,
               final TableSettings tableSettings,
               final Map<String, String> params){
        this.expression = expression;
        this.tableSettings = tableSettings;
        this.params = params;
    }

    public ExpressionOperator getExpression() {
        return expression;
    }

    public TableSettings getTableSettings() {
        return tableSettings;
    }

    public Map<String, String> getParamMap() {
        return params;
    }
}
