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

@JsonPropertyOrder({"key", "query", "resultRequests", "dateTimeLocale", "incremental"})
@XmlRootElement(name = "searchRequest")
@XmlType(name = "SearchRequest", propOrder = {"key", "query", "resultRequests", "dateTimeLocale", "incremental"})
@XmlAccessorType(XmlAccessType.FIELD)
public final class SearchRequest implements Serializable {
    private static final long serialVersionUID = -6668626615097471925L;

    @XmlElement
    private QueryKey key;
    @XmlElement
    private Query query;
    @XmlElementWrapper(name = "resultRequests")
    @XmlElement(name = "resultRequest")
    private List<ResultRequest> resultRequests;
    @XmlElement
    private String dateTimeLocale;
    @XmlElement
    private Boolean incremental;

    public SearchRequest() {
    }

    public SearchRequest(final QueryKey key, final Query query, final List<ResultRequest> resultRequests,
                         final String dateTimeLocale) {
        this.key = key;
        this.query = query;
        this.resultRequests = resultRequests;
        this.dateTimeLocale = dateTimeLocale;
    }

    public QueryKey getKey() {
        return key;
    }

    public void setKey(final QueryKey key) {
        this.key = key;
    }

    public Query getQuery() {
        return query;
    }

    public void setQuery(final Query query) {
        this.query = query;
    }

    public List<ResultRequest> getResultRequests() {
        return resultRequests;
    }

    public void setResultRequests(final List<ResultRequest> resultRequests) {
        this.resultRequests = resultRequests;
    }

    public String getDateTimeLocale() {
        return dateTimeLocale;
    }

    public void setDateTimeLocale(final String dateTimeLocale) {
        this.dateTimeLocale = dateTimeLocale;
    }

    public Boolean getIncremental() {
        return incremental;
    }

    public void setIncremental(final Boolean incremental) {
        this.incremental = incremental;
    }

    public boolean incremental() {
        return incremental != null && incremental;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final SearchRequest that = (SearchRequest) o;

        if (key != null ? !key.equals(that.key) : that.key != null) return false;
        if (query != null ? !query.equals(that.query) : that.query != null) return false;
        if (resultRequests != null ? !resultRequests.equals(that.resultRequests) : that.resultRequests != null)
            return false;
        if (dateTimeLocale != null ? !dateTimeLocale.equals(that.dateTimeLocale) : that.dateTimeLocale != null)
            return false;
        return incremental != null ? incremental.equals(that.incremental) : that.incremental == null;
    }

    @Override
    public int hashCode() {
        int result = key != null ? key.hashCode() : 0;
        result = 31 * result + (query != null ? query.hashCode() : 0);
        result = 31 * result + (resultRequests != null ? resultRequests.hashCode() : 0);
        result = 31 * result + (dateTimeLocale != null ? dateTimeLocale.hashCode() : 0);
        result = 31 * result + (incremental != null ? incremental.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "SearchRequest{" +
                "key=" + key +
                ", query=" + query +
                ", resultRequests=" + resultRequests +
                ", dateTimeLocale='" + dateTimeLocale + '\'' +
                ", incremental=" + incremental +
                '}';
    }
}