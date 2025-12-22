/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.dashboard.shared;

import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.QueryKey;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({
        "dataSource",
        "expression",
        "automate",
        "selectionHandlers",
        "lastQueryKey",
        "lastQueryNode"
})
@JsonInclude(Include.NON_NULL)
public final class QueryComponentSettings extends AbstractQueryComponentSettings implements ComponentSettings {

    @JsonProperty("dataSource")
    private final DocRef dataSource;
    @JsonProperty("expression")
    private final ExpressionOperator expression;

    @SuppressWarnings("checkstyle:LineLength")
    @JsonCreator
    public QueryComponentSettings(@JsonProperty("dataSource") final DocRef dataSource,
                                  @JsonProperty("expression") final ExpressionOperator expression,
                                  @JsonProperty("automate") final Automate automate,
                                  @JsonProperty("selectionHandlers") final List<ComponentSelectionHandler> selectionHandlers,
                                  @JsonProperty("lastQueryKey") final QueryKey lastQueryKey,
                                  @JsonProperty("lastQueryNode") final String lastQueryNode) {
        super(automate, selectionHandlers, lastQueryKey, lastQueryNode);
        this.dataSource = dataSource;
        this.expression = expression;
    }

    public DocRef getDataSource() {
        return dataSource;
    }

    public ExpressionOperator getExpression() {
        return expression;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final QueryComponentSettings that = (QueryComponentSettings) o;
        return Objects.equals(dataSource, that.dataSource) && Objects.equals(expression,
                that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), dataSource, expression);
    }

    @Override
    public String toString() {
        return "QueryComponentSettings{" +
               "dataSource=" + dataSource +
               ", expression=" + expression +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder
            extends AbstractQueryComponentSettings
            .AbstractBuilder<QueryComponentSettings, QueryComponentSettings.Builder>
            implements
            HasSelectionQueryBuilder<QueryComponentSettings, Builder> {

        private DocRef dataSource;
        private ExpressionOperator expression;

        private Builder() {
        }

        private Builder(final QueryComponentSettings queryComponentSettings) {
            super(queryComponentSettings);
            this.dataSource = queryComponentSettings.dataSource;
            this.expression = queryComponentSettings.expression == null
                    ? null
                    : queryComponentSettings.expression.copy().build();
        }

        public Builder dataSource(final DocRef dataSource) {
            this.dataSource = dataSource;
            return self();
        }

        public Builder expression(final ExpressionOperator expression) {
            this.expression = expression;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public QueryComponentSettings build() {
            return new QueryComponentSettings(
                    dataSource,
                    expression,
                    automate,
                    selectionQuery,
                    lastQueryKey,
                    lastQueryNode);
        }
    }
}
