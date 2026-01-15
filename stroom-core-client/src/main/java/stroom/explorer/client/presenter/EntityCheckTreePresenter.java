/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.dispatch.client.RestFactory;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.NodeFlag;
import stroom.security.shared.DocumentPermission;

import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.google.web.bindery.event.shared.SimpleEventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Set;

public class EntityCheckTreePresenter extends MyPresenterWidget<EntityCheckTreePresenter.EntityCheckTreeView>
        implements HasDataSelectionHandlers<Set<ExplorerNode>>, Focus {

    private final ExplorerTickBoxTree explorerTree;

    @Inject
    public EntityCheckTreePresenter(final EntityCheckTreeView view,
                                    final RestFactory restFactory) {
        super(new SimpleEventBus(), view);

        explorerTree = new ExplorerTickBoxTree(restFactory, this);

        view.setCellTree(explorerTree);
    }

    @Override
    public void focus() {
        explorerTree.setFocus(true);
    }

    public void setIncludedTypes(final String... includedTypes) {
        explorerTree.getTreeModel().setIncludedTypes(includedTypes);
    }

    public void setIncludedTypeSet(final Set<String> types) {
        explorerTree.getTreeModel().setIncludedTypeSet(types);
        refresh();
    }

    public void setIncludedRootTypes(final String... types) {
        explorerTree.getTreeModel().setIncludedRootTypes(types);
    }

    public void changeNameFilter(final String name) {
        explorerTree.getTreeModel().changeNameFilter(name);
        refresh();
    }

    public void setTags(final String... tags) {
        explorerTree.getTreeModel().setTags(tags);
    }

    public void setNodeFlags(final NodeFlag... nodeFlags) {
        explorerTree.getTreeModel().setNodeFlags(nodeFlags);
    }

    public void setRequiredPermissions(final DocumentPermission... requiredPermissions) {
        explorerTree.getTreeModel().setRequiredPermissions(requiredPermissions);
    }

    public void refresh() {
        explorerTree.getTreeModel().refresh();
    }

    public ExplorerTreeModel getTreeModel() {
        return explorerTree.getTreeModel();
    }

    public void setSelected(final ExplorerNode explorerNode, final boolean selected) {
        explorerTree.setSelected(explorerNode, selected);
    }

    public Set<ExplorerNode> getSelectedSet() {
        return explorerTree.getSelectedSet();
    }

    @Override
    public HandlerRegistration addDataSelectionHandler(final DataSelectionHandler<Set<ExplorerNode>> handler) {
        return addHandlerToSource(DataSelectionEvent.getType(), handler);
    }


    // --------------------------------------------------------------------------------


    public interface EntityCheckTreeView extends View {

        void setCellTree(Widget cellTree);
    }
}
