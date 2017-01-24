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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.Arrays;

@JsonPropertyOrder({"highlights", "errors", "complete", "results"})
@XmlRootElement(name = "searchResponse")
@XmlType(name = "searchResponse", propOrder = {"highlights", "errors", "complete", "results"})
public class SearchResponse implements Serializable {
    private static final long serialVersionUID = -2964122512841756795L;

    /**
     * A set of strings to highlight in the UI that should correlate with the
     * search query.
     */
    private String[] highlights;

    /**
     * Any errors that have been generated during searching.
     */
    private String[] errors;

    /**
     * Complete means that all index shards have been searched across the
     * cluster and there are no more results to come.
     **/
    private Boolean complete;

    private Result[] results;

    public SearchResponse() {
    }

    @XmlElementWrapper(name = "highlights")
    @XmlElement(name = "highlight")
    public String[] getHighlights() {
        return highlights;
    }

    public void setHighlights(final String[] highlights) {
        this.highlights = highlights;
    }

    @XmlElementWrapper(name = "errors")
    @XmlElement(name = "error")
    public String[] getErrors() {
        return errors;
    }

    public void setErrors(final String[] errors) {
        this.errors = errors;
    }

    @XmlElement
    public Boolean getComplete() {
        return complete;
    }

    public void setComplete(final Boolean complete) {
        this.complete = complete;
    }

    public boolean complete() {
        return complete != null && complete;
    }

    @XmlElementWrapper(name = "results")
    @XmlElements({
            @XmlElement(name = "table", type = TableResult.class),
            @XmlElement(name = "vis", type = VisResult.class)
    })
    public Result[] getResults() {
        return results;
    }

    public void setResults(final Result[] results) {
        this.results = results;
    }

//    public void addResult(final ComponentResult result) {
//        if (results == null) {
//            results = new ArrayList<>();
//        }
//        results.add(result);
//    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final SearchResponse that = (SearchResponse) o;

        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(highlights, that.highlights)) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(errors, that.errors)) return false;
        if (complete != null ? !complete.equals(that.complete) : that.complete != null) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        return Arrays.equals(results, that.results);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(highlights);
        result = 31 * result + Arrays.hashCode(errors);
        result = 31 * result + (complete != null ? complete.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(results);
        return result;
    }

    @Override
    public String toString() {
        return "SearchResponse{" +
                "highlights=" + Arrays.toString(highlights) +
                ", errors=" + Arrays.toString(errors) +
                ", complete=" + complete +
                ", results=" + Arrays.toString(results) +
                '}';
    }
}
