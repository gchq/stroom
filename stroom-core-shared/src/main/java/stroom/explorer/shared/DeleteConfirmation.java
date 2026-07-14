/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.explorer.shared;

import stroom.docref.DocRef;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The information needed to confirm a delete: the items that would additionally be deleted because
 * they are contained within a folder in the selection, and the documents outside the selection that
 * depend on what is being deleted.
 * <p>
 * For both, the identities of docs the current user may view are provided, while the existence of any
 * further docs they may not view is disclosed via a flag (never named or counted).
 */
@JsonInclude(Include.NON_NULL)
public class DeleteConfirmation {

    public static final DeleteConfirmation EMPTY = new DeleteConfirmation(
            Collections.emptyList(), 0, Collections.emptyMap(), false, false,
            Collections.emptyList(), false);

    /**
     * Viewable descendants of the selected folders (excluding the selected roots), capped for display.
     */
    @JsonProperty
    private final List<DocRef> childItems;
    /**
     * Total number of <b>viewable</b> descendants that would also be deleted (excluding the selected
     * roots). May exceed the size of {@link #childItems} if that list was capped for display. Items the
     * user cannot view are not counted here; their existence is disclosed via {@link #hasHiddenChildItems}.
     */
    @JsonProperty
    private final int totalChildCount;
    /**
     * The viewable contained items grouped by document type (type -&gt; count), covering all viewable
     * contained items (not just the capped {@link #childItems}), for a per-type summary.
     */
    @JsonProperty
    private final Map<String, Integer> childTypeCounts;
    /**
     * True if at least one contained item cannot be viewed by the current user.
     */
    @JsonProperty
    private final boolean hasHiddenChildItems;
    /**
     * True if {@link #childItems} was capped, i.e. there are more viewable contained items than listed.
     */
    @JsonProperty
    private final boolean childItemsTruncated;
    /**
     * Documents outside the delete set that depend on what is being deleted and that the user may view.
     */
    @JsonProperty
    private final List<DocRef> visibleDependants;
    /**
     * True if at least one dependant cannot be viewed by the current user.
     */
    @JsonProperty
    private final boolean hasHiddenDependants;

    @JsonCreator
    public DeleteConfirmation(@JsonProperty("childItems") final List<DocRef> childItems,
                              @JsonProperty("totalChildCount") final int totalChildCount,
                              @JsonProperty("childTypeCounts") final Map<String, Integer> childTypeCounts,
                              @JsonProperty("hasHiddenChildItems") final boolean hasHiddenChildItems,
                              @JsonProperty("childItemsTruncated") final boolean childItemsTruncated,
                              @JsonProperty("visibleDependants") final List<DocRef> visibleDependants,
                              @JsonProperty("hasHiddenDependants") final boolean hasHiddenDependants) {
        this.childItems = childItems;
        this.totalChildCount = totalChildCount;
        this.childTypeCounts = childTypeCounts;
        this.hasHiddenChildItems = hasHiddenChildItems;
        this.childItemsTruncated = childItemsTruncated;
        this.visibleDependants = visibleDependants;
        this.hasHiddenDependants = hasHiddenDependants;
    }

    public List<DocRef> getChildItems() {
        return childItems;
    }

    public int getTotalChildCount() {
        return totalChildCount;
    }

    public Map<String, Integer> getChildTypeCounts() {
        return childTypeCounts;
    }

    public boolean isHasHiddenChildItems() {
        return hasHiddenChildItems;
    }

    public boolean isChildItemsTruncated() {
        return childItemsTruncated;
    }

    public List<DocRef> getVisibleDependants() {
        return visibleDependants;
    }

    public boolean isHasHiddenDependants() {
        return hasHiddenDependants;
    }

    @JsonIgnore
    public boolean hasChildItems() {
        // Includes the case where every contained item is hidden (nothing viewable to count/list).
        return totalChildCount > 0 || hasHiddenChildItems;
    }

    @JsonIgnore
    public boolean hasDependants() {
        return hasHiddenDependants || NullSafe.hasItems(visibleDependants);
    }

    @JsonIgnore
    public boolean isEmpty() {
        return !hasChildItems() && !hasDependants();
    }
}
