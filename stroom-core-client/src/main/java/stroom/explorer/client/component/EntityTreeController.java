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
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.NodeFlag;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.security.shared.DocumentPermission;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.component.client.Controller;
import stroom.widget.quickfilter.client.QuickFilterTooltipUtil;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;

/// Fills a folder or document picker in a form ([EntityTree]) that a template declares, and
/// filters it as the user types in its quick filter:
///
/// ```java
/// folders = make(entityTreeControllerProvider).attach(view.getFolders());
/// folders.setIncludedTypes(DocumentTypes.FOLDER_TYPES);
/// ```
public class EntityTreeController extends Controller {

    private final Provider<ExplorerTreeController> explorerTreeControllerProvider;
    private final UiConfigCache uiConfigCache;
    private final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory;
    private EntityTree view;
    private ExplorerTreeController treeController;

    /// Made by GIN; [#attach(EntityTree)] gives it its picker.
    ///
    /// @param eventBus                         The application's event bus.
    /// @param explorerTreeControllerProvider   Makes what fills the tree.
    /// @param uiConfigCache                    Gives the quick filter's help link.
    /// @param quickFilterContextHandlerFactory Makes what the quick filter's context menu offers.
    @Inject
    public EntityTreeController(final EventBus eventBus,
                                final Provider<ExplorerTreeController> explorerTreeControllerProvider,
                                final UiConfigCache uiConfigCache,
                                final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory) {
        super(eventBus);
        this.explorerTreeControllerProvider = explorerTreeControllerProvider;
        this.uiConfigCache = uiConfigCache;
        this.quickFilterContextHandlerFactory = quickFilterContextHandlerFactory;
    }

    /// Attaches this to the picker it fills.
    ///
    /// @param view The picker, e.g. one a template declares.
    /// @return This, to configure.
    public EntityTreeController attach(final EntityTree view) {
        this.view = Objects.requireNonNull(view, "view");
        // Debatable whether we want to show alerts or not (it doesn't)
        treeController = make(explorerTreeControllerProvider).attach(view.getTree());
        treeController.setOnFilterError(view::setFilterError);
        view.setOnFilterChange(treeController::changeNameFilter);
        view.setFilterContextHandler(quickFilterContextHandlerFactory.create(
                ExplorerTreeFilter.QUICK_FILTER_CONTEXT, this, () -> taskMonitor().createTaskMonitor()));

        // Same field defs as the Explorer Tree
        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null && !isDisposed()) {
                view.setFilterHelp(() -> QuickFilterTooltipUtil.createTooltip(
                        "Choose Item Quick Filter",
                        ExplorerTreeFilter.FIELD_DEFINITIONS,
                        uiConfig.getHelpUrlQuickFilter()));
            }
        });
        return this;
    }

    /// Puts the focus on the picker's quick filter.
    public void focus() {
        view.focus();
    }

    /// @param types The types of document offered.
    public void setIncludedTypes(final String... types) {
        treeController.setIncludedTypes(types);
    }

    /// @param types The types of the tree's roots.
    public void setIncludedRootTypes(final String... types) {
        treeController.setIncludedRootTypes(types);
    }

    /// @param tags The tags a document must have to be offered.
    public void setTags(final String... tags) {
        treeController.setTags(tags);
    }

    /// @param nodeFlags The flags of the explorer nodes offered.
    public void setNodeFlags(final NodeFlag... nodeFlags) {
        treeController.setNodeFlags(nodeFlags);
    }

    /// @param requiredPermissions The permissions the user must have on the chosen item.
    public void setRequiredPermissions(final DocumentPermission... requiredPermissions) {
        treeController.setRequiredPermissions(requiredPermissions);
    }

    /// @return What fills the tree, e.g. to show an item in it.
    public ExplorerTreeController getTreeController() {
        return treeController;
    }

    /// @return The item chosen, or null.
    public ExplorerNode getSelectedItem() {
        return view.getTree().getSelectionModel().getSelected();
    }

    /// @param selection The item to choose, or null for none.
    public void setSelectedItem(final ExplorerNode selection) {
        view.getTree().getSelectionModel().setSelected(selection);
    }

    /// @param selectParentIfNotFound Whether the parent of an item chosen that can't be found is
    ///                               chosen instead.
    public void setSelectParentIfNotFound(final boolean selectParentIfNotFound) {
        treeController.setSelectParentIfNotFound(selectParentIfNotFound);
    }

}
