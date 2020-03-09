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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class FetchExplorerNodeResult {
    @JsonProperty
    private List<ExplorerNode> rootNodes;
    @JsonProperty
    private List<String> openedItems;
    @JsonProperty
    private Set<String> temporaryOpenedItems;

    public FetchExplorerNodeResult() {
        rootNodes = new ArrayList<>();
        openedItems = new ArrayList<>();
    }

    @JsonCreator
    public FetchExplorerNodeResult(@JsonProperty("rootNodes") final List<ExplorerNode> rootNodes,
                                   @JsonProperty("openedItems") final List<String> openedItems,
                                   @JsonProperty("temporaryOpenedItems") final Set<String> temporaryOpenedItems) {
        this.rootNodes = rootNodes;
        this.openedItems = openedItems;
        this.temporaryOpenedItems = temporaryOpenedItems;
    }

    public List<ExplorerNode> getRootNodes() {
        return rootNodes;
    }

    public List<String> getOpenedItems() {
        return openedItems;
    }

    public Set<String> getTemporaryOpenedItems() {
        return temporaryOpenedItems;
    }

    public void setTemporaryOpenedItems(final Set<String> temporaryOpenedItems) {
        this.temporaryOpenedItems = temporaryOpenedItems;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final FetchExplorerNodeResult that = (FetchExplorerNodeResult) o;
        return Objects.equals(rootNodes, that.rootNodes) &&
                Objects.equals(openedItems, that.openedItems) &&
                Objects.equals(temporaryOpenedItems, that.temporaryOpenedItems);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rootNodes, openedItems, temporaryOpenedItems);
    }
}
