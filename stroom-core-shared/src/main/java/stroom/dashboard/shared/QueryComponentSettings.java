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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"dataSource", "expression", "automate"})
@JsonInclude(Include.NON_NULL)
public class QueryComponentSettings implements ComponentSettings {

    @JsonProperty("dataSource")
    private final DocRef dataSource;
    @JsonProperty("expression")
    private final ExpressionOperator expression;
    @JsonProperty("automate")
    private final Automate automate;

    @JsonCreator
    public QueryComponentSettings(@JsonProperty("dataSource") final DocRef dataSource,
                                  @JsonProperty("expression") final ExpressionOperator expression,
                                  @JsonProperty("automate") final Automate automate) {
        this.dataSource = dataSource;
        this.expression = expression;
        this.automate = automate;
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

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final QueryComponentSettings that = (QueryComponentSettings) o;
        return Objects.equals(dataSource, that.dataSource) &&
                Objects.equals(expression, that.expression) &&
                Objects.equals(automate, that.automate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataSource, expression, automate);
    }

    @Override
    public String toString() {
        return "QueryComponentSettings{" +
                "dataSource=" + dataSource +
                ", expression=" + expression +
                ", automate=" + automate +
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

        private Builder() {
        }

        private Builder(final QueryComponentSettings queryComponentSettings) {
            this.dataSource = queryComponentSettings.dataSource;
            this.expression = queryComponentSettings.expression;
            this.automate = queryComponentSettings.automate;
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

        public QueryComponentSettings build() {
            return new QueryComponentSettings(dataSource, expression, automate);
        }
    }
}
