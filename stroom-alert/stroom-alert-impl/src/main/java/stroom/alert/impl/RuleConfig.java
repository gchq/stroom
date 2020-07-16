/*
 * Copyright 2020 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package stroom.alert.impl;

import stroom.alert.api.AlertDefinition;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;

import java.util.List;
import java.util.Map;
import java.util.Objects;

class RuleConfig {
    private final String queryId;
    private final ExpressionOperator expression;
    private final DocRef pipeline;
    private final List<AlertDefinition> alertDefinitions;
    private final Map<String, String> params;

    RuleConfig(final String dashboardUUID,
               final String queryId,
               final ExpressionOperator expression,
               final DocRef pipeline,
               final List<AlertDefinition> alertDefinitions,
               final Map<String, String> params){
        this.queryId = dashboardUUID+ "/" + queryId;
        this.expression = expression;
        this.pipeline = pipeline;
        this.alertDefinitions = alertDefinitions;
        this.params = params;
    }

    public String getQueryId() {
        return queryId;
    }

    public DocRef getPipeline() {
        return pipeline;
    }

    public List<AlertDefinition> getAlertDefinitions() {
        return alertDefinitions;
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
