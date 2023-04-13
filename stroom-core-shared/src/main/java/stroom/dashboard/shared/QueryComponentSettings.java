/*
 * Copyright 2017 Crown Copyright
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
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.QueryKey;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({
        "dataSource",
        "expression",
        "automate",
        "selectionHandlers"
})
@JsonInclude(Include.NON_NULL)
public class QueryComponentSettings implements ComponentSettings {

    @JsonProperty("dataSource")
    private final DocRef dataSource;
    @JsonProperty("expression")
    private final ExpressionOperator expression;
    @JsonProperty("automate")
    private final Automate automate;
    @JsonProperty("selectionHandlers")
    private final List<ComponentSelectionHandler> selectionHandlers;
    @JsonProperty("lastQueryKey")
    private final QueryKey lastQueryKey;
    @JsonProperty("lastQueryNode")
    private final String lastQueryNode;


    @SuppressWarnings("checkstyle:LineLength")
    @JsonCreator
    public QueryComponentSettings(@JsonProperty("dataSource") final DocRef dataSource,
                                  @JsonProperty("expression") final ExpressionOperator expression,
                                  @JsonProperty("automate") final Automate automate,
                                  @JsonProperty("selectionHandlers") final List<ComponentSelectionHandler> selectionHandlers,
                                  @JsonProperty("lastQueryKey") final QueryKey lastQueryKey,
                                  @JsonProperty("lastQueryNode") final String lastQueryNode) {
        this.dataSource = dataSource;
        this.expression = expression;
        this.automate = automate;
        this.selectionHandlers = selectionHandlers;
        this.lastQueryKey = lastQueryKey;
        this.lastQueryNode = lastQueryNode;
    }

    public DocRef getDataSource() {
        return dataSource;
    }

    public ExpressionOperator getExpression() {
        return expression;
    }

    public Automate getAutomate() {
        return automate;
    }

    public List<ComponentSelectionHandler> getSelectionHandlers() {
        return selectionHandlers;
    }

    public QueryKey getLastQueryKey() {
        return lastQueryKey;
    }

    public String getLastQueryNode() {
        return lastQueryNode;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final QueryComponentSettings that = (QueryComponentSettings) o;
        return Objects.equals(dataSource, that.dataSource) && Objects.equals(expression,
                that.expression) && Objects.equals(automate, that.automate) && Objects.equals(
                selectionHandlers,
                that.selectionHandlers) && Objects.equals(lastQueryKey,
                that.lastQueryKey) && Objects.equals(lastQueryNode, that.lastQueryNode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataSource, expression, automate, selectionHandlers, lastQueryKey, lastQueryNode);
    }

    @Override
    public String toString() {
        return "QueryComponentSettings{" +
                "dataSource=" + dataSource +
                ", expression=" + expression +
                ", automate=" + automate +
                ", selectionHandlers=" + selectionHandlers +
                ", lastQueryKey=" + lastQueryKey +
                ", lastQueryNode='" + lastQueryNode + '\'' +
                '}';
    }


    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private DocRef dataSource;
        private ExpressionOperator expression;
        private Automate automate;
        private List<ComponentSelectionHandler> selectionHandlers;
        private QueryKey lastQueryKey;
        private String lastQueryNode;

        private Builder() {
        }

        private Builder(final QueryComponentSettings queryComponentSettings) {
            this.dataSource = queryComponentSettings.dataSource;
            this.expression = queryComponentSettings.expression;
            this.automate = queryComponentSettings.automate;
            if (queryComponentSettings.selectionHandlers != null) {
                this.selectionHandlers = new ArrayList<>(queryComponentSettings.selectionHandlers);
            }
            this.lastQueryKey = queryComponentSettings.lastQueryKey;
            this.lastQueryNode = queryComponentSettings.lastQueryNode;
        }

        public Builder dataSource(final DocRef dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        public Builder expression(final ExpressionOperator expression) {
            this.expression = expression;
            return this;
        }

        public Builder automate(final Automate automate) {
            this.automate = automate;
            return this;
        }

        public Builder selectionHandlers(final List<ComponentSelectionHandler> selectionHandlers) {
            this.selectionHandlers = selectionHandlers;
            return this;
        }

        public Builder addSelectionHandler(final ComponentSelectionHandler selectionHandler) {
            if (this.selectionHandlers == null) {
                this.selectionHandlers = new ArrayList<>();
            }
            this.selectionHandlers.add(selectionHandler);
            return this;
        }

        public Builder lastQueryKey(final QueryKey lastQueryKey) {
            this.lastQueryKey = lastQueryKey;
            return this;
        }

        public Builder lastQueryNode(final String lastQueryNode) {
            this.lastQueryNode = lastQueryNode;
            return this;
        }

        public QueryComponentSettings build() {
            return new QueryComponentSettings(
                    dataSource,
                    expression,
                    automate,
                    selectionHandlers,
                    lastQueryKey,
                    lastQueryNode);
        }
    }
}
