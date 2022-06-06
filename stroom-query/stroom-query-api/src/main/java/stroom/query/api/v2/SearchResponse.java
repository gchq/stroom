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

package stroom.query.api.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Object describing the response to a {@link SearchRequest searchRequest} which may or may not contains results
 */
@JsonPropertyOrder({"queryKey", "highlights", "results", "errors", "complete"})
@JsonInclude(Include.NON_NULL)
@Schema(description = "The response to a search request, that may or may not contain results. The results " +
        "may only be a partial set if an iterative screech was requested")
public final class SearchResponse {

    public static final String TIMEOUT_MESSAGE = "The search timed out after ";

    @Schema(description = "The key of the query associated with this response.", required = true)
    @JsonProperty
    private final QueryKey queryKey;

    @Schema(description = "A list of strings to highlight in the UI that should correlate with the search query.",
            required = true)
    @JsonProperty
    private final List<String> highlights;

    @JsonProperty
    private final List<Result> results;

    @JsonPropertyDescription("A list of errors that occurred in running the query")
    @JsonProperty
    private final List<String> errors;

    @JsonPropertyDescription("True if the query has returned all known results")
    @JsonProperty
    private final Boolean complete;

    /**
     * @param queryKey   The key of the query associated with this response.
     * @param highlights A list of strings to highlight in the UI that should correlate with the search query.
     * @param results    A list of {@link Result result} objects that each correspond to a
     *                   {@link ResultRequest resultRequest} in the {@link SearchRequest searchRequest}
     * @param errors     Any errors that have been generated during searching.
     * @param complete   Complete means that the search has finished and there are no more results to come.
     */
    @JsonCreator
    public SearchResponse(@JsonProperty("queryKey") final QueryKey queryKey,
                          @JsonProperty("highlights") final List<String> highlights,
                          @JsonProperty("results") final List<Result> results,
                          @JsonProperty("errors") final List<String> errors,
                          @JsonProperty("complete") final Boolean complete) {
        this.queryKey = queryKey;
        this.highlights = highlights;
        this.results = results;
        this.errors = errors == null || errors.isEmpty()
                ? null
                : errors;
        this.complete = complete;
    }

    public QueryKey getQueryKey() {
        return queryKey;
    }

    /**
     * @return A list of strings to highlight in the UI that should correlate with the search query.
     */
    public List<String> getHighlights() {
        return highlights;
    }

    /**
     * @return A list of {@link Result result} objects, corresponding to the {@link ResultRequest resultRequests} in
     * the {@link SearchRequest searchRequest}
     */
    public List<Result> getResults() {
        return results;
    }

    /**
     * @return A list of errors found when performing the search
     */
    public List<String> getErrors() {
        return errors;
    }

    /**
     * @return The completed status of the search.  A value of false or null indicates the search has not yet
     * found all results.
     */
    public Boolean getComplete() {
        return complete;
    }

    /**
     * @return The completed status of the search.  A value of false indicates the search has not yet found all results
     */
    public boolean complete() {
        return complete != null && complete;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SearchResponse that = (SearchResponse) o;
        return Objects.equals(highlights, that.highlights) &&
                Objects.equals(results, that.results) &&
                Objects.equals(errors, that.errors) &&
                Objects.equals(complete, that.complete);
    }

    @Override
    public int hashCode() {
        return Objects.hash(highlights, results, errors, complete);
    }

    @Override
    public String toString() {
        return "SearchResponse{" +
                "highlights=" + highlights +
                ", results=" + results +
                ", errors=" + errors +
                ", complete=" + complete +
                '}';
    }

    public static class TableResultBuilder extends Builder<TableResult, TableResultBuilder> {

        @Override
        public TableResultBuilder self() {
            return this;
        }
    }

    public static class FlatResultBuilder extends Builder<FlatResult, FlatResultBuilder> {

        @Override
        public FlatResultBuilder self() {
            return this;
        }
    }

    /**
     * Builder for constructing a {@link SearchResponse}
     *
     * @param <T_RESULT_CLASS> The class of the popToWhenComplete builder, allows nested building
     */
    private abstract static class Builder<
            T_RESULT_CLASS extends Result,
            T_CHILD_CLASS extends Builder<T_RESULT_CLASS, ?>> {

        // Mandatory parameters
        QueryKey queryKey;
        Boolean complete;

        // Optional parameters
        List<String> highlights = new ArrayList<>();
        List<Result> results = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        Builder() {
        }

        Builder(final SearchResponse searchResponse) {
            queryKey = searchResponse.queryKey;
            complete = searchResponse.complete;
            highlights = searchResponse.highlights;
            results = searchResponse.results;
            errors = searchResponse.errors;
        }

        /**
         * @param value are the results considered complete
         * @return The {@link Builder}, enabling method chaining
         */
        public final T_CHILD_CLASS queryKey(final QueryKey queryKey) {
            this.queryKey = queryKey;
            return self();
        }

        /**
         * @param value are the results considered complete
         * @return The {@link Builder}, enabling method chaining
         */
        public final T_CHILD_CLASS complete(final Boolean value) {
            this.complete = value;
            return self();
        }

        public T_CHILD_CLASS highlights(final List<String> highlights) {
            this.highlights = highlights;
            return self();
        }

        public T_CHILD_CLASS results(final List<Result> results) {
            this.results = results;
            return self();
        }

        public T_CHILD_CLASS errors(final List<String> errors) {
            this.errors = errors;
            return self();
        }

        /**
         * Builds the {@link SearchResponse searchResponse} object
         *
         * @return A populated {@link SearchResponse searchResponse} object
         */
        public SearchResponse build() {
            return new SearchResponse(queryKey, highlights, results, errors, complete);
        }

        protected abstract T_CHILD_CLASS self();
    }

}
