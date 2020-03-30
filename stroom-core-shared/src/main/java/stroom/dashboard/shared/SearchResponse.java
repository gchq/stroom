/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.util.shared.ToStringBuilder;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

@JsonPropertyOrder({"queryKey", "highlights", "errors", "complete", "results"})
@JsonInclude(Include.NON_NULL)
public class SearchResponse {
    /**
     * The dashboard component that this search response is for.
     */
    @JsonProperty
    private final DashboardQueryKey queryKey;

    /**
     * A set of strings to highlight in the UI that should correlate with the
     * search query.
     */
    @JsonProperty
    private final Set<String> highlights;

    /**
     * Any errors that have been generated during searching.
     */
    @JsonProperty
    private final String errors;

    /**
     * Complete means that all index shards have been searched across the
     * cluster and there are no more results to come.
     **/
    @JsonProperty
    private final boolean complete;

    @JsonProperty
    private final Map<String, ComponentResult> results;

    @JsonCreator
    public SearchResponse(@JsonProperty("queryKey") final DashboardQueryKey queryKey,
                          @JsonProperty("highlights") final Set<String> highlights,
                          @JsonProperty("errors") final String errors,
                          @JsonProperty("complete") final boolean complete,
                          @JsonProperty("results") final Map<String, ComponentResult> results) {
        this.queryKey = queryKey;
        this.highlights = highlights;
        this.errors = errors;
        this.complete = complete;
        this.results = results;
    }

    public DashboardQueryKey getQueryKey() {
        return queryKey;
    }

    public Set<String> getHighlights() {
        return highlights;
    }

    public String getErrors() {
        return errors;
    }

    public boolean isComplete() {
        return complete;
    }

    public Map<String, ComponentResult> getResults() {
        return results;
    }

    @Override
    public String toString() {
        final ToStringBuilder builder = new ToStringBuilder();
        builder.append("highlights", highlights);
        builder.append("errors", errors);
        builder.append("complete", complete);
        return builder.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SearchResponse that = (SearchResponse) o;
        return complete == that.complete &&
                Objects.equals(queryKey, that.queryKey) &&
                Objects.equals(highlights, that.highlights) &&
                Objects.equals(errors, that.errors) &&
                Objects.equals(results, that.results);
    }

    @Override
    public int hashCode() {
        return Objects.hash(queryKey, highlights, errors, complete, results);
    }
}
