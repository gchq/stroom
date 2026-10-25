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
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.NodeFlag;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.security.shared.DocumentPermission;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.dropdowntree.client.view.QuickFilterContextHandler;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.presenter.client.HasUiHandlers;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Supplier;

public class EntityTreePresenter
        extends MyPresenterWidget<EntityTreePresenter.EntityTreeView>
        implements EntityTreeUiHandlers, Focus {

    private final ExplorerTree explorerTree;
    private final ExplorerTreeController treeController;

    @Inject
    public EntityTreePresenter(final EventBus eventBus,
                               final EntityTreeView view,
                               final Provider<ExplorerTreeController> explorerTreeControllerProvider,
                               final UiConfigCache uiConfigCache,
                               final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory) {
        super(eventBus, view);
        view.setUiHandlers(this);

        // Debatable whether we want to show alerts or not (it doesn't)
        explorerTree = new ExplorerTree();
        treeController = make(explorerTreeControllerProvider).attach(explorerTree);
        treeController.setTaskMonitorFactory(this);

        // Add views.
        view.setCellTree(explorerTree);
        treeController.setOnFilterError(view::setFilterError);
        view.setQuickFilterContextHandler(quickFilterContextHandlerFactory.create(
                ExplorerTreeFilter.QUICK_FILTER_CONTEXT, this, this));

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
        treeController.changeNameFilter(filter);
    }

    public void setIncludedTypes(final String... types) {
        treeController.setIncludedTypes(types);
    }

    public void setIncludedRootTypes(final String... types) {
        treeController.setIncludedRootTypes(types);
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

    /// @return What fills the tree, e.g. to show an item in it.
    public ExplorerTreeController getTreeController() {
        return treeController;
    }

    public ExplorerNode getSelectedItem() {
        return explorerTree.getSelectionModel().getSelected();
    }

    public void setSelectedItem(final ExplorerNode selection) {
        explorerTree.getSelectionModel().setSelected(selection);
    }

    public void setSelectParentIfNotFound(final boolean selectParentIfNotFound) {
        treeController.setSelectParentIfNotFound(selectParentIfNotFound);
    }


    // --------------------------------------------------------------------------------


    public interface EntityTreeView extends View, Focus, HasUiHandlers<EntityTreeUiHandlers> {

        /**
         * Show why the server rejected the filter, or clear it when passed null.
         * See {@code FetchExplorerNodeResult.filterError}.
         */
        void setFilterError(String filterError);

        void setQuickFilterContextHandler(QuickFilterContextHandler contextHandler);

        void setCellTree(Widget cellTree);

        void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier);
    }
}
