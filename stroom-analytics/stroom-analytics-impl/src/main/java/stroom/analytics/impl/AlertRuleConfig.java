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

package stroom.analytics.impl;

import stroom.analytics.api.AlertDefinition;
import stroom.analytics.api.AlertManager;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.TableSettings;
import stroom.query.shared.QueryContext;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.Map;

class AlertRuleConfig implements RuleConfig {
    private final AnalyticRuleDoc analyticRuleDoc;
    private final QueryContext queryContext;
    private final ExpressionOperator expression;
    private final DocRef extractionPipeline;
    private  final TableSettings tableSettings;
    private final SearchRequest searchRequest;

    AlertRuleConfig(final AnalyticRuleDoc analyticRuleDoc,
                    final QueryContext queryContext,
                    final ExpressionOperator expression,
                    final DocRef extractionPipeline,
                    final TableSettings tableSettings,
                    final SearchRequest searchRequest) {
        this.analyticRuleDoc = analyticRuleDoc;
        this.queryContext = queryContext;
        this.expression = expression;
        this.extractionPipeline = extractionPipeline;
        this.tableSettings = tableSettings;
        this.searchRequest = searchRequest;
    }

    @Override
    public String getUuid() {
        return analyticRuleDoc.getUuid();
    }

    @Override
    public String getName() {
        return analyticRuleDoc.getName();
    }

    @Override
    public Map<String, String> getParams() {
        return Map.of();
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
        return List.of(new AlertDefinition(tableSettings,
                Map.of(AlertManager.DASHBOARD_NAME_KEY, getName(),
                        AlertManager.RULES_FOLDER_KEY, getUuid(),
                        AlertManager.TABLE_NAME_KEY, getName())));
    }

    public AnalyticRuleDoc getAlertRuleDoc() {
        return analyticRuleDoc;
    }

    public SearchRequest getSearchRequest() {
        return searchRequest;
    }

    @JsonIgnore
    public QueryKey getQueryKey() {
        return analyticRuleDoc.getQueryKey();
    }
}
