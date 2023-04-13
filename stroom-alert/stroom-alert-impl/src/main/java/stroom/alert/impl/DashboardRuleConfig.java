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
import stroom.alert.api.AlertManager;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

class DashboardRuleConfig implements RuleConfig {

    private final String queryId;
    private final Map<String, String> paramMap;
    private final ExpressionOperator expression;
    private final DocRef extractionPipeline;
    private final List<AlertDefinition> alertDefinitions;
    private final String dashboardNames;

    DashboardRuleConfig(final String dashboardUUID,
                        final String queryId,
                        final Map<String, String> paramMap,
                        final ExpressionOperator expression,
                        final DocRef extractionPipeline,
                        final List<AlertDefinition> alertDefinitions) {
        this.queryId = dashboardUUID + "/" + queryId;
        this.paramMap = paramMap;
        this.expression = expression;
        this.extractionPipeline = extractionPipeline;
        this.alertDefinitions = alertDefinitions;

        dashboardNames = alertDefinitions.stream()
                .map(a -> a.getAttributes().get(AlertManager.DASHBOARD_NAME_KEY))
                .collect(Collectors.joining(", "));
    }

    @Override
    public String getUuid() {
        return queryId;
    }

    @Override
    public String getName() {
        return queryId + " from dashboards " + dashboardNames;
    }

    @Override
    public Map<String, String> getParams() {
        return paramMap;
    }

    @Override
    public ExpressionOperator getExpression() {
        return expression;
    }

    @Override
    public DocRef getExtractionPipeline() {
        return extractionPipeline;
    }

    @Override
    public List<AlertDefinition> getAlertDefinitions() {
        return alertDefinitions;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DashboardRuleConfig that = (DashboardRuleConfig) o;
        return queryId.equals(that.queryId) && extractionPipeline.equals(that.extractionPipeline);
    }

    @Override
    public int hashCode() {
        return Objects.hash(queryId, extractionPipeline);
    }
}
