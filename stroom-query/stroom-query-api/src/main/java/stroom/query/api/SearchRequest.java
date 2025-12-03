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

package stroom.query.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A class for describing a search request including the query to run and definition(s) of how the results
 * should be returned
 */
@JsonPropertyOrder({
        "searchRequestSource",
        "key",
        "query",
        "resultRequests",
        "dateTimeLocale",
        "incremental",
        "timeout"})
@JsonInclude(Include.NON_NULL)
@Schema(description = "A request for new search or a follow up request for more data for an existing " +
                      "iterative search")
public final class SearchRequest {

    @JsonProperty
    private final SearchRequestSource searchRequestSource;

    @JsonProperty
    private final QueryKey key;

    @JsonProperty
    private final Query query;

    @JsonProperty
    private final List<ResultRequest> resultRequests;

    @Schema(description = "The client date/time settings")
    @JsonProperty
    private final DateTimeSettings dateTimeSettings;

    @Schema(description = "If true the response will contain all results found so far, typically no results on the " +
                          "first request. Future requests for the same query key may return more results. Intended " +
                          "for use on longer running searches to allow partial result sets to be returned as soon as " +
                          "they are available rather than waiting for the full result set.")
    @JsonProperty
    private final Boolean incremental;

    @Schema(description = "Set the maximum time (in ms) for the server to wait for a complete result set. The " +
                          "timeout applies to both incremental and non incremental queries, though the behaviour is " +
                          "slightly different. The timeout will make the server wait for which ever comes first out " +
                          "of the query completing or the timeout period being reached. If no value is supplied then " +
                          "for an incremental query a default value of 0 will be used (i.e. returning immediately) " +
                          "and for a non-incremental query the server's default timeout period will be used. For an " +
                          "incremental query, if the query has not completed by the end of the timeout period, it " +
                          "will return the currently know results with complete=false, however for a non-incremental " +
                          "query it will return no results, complete=false and details of the timeout in the error " +
                          "field")
    @JsonProperty
    private final Long timeout;

    /**
     * @param key              A unique key to identify the instance of the search by. This key is used to
     *                         identify multiple requests for the same search when running in incremental mode.
     * @param query            The query terms for the search
     * @param resultRequests   A list of {@link ResultRequest resultRequest} definitions. If null or the list is
     *                         empty no results will be returned. Allows the caller to request that the results of
     *                         the query are returned in multiple forms, e.g. using a number of different
     *                         filtering/aggregation/sorting approaches.
     * @param dateTimeSettings The client date time settings to use when formatting date values in the search
     *                         results.
     * @param incremental      If true the response will contain all results found so far. Future requests for the
     *                         same query key may return more results. Intended for use on longer running searches
     *                         to allow partial result sets to be returned as soon as they are available rather
     *                         than waiting for the full result set.
     * @param timeout          Set the maximum time (in ms) for the server to wait for a complete result set.The
     *                         timeout applies to both incremental and non incremental queries, though the
     *                         behaviour is slightly different. The timeout will make the server wait for which
     *                         ever comes first out of the query completing or the timeout period being reached.
     *                         If no value is supplied then for an incremental query a default value of 0 will be
     *                         used (i.e. returning immediately) and for a non-incremental query the server's
     *                         default timeout period will be used. For an incremental query, if the query has not
     *                         completed by the end of the timeout period, it will return the currently know
     *                         results with complete=false, however for a non-incremental query it will return no
     *                         results, complete=false and details of the timeout in the error field
     */
    @JsonCreator
    public SearchRequest(
            @JsonProperty("searchRequestSource") final SearchRequestSource searchRequestSource,
            @JsonProperty("key") final QueryKey key,
            @JsonProperty("query") final Query query,
            @JsonProperty("resultRequests") final List<ResultRequest> resultRequests,
            @JsonProperty("dateTimeSettings") final DateTimeSettings dateTimeSettings,
            @JsonProperty("incremental") final Boolean incremental,
            @JsonProperty("timeout") final Long timeout) {
        this.searchRequestSource = searchRequestSource;
        this.key = key;
        this.query = query;
        this.resultRequests = resultRequests;
        this.dateTimeSettings = dateTimeSettings;
        this.incremental = incremental;
        this.timeout = timeout;
    }

    public SearchRequest(final SearchRequestSource searchRequestSource,
                         final QueryKey key,
                         final Query query,
                         final List<ResultRequest> resultRequests,
                         final DateTimeSettings dateTimeSettings,
                         final Boolean incremental) {
        this.searchRequestSource = searchRequestSource;
        this.key = key;
        this.query = query;
        this.resultRequests = resultRequests;
        this.dateTimeSettings = dateTimeSettings;
        this.incremental = incremental;
        this.timeout = null;
    }

