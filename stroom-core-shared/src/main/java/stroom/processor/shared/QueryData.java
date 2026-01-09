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

package stroom.processor.shared;

import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.Param;
import stroom.query.api.TimeRange;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class QueryData {

    @JsonProperty
    private final DocRef dataSource;
    @JsonProperty
    private final ExpressionOperator expression;
    @JsonProperty
    private final List<Param> params;
    @JsonProperty
    private final TimeRange timeRange;
    @JsonProperty
    private final Limits limits;
    @JsonProperty
    private final FeedDependencies feedDependencies;

    @JsonCreator
    public QueryData(@JsonProperty("dataSource") final DocRef dataSource,
                     @JsonProperty("expression") final ExpressionOperator expression,
                     @JsonProperty("params") final List<Param> params,
                     @JsonProperty("timeRange") final TimeRange timeRange,
                     @JsonProperty("limits") final Limits limits,
                     @JsonProperty("feedDependencies") final FeedDependencies feedDependencies) {
        this.dataSource = dataSource;
        this.expression = expression;
        this.params = params;
        this.timeRange = timeRange;
        this.limits = limits;
        this.feedDependencies = feedDependencies;
    }

    public DocRef getDataSource() {
        return dataSource;
    }

    public ExpressionOperator getExpression() {
        return expression;
    }

    public List<Param> getParams() {
        return params;
    }

    public TimeRange getTimeRange() {
        return timeRange;
    }

    public Limits getLimits() {
        return limits;
    }

    public FeedDependencies getFeedDependencies() {
        return feedDependencies;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final QueryData queryData = (QueryData) o;
        return Objects.equals(dataSource, queryData.dataSource) &&
               Objects.equals(expression, queryData.expression) &&
               Objects.equals(params, queryData.params) &&
               Objects.equals(timeRange, queryData.timeRange) &&
               Objects.equals(limits, queryData.limits) &&
               Objects.equals(feedDependencies, queryData.feedDependencies);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataSource, expression, params, timeRange, limits, feedDependencies);
    }

    @Override
    public String toString() {
        return "QueryData{" +
               "dataSource=" + dataSource +
               ", expression=" + expression +
               ", params=" + params +
               ", timeRange=" + timeRange +
               ", limits=" + limits +
               ", feedDependencies=" + feedDependencies +
               '}';
    }

    public static final class Builder {

        private DocRef dataSource;
        private ExpressionOperator expression;
        private List<Param> params;
        private TimeRange timeRange;
        private Limits limits;
        private FeedDependencies feedDependencies;

        private Builder() {
        }

        private Builder(final QueryData queryData) {
            this.dataSource = queryData.dataSource;
            this.expression = queryData.expression;
            this.params = queryData.params;
            this.timeRange = queryData.timeRange;
            this.limits = queryData.limits;
            this.feedDependencies = queryData.feedDependencies;
        }

        public Builder dataSource(final DocRef dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        public Builder limits(final Limits limits) {
            this.limits = limits;
            return this;
        }

        public Builder expression(final ExpressionOperator expression) {
            this.expression = expression;
            return this;
        }

        public Builder params(final List<Param> params) {
            this.params = params;
            return this;
        }

        public Builder timeRange(final TimeRange timeRange) {
            this.timeRange = timeRange;
            return this;
        }

        public Builder feedDependencies(final FeedDependencies feedDependencies) {
            this.feedDependencies = feedDependencies;
            return this;
        }

        public QueryData build() {
            return new QueryData(dataSource, expression, params, timeRange, limits, feedDependencies);
        }
    }
}
