/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.explorer.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.NodeFlag;
import stroom.security.shared.DocumentPermission;
import stroom.task.client.TaskHandlerFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.dropdowntree.client.view.ExplorerPopupUiHandlers;
import stroom.widget.dropdowntree.client.view.ExplorerPopupView;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.SelectionType;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;

public class ExplorerPopupPresenter
        extends MyPresenterWidget<ExplorerPopupView>
        implements ExplorerPopupUiHandlers {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    private final ExtendedExplorerTree explorerTree;
    private final RestFactory restFactory;
    private boolean allowFolderSelection;
    private String caption = "Choose item";
    private String initialQuickFilter;
    private ExplorerNode initialSelection;
    private Consumer<ExplorerNode> selectionChangeConsumer = e -> {

    };

    @Inject
    ExplorerPopupPresenter(final EventBus eventBus,
                           final ExplorerPopupView view,
                           final RestFactory restFactory,
                           final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        this.restFactory = restFactory;

        getView().setUiHandlers(this);

        explorerTree = new ExtendedExplorerTree(this, restFactory, this);
        setIncludeNullSelection(true);

        // Add views.
        view.setCellTree(explorerTree);

        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                view.setQuickFilterTooltipSupplier(() -> QuickFilterTooltipUtil.createTooltip(
                        "Choose Item Quick Filter",
                        ExplorerTreeFilter.FIELD_DEFINITIONS,
                        uiConfig.getHelpUrlQuickFilter()));
            }
        }, this);
    }

    public void setSelectionChangeConsumer(final Consumer<ExplorerNode> selectionChangeConsumer) {
        this.selectionChangeConsumer = selectionChangeConsumer;
    }

    public ExplorerNode getCurrentSelection() {
        return explorerTree.getSelectionModel().getSelected();
    }

    @Override
    protected void onHide() {
    }

    public void show(final Consumer<DocRef> consumer) {
        show(consumer, () -> {
        });
    }

    public void show(final Consumer<DocRef> consumer, final Runnable onShow) {
        initialSelection = getCurrentSelection();
        refresh();
        final PopupSize popupSize = PopupSize.resizable(500, 550);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption(caption)
                .onShow(e -> {
                    onShow.run();
                    getView().focus();
                })
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final ExplorerNode selected = getSelectedEntityData();
                        if (isSelectionAllowed(selected)) {
                            selectionChangeConsumer.accept(selected);
                            explorerTree.getSelectionModel().setSelected(selected);
                            consumer.accept(selected == null
                                    ? null
                                    : selected.getDocRef());
                            e.hide();
                        } else {
                            AlertEvent.fireError(ExplorerPopupPresenter.this,
                                    "You must choose a valid item.", e::reset);
                        }
                    } else {
                        final ExplorerNode selected = initialSelection;
                        if (isSelectionAllowed(selected)) {
                            selectionChangeConsumer.accept(selected);
                            explorerTree.getSelectionModel().setSelected(selected);
                            consumer.accept(selected == null
                                    ? null
                                    : selected.getDocRef());
                        }
                        e.hide();
                    }
                })
                .fire();
    }

    protected void setIncludeNullSelection(final boolean includeNullSelection) {
        explorerTree.getTreeModel().setIncludeNullSelection(includeNullSelection);
    }

    protected void setSelectedTreeItem(final ExplorerNode selected,
                                       final SelectionType selectionType,
                                       final boolean initial) {
        // Is the selection type valid?
        if (isSelectionAllowed(selected)) {
            // Drop down presenters need to know what the initial selection was so that they can
            // update the name of their selected item properly.
            if (initial) {
                selectionChangeConsumer.accept(selected);
                initialSelection = selected;

            } else if (selectionType.isDoubleSelect()) {
                selectionChangeConsumer.accept(selected);
                explorerTree.getSelectionModel().setSelected(selected);
                HidePopupRequestEvent.builder(this).fire();
            }
        }
    }

    private boolean isSelectionAllowed(final ExplorerNode selected) {
        if (selected == null) {
            return true;
        }
        if (allowFolderSelection) {
            return true;
        }

        return !DocumentTypes.isFolder(selected.getType());
    }

    @Override
    public void nameFilterChanged(final String text) {
//        GWT.log("nameFilterChanged: " + text);
        explorerTree.changeNameFilter(text);
    }

    public void refresh() {
        // Refresh gets called on show so no point doing it before then
        getView().setQuickFilter(initialQuickFilter);
        explorerTree.getTreeModel().setInitialNameFilter(initialQuickFilter);
        explorerTree.getTreeModel().reset(initialQuickFilter);
        explorerTree.getTreeModel().setEnsureVisible(explorerTree.getSelectionModel().getSelected());
        explorerTree.getTreeModel().refresh();
    }

    public void setIncludedTypes(final String... includedTypes) {
        explorerTree.getTreeModel().setIncludedTypes(includedTypes);
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

    public DocRef getSelectedEntityReference() {
        final ExplorerNode explorerNode = getSelectedEntityData();
        if (explorerNode == null) {
            return null;
        }
        return explorerNode.getDocRef();
    }

    public void setSelectedEntityReference(final DocRef docRef) {
        if (docRef != null) {
            restFactory
                    .create(EXPLORER_RESOURCE)
                    .method(res -> res.getFromDocRef(docRef))
                    .onSuccess(this::setSelectedEntityData)
                    .taskHandlerFactory(this)
                    .exec();
        }
    }

    private ExplorerNode getSelectedEntityData() {
        return resolve(explorerTree.getSelectionModel().getSelected());
    }

    private void setSelectedEntityData(final ExplorerNode explorerNode) {
        explorerTree.getSelectionModel().setSelected(explorerNode);
        selectionChangeConsumer.accept(explorerNode);
        refresh();
    }

    public void setAllowFolderSelection(final boolean allowFolderSelection) {
        this.allowFolderSelection = allowFolderSelection;
    }

    private static ExplorerNode resolve(final ExplorerNode selection) {
        if (ExplorerTreeModel.NULL_SELECTION.equals(selection)) {
            return null;
        }

        return selection;
    }

    public void setCaption(final String caption) {
        this.caption = caption;
    }

    public void setInitialQuickFilter(final String initialQuickFilter) {
        this.initialQuickFilter = initialQuickFilter;
    }


    // --------------------------------------------------------------------------------


    private static class ExtendedExplorerTree extends ExplorerTree {

        private final ExplorerPopupPresenter explorerDropDownTreePresenter;

        public ExtendedExplorerTree(final ExplorerPopupPresenter explorerDropDownTreePresenter,
                                    final RestFactory restFactory,
                                    final TaskHandlerFactory taskHandlerFactory) {
            super(restFactory, taskHandlerFactory, false, false);
            this.explorerDropDownTreePresenter = explorerDropDownTreePresenter;
            this.getTreeModel().setIncludedRootTypes(ExplorerConstants.SYSTEM);
        }

        @Override
        protected void setInitialSelectedItem(final ExplorerNode selection) {
            super.setInitialSelectedItem(selection);
            explorerDropDownTreePresenter.setSelectedTreeItem(resolve(selection),
                    new SelectionType(), true);
        }

        @Override
        protected void doSelect(final ExplorerNode row, final SelectionType selectionType) {
            super.doSelect(row, selectionType);
            explorerDropDownTreePresenter.setSelectedTreeItem(resolve(row), selectionType, false);
        }
    }
}
