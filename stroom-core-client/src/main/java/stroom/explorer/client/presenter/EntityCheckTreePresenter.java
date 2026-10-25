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

package stroom.explorer.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.explorer.client.component.ExplorerTree;
import stroom.explorer.client.component.ExplorerTreeController;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.NodeFlag;
import stroom.security.shared.DocumentPermission;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;

import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.google.web.bindery.event.shared.SimpleEventBus;

import java.util.Set;

public class EntityCheckTreePresenter extends MyPresenterWidget<EntityCheckTreePresenter.EntityCheckTreeView>
        implements Focus {

    private final ExplorerTree explorerTree;
    private final ExplorerTreeController treeController;

    @Inject
    public EntityCheckTreePresenter(final EntityCheckTreeView view,
                                    final Provider<ExplorerTreeController> explorerTreeControllerProvider) {
        super(new SimpleEventBus(), view);

        // A tick tree, showing alerts
        explorerTree = new ExplorerTree();
        explorerTree.setTickBoxes(true);
        treeController = make(explorerTreeControllerProvider).attach(explorerTree);
        treeController.setTaskMonitorFactory(this);
        treeController.setShowAlerts(true);
        view.setCellTree(explorerTree);
    }

    @Override
    public void focus() {
        explorerTree.setFocus(true);
    }

    public void setIncludedTypes(final String... includedTypes) {
        treeController.setIncludedTypes(includedTypes);
    }

    public void setIncludedTypeSet(final Set<String> types) {
        treeController.setIncludedTypeSet(types);
        refresh();
    }

    public void setIncludedRootTypes(final String... types) {
        treeController.setIncludedRootTypes(types);
    }

    public void changeNameFilter(final String name) {
        treeController.changeNameFilter(name);
        refresh();
    }

    public void setTags(final String... tags) {
        treeController.setTags(tags);
    }

    public void setNodeFlags(final NodeFlag... nodeFlags) {
        treeController.setNodeFlags(nodeFlags);
    }

    public void setRequiredPermissions(final DocumentPermission... requiredPermissions) {
        treeController.setRequiredPermissions(requiredPermissions);
    }

    public void refresh() {
        treeController.refresh();
    }

    /// @return What fills the tree, e.g. to show items in it.
    public ExplorerTreeController getTreeController() {
        return treeController;
    }

    public void setSelected(final ExplorerNode explorerNode, final boolean selected) {
        explorerTree.setTicked(explorerNode, selected);
    }

    public Set<ExplorerNode> getSelectedSet() {
        return explorerTree.getTickedSet();
    }



    // --------------------------------------------------------------------------------


    /**
     * Receives the reason the server rejected the current filter, or null when it was applied.
     * See {@code FetchExplorerNodeResult.filterError}.
     */
    public void setFilterErrorConsumer(final java.util.function.Consumer<String> filterErrorConsumer) {
        treeController.setOnFilterError(filterErrorConsumer);
    }

    public interface EntityCheckTreeView extends View {

        void setCellTree(Widget cellTree);
    }
}
