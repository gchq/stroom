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

package stroom.explorer.client.component;

import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.NodeFlag;
import stroom.security.shared.DocumentPermission;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/// Fills an explorer tree with tick boxes ([ExplorerTree] with `tickBoxes="true"`) that a template
/// declares, showing each item's alerts, for the user to tick the items to act on, e.g. to export:
///
/// ```java
/// tree = make(documentTickTreeControllerProvider).attach(view.getTree());
/// tree.setRequiredPermissions(DocumentPermission.VIEW);
/// ```
public class DocumentTickTreeController extends Controller {

    private final Provider<ExplorerTreeController> explorerTreeControllerProvider;
    private ExplorerTree explorerTree;
    private ExplorerTreeController treeController;

    /// Made by GIN; [#attach(ExplorerTree)] gives it its tree.
    ///
    /// @param explorerTreeControllerProvider Makes what fills the tree.
    @Inject
    public DocumentTickTreeController(final Provider<ExplorerTreeController> explorerTreeControllerProvider) {
        this.explorerTreeControllerProvider = explorerTreeControllerProvider;
    }

    /// Attaches this to the tree it fills, giving it tick boxes.
    ///
    /// @param explorerTree The tree, e.g. one a template declares.
    /// @return This, to configure.
    public DocumentTickTreeController attach(final ExplorerTree explorerTree) {
        this.explorerTree = Objects.requireNonNull(explorerTree, "explorerTree");
        explorerTree.setTickBoxes(true);
        treeController = make(explorerTreeControllerProvider).attach(explorerTree);
        treeController.setShowAlerts(true);
        return this;
    }

    /// Puts the focus on the tree.
    public void focus() {
        explorerTree.setFocus(true);
    }

    /// @param includedTypes The types of document offered.
    public void setIncludedTypes(final String... includedTypes) {
        treeController.setIncludedTypes(includedTypes);
    }

    /// Offers only these types of document, and fetches the tree again.
    ///
    /// @param types The types.
    public void setIncludedTypeSet(final Set<String> types) {
        treeController.setIncludedTypeSet(types);
        refresh();
    }

    /// @param types The types of the tree's roots.
    public void setIncludedRootTypes(final String... types) {
        treeController.setIncludedRootTypes(types);
    }

    /// Filters the items by name, and fetches the tree again.
    ///
    /// @param name The filter.
    public void changeNameFilter(final String name) {
        treeController.changeNameFilter(name);
        refresh();
    }

    /// @param tags The tags a document must have to be offered.
    public void setTags(final String... tags) {
        treeController.setTags(tags);
    }

    /// @param nodeFlags The flags of the explorer nodes offered.
    public void setNodeFlags(final NodeFlag... nodeFlags) {
        treeController.setNodeFlags(nodeFlags);
    }

    /// @param requiredPermissions The permissions the user must have on the items offered.
    public void setRequiredPermissions(final DocumentPermission... requiredPermissions) {
        treeController.setRequiredPermissions(requiredPermissions);
    }

    /// Fetches the tree again.
    public void refresh() {
        treeController.refresh();
    }

    /// @return What fills the tree, e.g. to show items in it.
    public ExplorerTreeController getTreeController() {
        return treeController;
    }

    /// @param explorerNode The item.
    /// @param selected     Whether it is ticked.
    public void setSelected(final ExplorerNode explorerNode, final boolean selected) {
        explorerTree.setTicked(explorerNode, selected);
    }

    /// @return The items ticked.
    public Set<ExplorerNode> getSelectedSet() {
        return explorerTree.getTickedSet();
    }

    /// @param filterErrorConsumer Told why the server rejected the filter, or null when it was
    ///                            applied.
    public void setOnFilterError(final Consumer<String> filterErrorConsumer) {
        treeController.setOnFilterError(filterErrorConsumer);
    }
}
