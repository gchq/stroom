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

package stroom.dashboard.shared;

import stroom.query.api.QueryKey;
import stroom.query.api.Result;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.TokenError;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@JsonPropertyOrder({
        "queryKey",
        "highlights",
        "errors",
        "tokenError",
        "complete",
        "results",
        "errorMessages"})
@JsonInclude(Include.NON_NULL)
public class DashboardSearchResponse {

    /**
     * The node that this search response is for.
     */
    @JsonProperty
    private final String node;

    /**
     * The dashboard component that this search response is for.
     */
    @JsonProperty
    private final QueryKey queryKey;

    /**
     * A set of strings to highlight in the UI that should correlate with the
     * search query.
     */
    @JsonProperty
    private final Set<String> highlights;

    /**
     * @deprecated Use {@link DashboardSearchResponse#errorMessages} instead.
     */
    @Deprecated
    @JsonProperty
    private final List<String> errors;

    /**
     * Any errors that have been generated during searching.
     */
    @JsonProperty
    private final List<ErrorMessage> errorMessages;

    @JsonProperty
    private final TokenError tokenError;

    /**
     * Complete means that all index shards have been searched across the
     * cluster and there are no more results to come.
     **/
    @JsonProperty
    private final boolean complete;

    @JsonProperty
    private final List<Result> results;

    @JsonCreator
    public DashboardSearchResponse(@JsonProperty("node") final String node,
                                   @JsonProperty("queryKey") final QueryKey queryKey,
                                   @JsonProperty("highlights") final Set<String> highlights,
                                   @JsonProperty("errors") final List<String> errors,
                                   @JsonProperty("tokenError") final TokenError tokenError,
                                   @JsonProperty("complete") final boolean complete,
                                   @JsonProperty("results") final List<Result> results,
                                   @JsonProperty("errorMessages") final List<ErrorMessage> errorMessages) {
        this.node = node;
        this.queryKey = queryKey;
        this.highlights = highlights;
        this.errors = errors;
        this.tokenError = tokenError;
        this.complete = complete;
        this.results = results;
        this.errorMessages = errorMessages;
    }

    public String getNode() {
        return node;
    }

    public QueryKey getQueryKey() {
        return queryKey;
    }

    public Set<String> getHighlights() {
        return highlights;
    }

    public List<String> getErrors() {
        return errors == null
                ? Collections.emptyList()
                : errors;
    }

    public List<ErrorMessage> getErrorMessages() {
        return errorMessages == null
                ? Collections.emptyList()
                : errorMessages;
    }

    public TokenError getTokenError() {
        return tokenError;
    }

    public boolean isComplete() {
        return complete;
    }

    public List<Result> getResults() {
        return results;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DashboardSearchResponse that = (DashboardSearchResponse) o;
        return complete == that.complete &&
               Objects.equals(queryKey, that.queryKey) &&
               Objects.equals(highlights, that.highlights) &&
               Objects.equals(errors, that.errors) &&
               Objects.equals(tokenError, that.tokenError) &&
               Objects.equals(results, that.results) &&
               Objects.equals(errorMessages, that.errorMessages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(queryKey, highlights, errors, tokenError, complete, results, errorMessages);
    }

    @Override
    public String toString() {
        return "DashboardSearchResponse{" +
               "queryKey=" + queryKey +
               ", highlights=" + highlights +
               ", tokenError=" + tokenError +
               ", complete=" + complete +
               ", results=" + results +
               ", errorMessages=" + errorMessages +
               '}';
    }
}
