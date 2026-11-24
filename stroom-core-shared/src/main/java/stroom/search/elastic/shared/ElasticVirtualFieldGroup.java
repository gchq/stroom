/*
 * Copyright 2026 Crown Copyright
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

package stroom.search.elastic.shared;

import stroom.query.api.ExpressionOperator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/// A named group of a virtual field. Searching the virtual field for the group's name runs the group's
/// expression, which targets concrete `keyword` fields of the index.
@JsonPropertyOrder({"name", "expression"})
@JsonInclude(Include.NON_NULL)
public final class ElasticVirtualFieldGroup {

    @JsonProperty
    private final String name;
    @JsonProperty
    private final ExpressionOperator expression;

    /// @param name       the value users search the virtual field for, e.g. `web`
    /// @param expression the expression the group stands for
    @JsonCreator
    public ElasticVirtualFieldGroup(@JsonProperty("name") final String name,
                                    @JsonProperty("expression") final ExpressionOperator expression) {
        this.name = name;
        this.expression = expression;
    }

    /// @return the value users search the virtual field for
    public String getName() {
        return name;
    }

    /// @return the expression the group stands for
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
        final ElasticVirtualFieldGroup that = (ElasticVirtualFieldGroup) o;
        return Objects.equals(name, that.name) && Objects.equals(expression, that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, expression);
    }

    @Override
    public String toString() {
        return "ElasticVirtualFieldGroup{" +
               "name='" + name + '\'' +
               ", expression=" + expression +
               '}';
    }
}
