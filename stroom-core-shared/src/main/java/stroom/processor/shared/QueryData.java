/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.processor.shared;

import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.Param;
import stroom.query.api.v2.TimeRange;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlType(name = "query", propOrder = {"dataSource", "expression", "params", "timeRange", "limits"})
@XmlRootElement(name = "query")
@JsonInclude(Include.NON_NULL)
public class QueryData implements Serializable {

    @JsonProperty
    private DocRef dataSource;
    @JsonProperty
    private ExpressionOperator expression;
    @JsonProperty
    private List<Param> params;
    @JsonProperty
    private TimeRange timeRange;
    @JsonProperty
    private Limits limits;

    public QueryData() {
    }

    @JsonCreator
    public QueryData(@JsonProperty("dataSource") final DocRef dataSource,
                     @JsonProperty("expression") final ExpressionOperator expression,
                     @JsonProperty("params") final List<Param> params,
                     @JsonProperty("timeRange") final TimeRange timeRange,
                     @JsonProperty("limits") final Limits limits) {
        this.dataSource = dataSource;
        this.expression = expression;
        this.params = params;
        this.timeRange = timeRange;
        this.limits = limits;
    }

    @XmlElement
    public DocRef getDataSource() {
        return dataSource;
    }

    public void setDataSource(final DocRef dataSource) {
        this.dataSource = dataSource;
    }

    @XmlElement
    public ExpressionOperator getExpression() {
        return expression;
    }

    public void setExpression(final ExpressionOperator expression) {
        this.expression = expression;
    }

    @XmlElement
    public List<Param> getParams() {
        return params;
    }

    public void setParams(final List<Param> params) {
        this.params = params;
    }

    public TimeRange getTimeRange() {
        return timeRange;
    }

    public void setTimeRange(final TimeRange timeRange) {
        this.timeRange = timeRange;
    }

    @XmlElement
    public Limits getLimits() {
        return limits;
    }

    public void setLimits(final Limits limits) {
        this.limits = limits;
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
        private List<Param> params;
        private TimeRange timeRange;
        private Limits limits;

        private Builder() {
        }

        private Builder(final QueryData queryData) {
            this.dataSource = queryData.dataSource;
            this.expression = queryData.expression;
            this.params = queryData.params;
            this.timeRange = queryData.timeRange;
            this.limits = queryData.limits;
        }

        public Builder dataSource(final DocRef value) {
            this.dataSource = value;
            return this;
        }

        public Builder limits(final Limits value) {
            this.limits = value;
            return this;
        }

        public Builder expression(final ExpressionOperator value) {
            this.expression = value;
            return this;
        }

        public Builder params(final List<Param> value) {
            this.params = value;
            return this;
        }

        public Builder timeRange(final TimeRange timeRange) {
            this.timeRange = timeRange;
            return this;
        }

        public QueryData build() {
            return new QueryData(dataSource, expression, params, timeRange, limits);
        }
    }
}
