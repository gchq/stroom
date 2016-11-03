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

package stroom.query.shared;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import stroom.util.shared.SharedObject;
import stroom.util.shared.ToStringBuilder;

public class SearchResult implements SharedObject {
    private static final long serialVersionUID = -2964122512841756795L;

    /**
     * A set of strings to highlight in the UI that should correlate with the
     * search query.
     */
    private Set<String> highlights;

    /**
     * Any errors that have been generated during searching.
     */
    private String errors;

    /**
     * Complete means that all index shards have been searched across the
     * cluster and there are no more results to come.
     **/
    private boolean complete;

    private Map<String, SharedObject> results;

    public SearchResult() {
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

    public Map<String, SharedObject> getResults() {
        return results;
    }

    public void addResult(final String componentId, final SharedObject result) {
        if (results == null) {
            results = new HashMap<>();
        }
        results.put(componentId, result);
    }
}
