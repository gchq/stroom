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

package stroom.query.shared;

import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.Param;
import stroom.query.api.TimeRange;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class QueryContext {

    @JsonProperty
    private final List<Param> params;
    @JsonProperty
    private final TimeRange timeRange;
    @JsonProperty
    private final String queryInfo;
    @JsonProperty
    private final DateTimeSettings dateTimeSettings;
    @JsonProperty
    private final ExpressionOperator additionalQueryExpression;

    @SuppressWarnings("checkstyle:LineLength")
    @JsonCreator
    public QueryContext(@JsonProperty("params") final List<Param> params,
                        @JsonProperty("timeRange") final TimeRange timeRange,
                        @JsonProperty("queryInfo") final String queryInfo,
                        @JsonProperty("dateTimeSettings") final DateTimeSettings dateTimeSettings,
                        @JsonProperty("additionalQueryExpression") final ExpressionOperator additionalQueryExpression) {
        this.params = params;
        this.timeRange = timeRange;
        this.queryInfo = queryInfo;
        this.dateTimeSettings = dateTimeSettings;
        this.additionalQueryExpression = additionalQueryExpression;
    }

    public List<Param> getParams() {
        return params;
    }

    public TimeRange getTimeRange() {
        return timeRange;
    }

    public String getQueryInfo() {
        return queryInfo;
    }

    public DateTimeSettings getDateTimeSettings() {
        return dateTimeSettings;
    }

    public ExpressionOperator getAdditionalQueryExpression() {
        return additionalQueryExpression;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final QueryContext that = (QueryContext) o;
        return Objects.equals(params, that.params) &&
                Objects.equals(timeRange, that.timeRange) &&
                Objects.equals(queryInfo, that.queryInfo) &&
                Objects.equals(dateTimeSettings, that.dateTimeSettings) &&
                Objects.equals(additionalQueryExpression, that.additionalQueryExpression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(params, timeRange, queryInfo, dateTimeSettings, additionalQueryExpression);
    }

    @Override
    public String toString() {
        return "QueryContext{" +
                "params=" + params +
                ", timeRange=" + timeRange +
                ", queryInfo='" + queryInfo + '\'' +
                ", dateTimeSettings=" + dateTimeSettings +
                ", currentSelectionExpression=" + additionalQueryExpression +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private List<Param> params;
        private TimeRange timeRange;
        private String queryInfo;
        private DateTimeSettings dateTimeSettings;
        private ExpressionOperator additionalQueryExpression;

        private Builder() {
        }

        private Builder(final QueryContext queryContext) {
            this.params = queryContext.params;
            this.timeRange = queryContext.timeRange;
            this.queryInfo = queryContext.queryInfo;
            this.dateTimeSettings = queryContext.dateTimeSettings;
            this.additionalQueryExpression = queryContext.additionalQueryExpression;
        }

        public Builder params(final List<Param> params) {
            this.params = params;
            return this;
        }

        public Builder timeRange(final TimeRange timeRange) {
            this.timeRange = timeRange;
            return this;
        }

        public Builder queryInfo(final String queryInfo) {
            this.queryInfo = queryInfo;
            return this;
        }

        public Builder dateTimeSettings(final DateTimeSettings dateTimeSettings) {
            this.dateTimeSettings = dateTimeSettings;
            return this;
        }

        public Builder additionalQueryExpression(final ExpressionOperator additionalQueryExpression) {
            this.additionalQueryExpression = additionalQueryExpression;
            return this;
        }

        public QueryContext build() {
            return new QueryContext(
                    params,
                    timeRange,
                    queryInfo,
                    dateTimeSettings,
                    additionalQueryExpression);
        }
    }
}
