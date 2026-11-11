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

package stroom.ai.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/// A table of results shown in a tab (a query's results, or a table on a dashboard), as the search requests a tool
/// needs to take a snapshot of its rows when the model asks for them. The rows themselves are never sent with a
/// question.
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiTabSearch {

    @JsonProperty
    private final String name;
    @JsonProperty
    private final String componentId;
    @JsonProperty
    private final AskStroomAiContext results;
    @JsonProperty
    private final AskStroomAiContext live;
    @JsonProperty
    private final List<String> columns;
    @JsonProperty
    private final Long visibleRows;
    @JsonProperty
    private final boolean searching;

    /// @param name        The table's name, as the user sees it, e.g. "Logons by user".
    /// @param componentId The table's component id on a dashboard, or null for a query's results.
    /// @param results     The search request that produced the rows shown (with its query key), or null if the
    ///                    search hasn't been run.
    /// @param live        The search request as the tab now stands (its query, expression, parameters and time
    ///                    range as edited, with no query key), to run the search again, or null if not known.
    /// @param columns     The names of the table's columns, as shown.
    /// @param visibleRows How many rows the table has, as shown, or null if not known.
    /// @param searching   True while the search is still running.
    @JsonCreator
    public AiTabSearch(@JsonProperty("name") final String name,
                       @JsonProperty("componentId") final String componentId,
                       @JsonProperty("results") final AskStroomAiContext results,
                       @JsonProperty("live") final AskStroomAiContext live,
                       @JsonProperty("columns") final List<String> columns,
                       @JsonProperty("visibleRows") final Long visibleRows,
                       @JsonProperty("searching") final Boolean searching) {
        this.name = name;
        this.componentId = componentId;
        this.results = results;
        this.live = live;
        this.columns = columns == null
                ? Collections.emptyList()
                : new ArrayList<>(columns);
        this.visibleRows = visibleRows;
        this.searching = searching != null && searching;
    }

    /// @return The table's name, as the user sees it.
    public String getName() {
        return name;
    }

    /// @return The table's component id on a dashboard, or null for a query's results.
    public String getComponentId() {
        return componentId;
    }

    /// @return The search request that produced the rows shown, or null if the search hasn't been run.
    public AskStroomAiContext getResults() {
        return results;
    }

    /// @return The search request as the tab now stands, or null if not known.
    public AskStroomAiContext getLive() {
        return live;
    }

    /// @return The names of the table's columns, as shown.
    public List<String> getColumns() {
        return columns;
    }

    /// @return How many rows the table has, as shown, or null if not known.
    public Long getVisibleRows() {
        return visibleRows;
    }

    /// @return True while the search is still running.
    public boolean isSearching() {
        return searching;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AiTabSearch that = (AiTabSearch) o;
        return searching == that.searching
               && Objects.equals(name, that.name)
               && Objects.equals(componentId, that.componentId)
               && Objects.equals(results, that.results)
               && Objects.equals(live, that.live)
               && Objects.equals(columns, that.columns)
               && Objects.equals(visibleRows, that.visibleRows);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, componentId, results, live, columns, visibleRows, searching);
    }

    @Override
    public String toString() {
        return "AiTabSearch{name='" + name + "', componentId='" + componentId + "', visibleRows=" + visibleRows
               + ", searching=" + searching + "}";
    }
}