    /**
     * Where did this search request originate, e.g. query, dashboard or API?
     *
     * @return The source of the search request.
     */
    public SearchRequestSource getSearchRequestSource() {
        return searchRequestSource;
    }

    /**
     * @return The unique {@link QueryKey queryKey} for the search request
     */
    public QueryKey getKey() {
        return key;
    }

    /**
     * @return The {@link Query query} object containing the search terms
     */
    public Query getQuery() {
        return query;
    }

    /**
     * @return The list of {@link ResultRequest resultRequest} objects
     */
    public List<ResultRequest> getResultRequests() {
        return resultRequests;
    }

    /**
     * @return Date and time settings specific to the client making the request.
     */
    public DateTimeSettings getDateTimeSettings() {
        return dateTimeSettings;
    }

    /**
     * @return Whether the search should return immediately with the results found so far or wait for the search
     * to finish
     */
    public Boolean getIncremental() {
        return incremental;
    }

    /**
     * @return The timeout period in ms. Can be null.
     */
    public Long getTimeout() {
        return timeout;
    }

    /**
     * @return Whether the search should return immediately with the results found so far or wait for the search
     * to finish
     */
    public boolean incremental() {
        return incremental != null && incremental;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SearchRequest that = (SearchRequest) o;
        return Objects.equals(key, that.key) &&
               Objects.equals(query, that.query) &&
               Objects.equals(resultRequests, that.resultRequests) &&
               Objects.equals(dateTimeSettings, that.dateTimeSettings) &&
               Objects.equals(incremental, that.incremental) &&
               Objects.equals(timeout, that.timeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, query, resultRequests, dateTimeSettings, incremental, timeout);
    }

    @Override
    public String toString() {
        return "SearchRequest{" +
               "key=" + key +
               ", query=" + query +
               ", resultRequests=" + resultRequests +
               ", dateTimeSettings='" + dateTimeSettings + '\'' +
               ", incremental=" + incremental +
               ", timeout=" + timeout +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    /**
     * Builder for constructing a {@link SearchRequest}
     */
    public static final class Builder {

        private SearchRequestSource searchRequestSource;
        private List<ResultRequest> resultRequests;
        private QueryKey key;
        private Query query;
        private DateTimeSettings dateTimeSettings;
        private Boolean incremental;
        private Long timeout;

        private Builder() {
        }

        private Builder(final SearchRequest searchRequest) {
            this.searchRequestSource = searchRequest.searchRequestSource;
            this.resultRequests = searchRequest.resultRequests;
            this.key = searchRequest.key;
            this.query = searchRequest.query;
            this.dateTimeSettings = searchRequest.dateTimeSettings;
            this.incremental = searchRequest.incremental;
            this.timeout = searchRequest.timeout;
        }

        /**
         * Where did this search request originate, e.g. query, dashboard or API?
         *
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder searchRequestSource(final SearchRequestSource searchRequestSource) {
            this.searchRequestSource = searchRequestSource;
            return this;
        }

        /**
         * @param value A unique key to identify the instance of the search by. This key is used to identify multiple
         *              requests for the same search when running in incremental mode.
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder key(final QueryKey value) {
            this.key = value;
            return this;
        }

        /**
         * Shortcut function to add a key value in one go
         *
         * @param uuid The UUID of the query key
         * @return this builder
         */
        public Builder key(final String uuid) {
            return key(new QueryKey(uuid));
        }

        /**
         * @param value The query terms for the search
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder query(final Query value) {
            this.query = value;
            return this;
        }

        /**
         * @param values The various forms of results required by the caller.
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder addResultRequests(final ResultRequest... values) {
            if (resultRequests == null) {
                this.resultRequests = new ArrayList<>();
            }
            this.resultRequests.addAll(Arrays.asList(values));
            return this;
        }

        public Builder resultRequests(final List<ResultRequest> resultRequests) {
            this.resultRequests = resultRequests;
            return this;
        }

        /**
         * @param value The date time settings to apply to any date/time results
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder dateTimeSettings(final DateTimeSettings value) {
            this.dateTimeSettings = value;
            return this;
        }

        /**
         * @param value If true the response will contain all results found so far. Future requests for the same
         *              query key may return more results. Intended for use on longer running searches to allow
         *              partial result sets to be returned as soon as they are available rather than waiting for the
         *              full result set.
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder incremental(final Boolean value) {
            this.incremental = value;
            return this;
        }

        /**
         * @param value The timeout period in ms. Can be null.
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder timeout(final Long value) {
            this.timeout = value;
            return this;
        }

        public SearchRequest build() {
            return new SearchRequest(
                    searchRequestSource,
                    key,
                    query,
                    resultRequests,
                    dateTimeSettings,
                    incremental,
                    timeout);
        }
    }

}
