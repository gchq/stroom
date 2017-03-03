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

package stroom.query.api;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.List;

@JsonPropertyOrder({"dataSource", "expression", "params"})
@XmlType(name = "Query", propOrder = {"dataSource", "expression", "params"})
@XmlRootElement(name = "query")
@XmlAccessorType(XmlAccessType.FIELD)
public final class Query implements Serializable {
    private static final long serialVersionUID = 9055582579670841979L;

    @XmlElement
    private DocRef dataSource;
    @XmlElement
    private ExpressionOperator expression;
    @XmlElementWrapper(name = "params")
    @XmlElement(name = "param")
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
}