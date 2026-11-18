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

package stroom.query.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Objects;

/// One join line of a StroomQL query, such as `left join "HR Users" as hr on UserId = hr.Username`.
///
/// The joined source is searched on its own, as its own [Query], before the rows it is joined to.
/// When the join line holds a bracketed query, its eval, grouping and select are in
/// [#getTableSettings()]; for a plain source that is `null`, and the joined source's rows are used as
/// they are. The joined source's fields are named `<alias>.<field>` in the rest of the query.
@JsonPropertyOrder({"type", "alias", "query", "tableSettings", "keys", "window"})
@JsonInclude(Include.NON_NULL)
@Schema(description = "A source joined to the rows of a query")
public final class QueryJoin {

    @Schema(description = "The kind of join")
    @JsonProperty
    private final JoinType type;

    @Schema(description = "The name the joined source's fields are prefixed with, as in <alias>.<field>")
    @JsonProperty
    private final String alias;

    @Schema(description = "The search of the joined source")
    @JsonProperty
    private final Query query;

    @Schema(description = "The eval, grouping and select of a bracketed joined query, or null to use the " +
                          "joined source's rows as they are")
    @JsonProperty
    private final TableSettings tableSettings;

    @Schema(description = "The pairs of expressions whose values must be equal for rows to join")
    @JsonProperty
    private final List<JoinKey> keys;

    @Schema(description = "The time window a joined row's time must fall in, for an event-to-event join")
    @JsonProperty
    private final JoinWindow window;

    /// Creates a join.
    ///
    /// @param type          The kind of join.
    /// @param alias         The name the joined source's fields are prefixed with.
    /// @param query         The search of the joined source.
    /// @param tableSettings The eval, grouping and select of a bracketed joined query, or `null`.
    /// @param keys          The pairs of expressions whose values must be equal for rows to join.
    /// @param window        The time window of an event-to-event join, or `null`.
    @JsonCreator
    public QueryJoin(@JsonProperty("type") final JoinType type,
                     @JsonProperty("alias") final String alias,
                     @JsonProperty("query") final Query query,
                     @JsonProperty("tableSettings") final TableSettings tableSettings,
                     @JsonProperty("keys") final List<JoinKey> keys,
                     @JsonProperty("window") final JoinWindow window) {
        this.type = type;
        this.alias = alias;
        this.query = query;
        this.tableSettings = tableSettings;
        this.keys = keys;
        this.window = window;
    }

    /// Gets the kind of join.
    ///
    /// @return The kind of join.
    public JoinType getType() {
        return type;
    }

    /// Gets the name the joined source's fields are prefixed with.
    ///
    /// @return The alias, e.g. `hr` for fields such as `hr.Department`.
    public String getAlias() {
        return alias;
    }

    /// Gets the search of the joined source.
    ///
    /// @return The joined source's query.
    public Query getQuery() {
        return query;
    }

    /// Gets the eval, grouping and select of a bracketed joined query.
    ///
    /// @return The table settings, or `null` if the joined source's rows are used as they are.
    public TableSettings getTableSettings() {
        return tableSettings;
    }

    /// Gets the pairs of expressions whose values must be equal for rows to join.
    ///
    /// @return The keys, never empty for a parsed query.
    public List<JoinKey> getKeys() {
        return keys;
    }

    /// Gets the time window of an event-to-event join.
    ///
    /// @return The window, or `null` if the join has none.
    public JoinWindow getWindow() {
        return window;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final QueryJoin that = (QueryJoin) o;
        return type == that.type &&
               Objects.equals(alias, that.alias) &&
               Objects.equals(query, that.query) &&
               Objects.equals(tableSettings, that.tableSettings) &&
               Objects.equals(keys, that.keys) &&
               Objects.equals(window, that.window);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, alias, query, tableSettings, keys, window);
    }

    @Override
    public String toString() {
        return "QueryJoin{" +
               "type=" + type +
               ", alias='" + alias + '\'' +
               ", query=" + query +
               ", tableSettings=" + tableSettings +
               ", keys=" + keys +
               ", window=" + window +
               '}';
    }
}
