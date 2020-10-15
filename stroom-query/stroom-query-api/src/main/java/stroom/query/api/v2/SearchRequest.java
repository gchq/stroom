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
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A class for describing a search request including the query to run and definition(s) of how the results
 * should be returned
 */
@JsonPropertyOrder({"key", "query", "resultRequests", "dateTimeLocale", "incremental", "timeout"})
@JsonInclude(Include.NON_NULL)
@XmlRootElement(name = "searchRequest")
@XmlType(name = "SearchRequest", propOrder = {"key", "query", "resultRequests", "dateTimeLocale", "incremental", "timeout"})
@XmlAccessorType(XmlAccessType.FIELD)
@ApiModel(description = "A request for new search or a follow up request for more data for an existing iterative search")
public final class SearchRequest implements Serializable {
    private static final long serialVersionUID = -6668626615097471925L;

    @XmlElement
    @ApiModelProperty(required = true)
    @JsonProperty
    private QueryKey key;

    @XmlElement
    @ApiModelProperty(required = true)
    @JsonProperty
    private Query query;

    @XmlElementWrapper(name = "resultRequests")
    @XmlElement(name = "resultRequest")
    @ApiModelProperty(required = true)
    @JsonProperty
    private List<ResultRequest> resultRequests;


    @XmlElement
    @ApiModelProperty(
            value = "The locale to use when formatting date values in the search results. The " +
                    "value is the string form of a java.time.ZoneId",
            required = true)
    @JsonProperty
    private String dateTimeLocale;

    @XmlElement
    @ApiModelProperty(
            value = "If true the response will contain all results found so far, typically no results on the first " +
                    "request. Future requests for the same query key may return more results. Intended for use on " +
                    "longer running searches to allow partial result sets to be returned as soon as they are " +
                    "available rather than waiting for the full result set.",
            required = true)
    @JsonProperty
    private Boolean incremental;

    @XmlElement
    @ApiModelProperty(
            value = "Set the maximum time (in ms) for the server to wait for a complete result set. The timeout applies to both " +
                    "incremental and non incremental queries, though the behaviour is slightly different. The timeout " +
                    "will make the server wait for which ever comes first out of the query completing or the timeout period " +
                    "being reached. If no value is supplied then for an incremental query a default value of 0 will be used " +
                    "(i.e. returning immediately) and for a non-incremental query the server's default timeout period will be " +
                    "used. For an incremental query, if the query has not completed by the end of the timeout period, it will " +
                    "return the currently know results with complete=false, however for a non-incremental query it will return " +
                    "no results, complete=false and details of the timeout in the error field")
    @JsonProperty
    private Long timeout;

    public SearchRequest() {
    }

    /**
     * @param key            A unique key to identify the instance of the search by. This key is used to identify multiple
     *                       requests for the same search when running in incremental mode.
     * @param query          The query terms for the search
     * @param resultRequests A list of {@link ResultRequest resultRequest} definitions. If null or the list is empty
     *                       no results will be returned. Allows the caller to request that the results of the query
     *                       are returned in multiple forms, e.g. using a number of different
     *                       filtering/aggregation/sorting approaches.
     * @param dateTimeLocale The locale to use when formatting date values in the search results. The value is the
     *                       string form of a {@link java.time.ZoneId zoneId}
     * @param incremental    If true the response will contain all results found so far. Future requests for the same
     *                       query key may return more results. Intended for use on longer running searches to allow
     *                       partial result sets to be returned as soon as they are available rather than waiting for the
     *                       full result set.
     * @param timeout        Set the maximum time (in ms) for the server to wait for a complete result set. The timeout applies to both
     *                       incremental and non incremental queries, though the behaviour is slightly different. The timeout
     *                       will make the server wait for which ever comes first out of the query completing or the timeout period
     *                       being reached. If no value is supplied then for an incremental query a default value of 0 will be used
     *                       (i.e. returning immediately) and for a non-incremental query the server's default timeout period will be
     *                       used. For an incremental query, if the query has not completed by the end of the timeout period, it will
     *                       return the currently know results with complete=false, however for a non-incremental query it will return
     *                       no results, complete=false and details of the timeout in the error field
     */
    @JsonCreator
    public SearchRequest(@JsonProperty("key") final QueryKey key,
                         @JsonProperty("query") final Query query,
                         @JsonProperty("resultRequests") final List<ResultRequest> resultRequests,
                         @JsonProperty("dateTimeLocale") final String dateTimeLocale,
                         @JsonProperty("incremental") final Boolean incremental,
                         @JsonProperty("timeout") final Long timeout) {
        this.key = key;
        this.query = query;
        this.resultRequests = resultRequests;
        this.dateTimeLocale = dateTimeLocale;
        this.incremental = incremental;
        this.timeout = timeout;
    }

