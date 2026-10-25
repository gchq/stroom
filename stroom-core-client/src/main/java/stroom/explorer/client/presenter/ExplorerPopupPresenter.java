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

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.ShowCreateDocumentDialogEvent;
import stroom.explorer.client.component.ExplorerTree;
import stroom.explorer.client.component.ExplorerTreeController;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.NodeFlag;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.security.shared.DocumentPermission;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.widget.dropdowntree.client.view.ExplorerPopupUiHandlers;
import stroom.widget.dropdowntree.client.view.ExplorerPopupView;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.util.client.SelectionType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ExplorerPopupPresenter
        extends MyPresenterWidget<ExplorerPopupView>
        implements ExplorerPopupUiHandlers {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    private final ExplorerTree explorerTree;
    private final ExplorerTreeController treeController;
    private final RestFactory restFactory;
    private boolean allowFolderSelection;
    private String caption = "Choose item";
    private List<String> includedTypes;
    private String initialQuickFilter;
    private Consumer<ExplorerNode> selectionChangeConsumer = e -> {

    };

    @Inject
    ExplorerPopupPresenter(final EventBus eventBus,
                           final ExplorerPopupView view,
                           final RestFactory restFactory,
                           final Provider<ExplorerTreeController> explorerTreeControllerProvider,
                           final UiConfigCache uiConfigCache,
                           final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory) {
        super(eventBus, view);
        getView().setAccessibleName("Filter documents");
        this.restFactory = restFactory;

        getView().setUiHandlers(this);

        explorerTree = new ExplorerTree();
        treeController = make(explorerTreeControllerProvider).attach(explorerTree);
        treeController.setTaskMonitorFactory(this);
        treeController.setIncludedRootTypes(ExplorerConstants.SYSTEM_TYPE);
        // The popup takes what the tree chooses, as it is chosen and as the tree chooses it
        explorerTree.setOnChoose((row, selectionType) -> setSelectedTreeItem(resolve(row), selectionType, false));
        explorerTree.setOnInitialSelect(selection ->
                setSelectedTreeItem(resolve(selection), new SelectionType(), true));
        // A picker's keys: Down in the filter goes to the chosen document, Up from the first item
        // back to the filter, and typing in the tree goes to the filter; moving chooses
        explorerTree.setAccessibleName("Documents");
        explorerTree.setSelectionFollowsFocus(true);
        explorerTree.setFilterKeys(getView()::focus, getView()::typeInFilter);
        getView().setFilterMoveDown(explorerTree::focusSelected);
        treeController.setOnFilterError(getView()::setFilterError);
        getView().setQuickFilterContextHandler(quickFilterContextHandlerFactory.create(
                ExplorerTreeFilter.QUICK_FILTER_CONTEXT, this, this));
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

    public void show(final BiConsumer<DocRef, HidePopupRequestEvent> consumer) {
        show(consumer, PopupType.OK_CANCEL_DIALOG);
    }

    public void show(final BiConsumer<DocRef, HidePopupRequestEvent> consumer, final PopupType popupType) {
        // What was chosen when it opened, put back if it is cancelled (moving through the tree
        // chooses)
        final ExplorerNode initialSelection = explorerTree.getSelectionModel().getSelected();
        refresh();
        final PopupSize popupSize = PopupSize.resizable(500, 550);

        ShowPopupEvent.builder(this)
                .popupType(popupType)
                .popupSize(popupSize)
                .caption(caption)
                // Modal, as it asks for a choice: the page behind can't be used, and Tab stays in it
                .modal(true)
                .onShow(e -> {
                    getView().focus();
                })
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final ExplorerNode selected = getSelectedEntityData();
                        if (isSelectionAllowed(selected)) {
                            selectionChangeConsumer.accept(selected);
                            explorerTree.getSelectionModel().setSelected(selected);
                            consumer.accept(NullSafe.get(selected, ExplorerNode::getDocRef), e);
                        } else {
                            AlertEvent.fireError(ExplorerPopupPresenter.this,
                                    "You must choose a valid item.", e::reset);
                        }
                    } else if (e.isCreate()) {
                        e.hide();

                        if (includedTypes != null && includedTypes.size() == 1) {
                            final String documentType = includedTypes.get(0);
                            ShowCreateDocumentDialogEvent.fire(
                                    this,
                                    "New " + documentType,
                                    null,
                                    documentType,
                                    "",
                                    true,
                                    explorerNode -> {
                                        consumer.accept(explorerNode.getDocRef(), e);
                                    });
                        }
                    } else {
                        restoreSelection(initialSelection);
                        e.hide();
                    }
                })
                .fire();
    }

    private void restoreSelection(final ExplorerNode selection) {
        if (selection == null) {
            explorerTree.getSelectionModel().clear();
        } else {
            explorerTree.getSelectionModel().setSelected(selection);
        }
    }

    public void show(final Consumer<DocRef> consumer) {
        show(consumer, PopupType.OK_CANCEL_DIALOG);
    }

    public void show(final Consumer<DocRef> consumer, final PopupType popupType) {
        show((docRef, e) -> {
            try {
                consumer.accept(docRef);
            } catch (final RuntimeException ex) {
                AlertEvent.fireErrorFromException(ExplorerPopupPresenter.this, ex, null);
            } finally {
                e.hide();
            }
        }, popupType);
    }

    protected void setIncludeNullSelection(final boolean includeNullSelection) {
        treeController.setIncludeNullSelection(includeNullSelection);
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
                // Tabbing into the tree lands on the chosen document, once its rows are drawn
                Scheduler.get().scheduleDeferred(explorerTree::showSelectedAsTabStop);

            } else if (selectionType.isDoubleSelect()) {
                selectionChangeConsumer.accept(selected);
                explorerTree.getSelectionModel().setSelected(selected);
                HidePopupRequestEvent.builder(this).fire();
            } else {
                getView().announce(NullSafe.getOrElse(selected, ExplorerNode::getName, "None") + " chosen");
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
//        GWT.log("nameFilterChanged: " + text + ", selected: " + explorerTree.getSelectionModel().getSelected());
        treeController.changeNameFilter(text);
    }

    public void refresh() {
        // Refresh gets called on show so no point doing it before then
        getView().setQuickFilter(initialQuickFilter);
        treeController.setInitialNameFilter(initialQuickFilter);
        treeController.reset(initialQuickFilter);
        treeController.setEnsureVisible(explorerTree.getSelectionModel().getSelected());
        treeController.refresh();
    }

    public void setIncludedTypes(final String... includedTypes) {
        this.includedTypes = Arrays.asList(includedTypes);
        treeController.setIncludedTypes(includedTypes);
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

    public DocRef getSelectedEntityReference() {
        return NullSafe.get(getSelectedEntityData(), ExplorerNode::getDocRef);
    }

    public void setSelectedEntityReference(final DocRef docRef) {
        setSelectedEntityReference(docRef, null);
    }

    public void setSelectedEntityReference(final DocRef docRef, final Runnable onSetSelected) {
        if (docRef != null) {
            restFactory
                    .create(EXPLORER_RESOURCE)
                    .method(res -> res.getFromDocRef(docRef))
                    .onSuccess(explorerNode -> {
                        setSelectedEntityData(explorerNode);
                        NullSafe.run(onSetSelected);
                    })
                    .taskMonitorFactory(this)
                    .exec();
        } else {
            clearSelected();
            NullSafe.run(onSetSelected);
        }
    }

    private ExplorerNode getSelectedEntityData() {
        return resolve(explorerTree.getSelectionModel().getSelected());
    }

    private void clearSelected() {
        explorerTree.getSelectionModel().clear();
    }

    private void setSelectedEntityData(final ExplorerNode explorerNode) {
        explorerTree.getSelectionModel().setSelected(explorerNode);
        selectionChangeConsumer.accept(explorerNode);
    }

    public void setAllowFolderSelection(final boolean allowFolderSelection) {
        this.allowFolderSelection = allowFolderSelection;
    }

    private static ExplorerNode resolve(final ExplorerNode selection) {
        if (ExplorerTree.NULL_SELECTION.equals(selection)) {
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
}
