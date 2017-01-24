/*
 * Copyright 2016 Crown Copyright
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

package stroom.query.api;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

@JsonPropertyOrder({"dataSource", "expression", "params"})
@XmlType(name = "query", propOrder = {"dataSource", "expression", "params"})
public class Query implements Serializable {
    private static final long serialVersionUID = 9055582579670841979L;

    private DocRef dataSource;
    private ExpressionOperator expression;
    private Param[] params;

    public Query() {
    }

    public Query(final DocRef dataSource, final ExpressionOperator expression) {
        this(dataSource, expression, null);
    }

    public Query(final DocRef dataSource, final ExpressionOperator expression, Param[] params) {
        this.dataSource = dataSource;
        this.expression = expression;
        this.params = params;
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

    @XmlElementWrapper(name = "params")
    @XmlElement(name = "param")
    public Param[] getParams() {
        return params;
    }

    public void setParams(final Param[] params) {
        this.params = params;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Query query = (Query) o;

        if (dataSource != null ? !dataSource.equals(query.dataSource) : query.dataSource != null) return false;
        if (expression != null ? !expression.equals(query.expression) : query.expression != null) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        return Arrays.equals(params, query.params);
    }

    @Override
    public int hashCode() {
        int result = dataSource != null ? dataSource.hashCode() : 0;
        result = 31 * result + (expression != null ? expression.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(params);
        return result;
    }

    @Override
    public String toString() {
        return "Query{" +
                "dataSource=" + dataSource +
                ", expression=" + expression +
                ", params=" + Arrays.toString(params) +
                '}';
    }
}