    /**
     * See {@link SearchRequest#SearchRequest(QueryKey, Query, List, String, Boolean, Long)}
     *
     * @param key            As linked
     * @param query          As linked
     * @param resultRequests As linked
     * @param dateTimeLocale As linked
     * @param incremental    As linked
     */
    public SearchRequest(final QueryKey key,
                         final Query query,
                         final List<ResultRequest> resultRequests,
                         final String dateTimeLocale,
                         final Boolean incremental) {
        this.key = key;
        this.query = query;
        this.resultRequests = resultRequests;
        this.dateTimeLocale = dateTimeLocale;
        this.incremental = incremental;
        this.timeout = null;
    }

    /**
     * @return The unique {@link QueryKey queryKey} for the search request
     */
    public QueryKey getKey() {
        return key;
    }

    public void setKey(final QueryKey key) {
        this.key = key;
    }

    /**
     * @return The {@link Query query} object containing the search terms
     */
    public Query getQuery() {
        return query;
    }

    public void setQuery(final Query query) {
        this.query = query;
    }

    /**
     * @return The list of {@link ResultRequest resultRequest} objects
     */
    public List<ResultRequest> getResultRequests() {
        return resultRequests;
    }

    public void setResultRequests(final List<ResultRequest> resultRequests) {
        this.resultRequests = resultRequests;
    }

    /**
     * @return The locale ID, see {@link java.time.ZoneId}, for the date values uses in the search response.
     */
    public String getDateTimeLocale() {
        return dateTimeLocale;
    }

    public void setDateTimeLocale(final String dateTimeLocale) {
        this.dateTimeLocale = dateTimeLocale;
    }

    /**
     * @return Whether the search should return immediately with the results found so far or wait for the search
     * to finish
     */
    public Boolean getIncremental() {
        return incremental;
    }

    public void setIncremental(final Boolean incremental) {
        this.incremental = incremental;
    }

    /**
     * @return The timeout period in ms. Can be null.
     */
    public Long getTimeout() {
        return timeout;
    }

    public void setTimeout(final Long timeout) {
        this.timeout = timeout;
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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SearchRequest that = (SearchRequest) o;
        return Objects.equals(key, that.key) &&
                Objects.equals(query, that.query) &&
                Objects.equals(resultRequests, that.resultRequests) &&
                Objects.equals(dateTimeLocale, that.dateTimeLocale) &&
                Objects.equals(incremental, that.incremental) &&
                Objects.equals(timeout, that.timeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, query, resultRequests, dateTimeLocale, incremental, timeout);
    }

    @Override
    public String toString() {
        return "SearchRequest{" +
                "key=" + key +
                ", query=" + query +
                ", resultRequests=" + resultRequests +
                ", dateTimeLocale='" + dateTimeLocale + '\'' +
                ", incremental=" + incremental +
                ", timeout=" + timeout +
                '}';
    }

    /**
     * Builder for constructing a {@link SearchRequest}
     */
    public static class Builder {

        private final List<ResultRequest> resultRequests = new ArrayList<>();
        private QueryKey key;
        private Query query;
        private String dateTimeLocale;

        private Boolean incremental;

        private Long timeout;

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
            this.resultRequests.addAll(Arrays.asList(values));
            return this;
        }

        /**
         * @param value The date time locale to apply to any date/time results
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder dateTimeLocale(final String value) {
            this.dateTimeLocale = value;
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
         * @param value See {@link SearchRequest#SearchRequest(QueryKey, Query, List, String, Boolean, Long)}
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder timeout(final Long value) {
            this.timeout = value;
            return this;
        }

        public SearchRequest build() {
            return new SearchRequest(key, query, resultRequests, dateTimeLocale, incremental, timeout);
        }
    }

}