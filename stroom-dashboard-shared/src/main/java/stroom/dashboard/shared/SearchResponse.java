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

package stroom.dashboard.shared;

import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;
import stroom.util.shared.SharedObject;
import stroom.util.shared.ToStringBuilder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "searchResponse", propOrder = {"highlights", "errors", "complete", "results"})
@XmlRootElement(name = "searchResponse")
public class SearchResponse implements SharedObject {
    private static final long serialVersionUID = -2964122512841756795L;

    /**
     * A set of strings to highlight in the UI that should correlate with the
     * search query.
     */
    @XmlElement
    private Set<String> highlights;

    /**
     * Any errors that have been generated during searching.
     */
    @XmlElement
    private String errors;

    /**
     * Complete means that all index shards have been searched across the
     * cluster and there are no more results to come.
     **/
    @XmlElement
    private boolean complete;

    @XmlElement
    private Map<String, ComponentResult> results;

    public SearchResponse() {
        // Default constructor necessary for GWT serialisation.
    }

    public Set<String> getHighlights() {
        return highlights;
    }

    public void setHighlights(final Set<String> highlights) {
        this.highlights = highlights;
    }

    public String getErrors() {
        return errors;
    }

    public void setErrors(final String errors) {
        this.errors = errors;
    }

    public boolean isComplete() {
        return complete;
    }

    public void setComplete(final boolean complete) {
        this.complete = complete;
    }

    @Override
    public String toString() {
        final ToStringBuilder builder = new ToStringBuilder();
        builder.append("highlights", highlights);
        builder.append("errors", errors);
        builder.append("complete", complete);
        return builder.toString();
    }

    public Map<String, ComponentResult> getResults() {
        return results;
    }

    public void addResult(final String componentId, final ComponentResult result) {
        if (results == null) {
            results = new HashMap<>();
        }
        results.put(componentId, result);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        SearchResponse that = (SearchResponse) o;

        return new EqualsBuilder()
                .append(complete, that.complete)
                .append(highlights, that.highlights)
                .append(errors, that.errors)
                .append(results, that.results)
                .isEquals();
    }

    @Override
    public int hashCode() {
        HashCodeBuilder hashCodeBuilder = new HashCodeBuilder();
        hashCodeBuilder.append(highlights);
        hashCodeBuilder.append(errors);
        hashCodeBuilder.append(complete);
        hashCodeBuilder.append(results);
        return hashCodeBuilder.toHashCode();
    }
}
