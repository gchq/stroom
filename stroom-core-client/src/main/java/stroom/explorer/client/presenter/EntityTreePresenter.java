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

import stroom.dispatch.client.RestFactory;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.NodeFlag;
import stroom.security.shared.DocumentPermission;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Supplier;

public class EntityTreePresenter
        extends MyPresenterWidget<EntityTreePresenter.EntityTreeView>
        implements EntityTreeUiHandlers, Focus {

    private final ExplorerTree explorerTree;

    @Inject
    public EntityTreePresenter(final EventBus eventBus,
                               final EntityTreeView view,
                               final RestFactory restFactory,
                               final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        view.setUiHandlers(this);

        // Debatable whether we want to show alerts or not
        explorerTree = new ExplorerTree(
                restFactory,
                this,
                false,
                false);

        // Add views.
        view.setCellTree(explorerTree);

        // Same field defs as the Explorer Tree
        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                view.registerPopupTextProvider(() -> QuickFilterTooltipUtil.createTooltip(
                        "Choose Item Quick Filter",
                        ExplorerTreeFilter.FIELD_DEFINITIONS,
                        uiConfig.getHelpUrlQuickFilter()));
            }
        }, this);
    }

    @Override
    public void focus() {
        getView().focus();
    }

    @Override
    public void changeFilter(final String filter) {
        explorerTree.changeNameFilter(filter);
    }

    public void setIncludedTypes(final String... types) {
        explorerTree.getTreeModel().setIncludedTypes(types);
    }

    public void setIncludedRootTypes(final String... types) {
        explorerTree.getTreeModel().setIncludedRootTypes(types);
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

    public ExplorerTreeModel getModel() {
        return explorerTree.getTreeModel();
    }

    public ExplorerNode getSelectedItem() {
        return explorerTree.getSelectionModel().getSelected();
    }

    public void setSelectedItem(final ExplorerNode selection) {
        explorerTree.getSelectionModel().setSelected(selection);
    }

    public void setSelectParentIfNotFound(final boolean selectParentIfNotFound) {
        explorerTree.getTreeModel().setSelectParentIfNotFound(selectParentIfNotFound);
    }


    // --------------------------------------------------------------------------------


    public interface EntityTreeView extends View, Focus, HasUiHandlers<EntityTreeUiHandlers> {

        void setCellTree(Widget cellTree);

        void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier);
    }
}
