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

package stroom.query.api.datasource;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// The field-level query options a field supports. Each data source decides which options it offers,
/// defines its own sets and gives each field the set that applies to it with
/// {@link QueryField#getQueryOptionSet()}. The set travels with the field, so the options dialog is built
/// from it without the UI knowing anything about the data source. The data source's search code then
/// applies the options stored in each expression term.
///
/// Definitions are listed in the order they are shown and resolved in. A rule should refer to an
/// option listed before it where possible; see {@link QueryOptionUtil#resolve(QueryOptionSet, java.util.Map)}.
@JsonPropertyOrder({"definitions"})
@JsonInclude(Include.NON_NULL)
public final class QueryOptionSet {

    @JsonProperty
    private final List<QueryOptionDefinition> definitions;

    /// Creates a set. Use {@link #of(QueryOptionDefinition...)} to create one in code.
    ///
    /// @param definitions The option definitions, in the order they are shown and resolved in.
    /// @throws IllegalArgumentException If two definitions have the same id, or a rule refers to an option
    ///                                  that is not in the set.
    @JsonCreator
    public QueryOptionSet(@JsonProperty("definitions") final List<QueryOptionDefinition> definitions) {
        this.definitions = definitions == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(definitions));
        validate(this.definitions);
    }

    /// @param definitions The option definitions, in the order they are shown and resolved in.
    /// @return A new set.
    /// @throws IllegalArgumentException If two definitions have the same id, or a rule refers to an option
    ///                                  that is not in the set.
    public static QueryOptionSet of(final QueryOptionDefinition... definitions) {
        return new QueryOptionSet(Arrays.asList(definitions));
    }

    /// @return The option definitions, in the order they are shown and resolved in.
    public List<QueryOptionDefinition> getDefinitions() {
        return definitions;
    }

    /// @param id The option id.
    /// @return The definition with the given id, or null if the set has no such option.
    public QueryOptionDefinition getDefinition(final String id) {
        for (final QueryOptionDefinition definition : definitions) {
            if (definition.getId().equals(id)) {
                return definition;
            }
        }
        return null;
    }

    private static void validate(final List<QueryOptionDefinition> definitions) {
        final Set<String> ids = new HashSet<>();
        for (final QueryOptionDefinition definition : definitions) {
            if (!ids.add(definition.getId())) {
                throw new IllegalArgumentException("Duplicate query option: " + definition.getId());
            }
        }
        for (final QueryOptionDefinition definition : definitions) {
            for (final QueryOptionRule rule : definition.getRules()) {
                if (!ids.contains(rule.getOptionId())) {
                    throw new IllegalArgumentException("Query option " + definition.getId() +
                                                       " has a rule for an option not in the set: " +
                                                       rule.getOptionId());
                }
            }
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final QueryOptionSet that = (QueryOptionSet) o;
        return Objects.equals(definitions, that.definitions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(definitions);
    }

    @Override
    public String toString() {
        return "QueryOptionSet{" +
               "definitions=" + definitions +
               '}';
    }
}
