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

package stroom.explorer.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class FetchExplorerNodesRequest {

    @JsonProperty
    private final Set<ExplorerNodeKey> openItems;
    @JsonProperty
    private final Set<ExplorerNodeKey> temporaryOpenedItems;
    @JsonProperty
    private final ExplorerTreeFilter filter;
    @JsonProperty
    private final Integer minDepth;
    @JsonProperty
    private final Set<ExplorerNodeKey> ensureVisible;
    @JsonProperty
    private final boolean showAlerts;
    @JsonProperty
    private final boolean refresh;

    /// Creates a request that is served from the server's cached explorer tree model.
    ///
    /// @param openItems            The nodes the user has opened.
    /// @param temporaryOpenedItems The nodes opened temporarily, e.g. to show filter matches.
    /// @param filter               The filter to apply to the tree.
    /// @param minDepth             The depth to which the tree should be opened as a minimum.
    /// @param ensureVisible        The nodes whose ancestors should be opened so they are visible.
    /// @param showAlerts           Whether to include alerts, e.g. broken dependencies, on nodes.
    public FetchExplorerNodesRequest(final Set<ExplorerNodeKey> openItems,
                                     final Set<ExplorerNodeKey> temporaryOpenedItems,
                                     final ExplorerTreeFilter filter,
                                     final Integer minDepth,
                                     final Set<ExplorerNodeKey> ensureVisible,
                                     final Boolean showAlerts) {
        this(openItems, temporaryOpenedItems, filter, minDepth, ensureVisible, showAlerts, false);
    }

    @JsonCreator
    public FetchExplorerNodesRequest(
            @JsonProperty("openItems") final Set<ExplorerNodeKey> openItems,
            @JsonProperty("temporaryOpenedItems") final Set<ExplorerNodeKey> temporaryOpenedItems,
            @JsonProperty("filter") final ExplorerTreeFilter filter,
            @JsonProperty("minDepth") final Integer minDepth,
            @JsonProperty("ensureVisible") final Set<ExplorerNodeKey> ensureVisible,
            @JsonProperty("showAlerts") final Boolean showAlerts,
            @JsonProperty("refresh") final Boolean refresh) {
        this.openItems = openItems;
        this.temporaryOpenedItems = temporaryOpenedItems;
        this.filter = filter;
        this.minDepth = minDepth;
        this.ensureVisible = ensureVisible;
        this.showAlerts = Objects.requireNonNullElse(showAlerts, false);
        this.refresh = Objects.requireNonNullElse(refresh, false);
    }

    public Set<ExplorerNodeKey> getOpenItems() {
        return openItems;
    }

    public Set<ExplorerNodeKey> getTemporaryOpenedItems() {
        return temporaryOpenedItems;
    }

    public ExplorerTreeFilter getFilter() {
        return filter;
    }

    public Integer getMinDepth() {
        return minDepth;
    }

    public Set<ExplorerNodeKey> getEnsureVisible() {
        return ensureVisible;
    }

    public boolean getShowAlerts() {
        return showAlerts;
    }

    /// @return `true` if the server should rebuild its explorer tree model from the database before
    /// returning nodes, rather than serving a cached model that may not reflect changes made on other
    /// nodes or by other users.
    public boolean isRefresh() {
        return refresh;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FetchExplorerNodesRequest criteria = (FetchExplorerNodesRequest) o;
        return Objects.equals(openItems, criteria.openItems) &&
                Objects.equals(temporaryOpenedItems, criteria.temporaryOpenedItems) &&
                Objects.equals(filter, criteria.filter) &&
                Objects.equals(minDepth, criteria.minDepth) &&
                Objects.equals(ensureVisible, criteria.ensureVisible) &&
                refresh == criteria.refresh;
    }

    @Override
    public int hashCode() {
        return Objects.hash(openItems, temporaryOpenedItems, filter, minDepth, ensureVisible, refresh);
    }
}
