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

import stroom.util.shared.SharedObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class FetchExplorerNodeResult implements SharedObject {
    private static final long serialVersionUID = 6474393620176001063L;

    private TreeStructure treeStructure = new TreeStructure();
    private List<ExplorerNode> openedItems = new ArrayList<>();
    private HashSet<ExplorerNode> temporaryOpenedItems;

    public FetchExplorerNodeResult() {
        // Default constructor necessary for GWT serialisation.
    }

    public TreeStructure getTreeStructure() {
        return treeStructure;
    }

    public List<ExplorerNode> getOpenedItems() {
        return openedItems;
    }

    public HashSet<ExplorerNode> getTemporaryOpenedItems() {
        return temporaryOpenedItems;
    }

    public void setTemporaryOpenedItems(final HashSet<ExplorerNode> temporaryOpenedItems) {
        this.temporaryOpenedItems = temporaryOpenedItems;
    }
}
