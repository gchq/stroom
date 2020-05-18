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

package stroom.legacy.model_6_1;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@value #CLASS_DESC}
 */
@JsonPropertyOrder({"dataSource", "expression", "params"})
@XmlType(name = "Query", propOrder = {"dataSource", "expression", "params"})
@XmlRootElement(name = "query")
@XmlAccessorType(XmlAccessType.FIELD)
@ApiModel(description = Query.CLASS_DESC)
@Deprecated
public final class Query implements Serializable {

    private static final long serialVersionUID = 9055582579670841979L;

    public static final String CLASS_DESC = "The query terms for the search";

    @XmlElement
    @ApiModelProperty(
            required = true)
    private DocRef dataSource;

    @XmlElement
    @ApiModelProperty(
            value = "The root logical operator in the query expression tree",
            required = true)
    private ExpressionOperator expression;

    @XmlElementWrapper(name = "params")
    @XmlElement(name = "param")
    @ApiModelProperty(
            value = "A list of key/value pairs that provide additional information about the query",
            required = false)
    private List<Param> params;

    private Query() {
    }

    public Query(final DocRef dataSource, final ExpressionOperator expression) {
        this(dataSource, expression, null);
    }

    public Query(final DocRef dataSource, final ExpressionOperator expression, final List<Param> params) {
        this.dataSource = dataSource;
        this.expression = expression;
        this.params = params;
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

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Query query = (Query) o;

        if (dataSource != null ? !dataSource.equals(query.dataSource) : query.dataSource != null) return false;
        if (expression != null ? !expression.equals(query.expression) : query.expression != null) return false;
        return params != null ? params.equals(query.params) : query.params == null;
    }

    @Override
    public int hashCode() {
        int result = dataSource != null ? dataSource.hashCode() : 0;
        result = 31 * result + (expression != null ? expression.hashCode() : 0);
        result = 31 * result + (params != null ? params.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Query{" +
                "dataSource=" + dataSource +
                ", expression=" + expression +
                ", params=" + params +
                '}';
    }

    /**
     * Builder for constructing a {@link Query}
     */
    public static class Builder {

        private DocRef dataSource;

        private ExpressionOperator expression;

        private final List<Param> params = new ArrayList<>();

        /**
         * @param value A DocRef that points to the data source of the query
         *
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder dataSource(final DocRef value) {
            this.dataSource = value;
            return this;
        }

        /**
         * Shortcut function for creating the datasource {@link DocRef} in one go
         * @param type The type of the datasource
         * @param uuid The UUID of the datasource
         * @param name The name of the datasource
         * @return this builder, with the completed datasource added.
         */
        public Builder dataSource(final String type, final String uuid, final String name) {
            return this.dataSource(new DocRef.Builder().type(type).uuid(uuid).name(name).build());
        }

        /**
         * @param value he root logical addOperator in the query expression tree
         *
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder expression(final ExpressionOperator value) {
            this.expression = value;
            return this;
        }

        /**
         * Shortcut function to add a parameter and go straight back to building the query
         * @param key The parameter key
         * @param value The parameter value
         * @return this builder with the completed parameter added.
         */
        public Builder addParam(final String key, final String value) {
            return addParams(new Param.Builder().key(key).value(value).build());
        }

        /**
         * @param values A list of key/value pairs that provide additional information about the query
         *
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder addParams(final Param...values) {
            this.params.addAll(Arrays.asList(values));
            return this;
        }

        public Query build() {
            return new Query(dataSource, expression, params);
        }
    }
}