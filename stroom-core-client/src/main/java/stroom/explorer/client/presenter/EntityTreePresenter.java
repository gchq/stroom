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

import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.explorer.shared.ExplorerData;
import stroom.widget.util.client.MySingleSelectionModel;

public class EntityTreePresenter extends MyPresenterWidget<EntityTreePresenter.EntityTreeView>
        implements EntityTreeUiHandlers {
    public interface EntityTreeView extends View, HasUiHandlers<EntityTreeUiHandlers> {
        void setCellTree(Widget cellTree);
    }

    private final ExplorerTree explorerTree;

    @Inject
    public EntityTreePresenter(final EventBus eventBus, final EntityTreeView view,
                               final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        view.setUiHandlers(this);

        explorerTree = new ExplorerTree(dispatcher);

        // Add views.
        view.setCellTree(explorerTree);
    }

    @Override
    public void changeFilter(final String filter) {
        explorerTree.changeNameFilter(filter);
    }

    public void setIncludedTypes(final String... types) {
        explorerTree.getTreeModel().setIncludedTypes(types);
    }

    public void setTags(final String... tags) {
        explorerTree.getTreeModel().setTags(tags);
    }

    public void setRequiredPermissions(final String... requiredPermissions) {
        explorerTree.getTreeModel().setRequiredPermissions(requiredPermissions);
    }

    public ExplorerTreeModel getModel() {
        return explorerTree.getTreeModel();
    }

    public MySingleSelectionModel<ExplorerData> getSelectionModel() {
        return explorerTree.getSelectionModel();
    }
}
