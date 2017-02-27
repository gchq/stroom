/*
 * Copyright 2016 Crown Copyright
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

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.List;

@JsonPropertyOrder({"highlights", "results", "errors", "complete"})
@XmlRootElement(name = "searchResponse")
@XmlType(name = "SearchResponse", propOrder = {"highlights", "results", "errors", "complete"})
@XmlAccessorType(XmlAccessType.FIELD)
public final class SearchResponse implements Serializable {
    private static final long serialVersionUID = -2964122512841756795L;

    /**
     * A set of strings to highlight in the UI that should correlate with the
     * search query.
     */
    @XmlElementWrapper(name = "highlights")
    @XmlElement(name = "highlight")
    private List<String> highlights;

    @XmlElementWrapper(name = "results")
    @XmlElements({
            @XmlElement(name = "table", type = TableResult.class),
            @XmlElement(name = "vis", type = FlatResult.class)
    })
    private List<Result> results;

    /**
     * Any errors that have been generated during searching.
     */
    @XmlElementWrapper(name = "errors")
    @XmlElement(name = "error")
    private List<String> errors;

    /**
     * Complete means that all index shards have been searched across the
     * cluster and there are no more results to come.
     **/
    @XmlElement
    private Boolean complete;

    private SearchResponse() {
    }

    public SearchResponse(final List<String> highlights, final List<Result> results, final List<String> errors, final Boolean complete) {
        this.highlights = highlights;
        this.results = results;
        this.errors = errors;
        this.complete = complete;
    }

    public List<String> getHighlights() {
        return highlights;
    }

    public List<Result> getResults() {
        return results;
    }

    public List<String> getErrors() {
        return errors;
    }

    public Boolean getComplete() {
        return complete;
    }

    public boolean complete() {
        return complete != null && complete;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final SearchResponse that = (SearchResponse) o;

        if (highlights != null ? !highlights.equals(that.highlights) : that.highlights != null) return false;
        if (results != null ? !results.equals(that.results) : that.results != null) return false;
        if (errors != null ? !errors.equals(that.errors) : that.errors != null) return false;
        return complete != null ? complete.equals(that.complete) : that.complete == null;
    }

    @Override
    public int hashCode() {
        int result = highlights != null ? highlights.hashCode() : 0;
        result = 31 * result + (results != null ? results.hashCode() : 0);
        result = 31 * result + (errors != null ? errors.hashCode() : 0);
        result = 31 * result + (complete != null ? complete.hashCode() : 0);
        return result;
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
}