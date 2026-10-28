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

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.ShowCreateDocumentDialogEvent;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.NodeFlag;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.security.shared.DocumentPermission;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.DialogAction;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.quickfilter.client.QuickFilterTooltipUtil;
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

/// The explorer's chooser ([ExplorerPopup]): a dialog that offers documents in the explorer's tree,
/// filtered by type, tag and permission, for the user to choose one.
///
/// A document picker's controller ([DocSelectionController]) makes one and shows it again and
/// again with [#show(Consumer)]; anything else makes one for one use and shows it with
/// [#showOnce(HasControllerNode, Consumer)], usually without "None" to choose:
///
/// ```java
/// final ExplorerPopupController chooser = chooserProvider.get();
/// chooser.setIncludeNullSelection(false);
/// chooser.setCaption("Choose Dictionary");
/// chooser.setIncludedTypes(DictionaryDoc.TYPE);
/// chooser.showOnce(this, docRef -> ...);
/// ```
public class ExplorerPopupController extends Controller {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    private final ExplorerPopup view;
    private final ExplorerTree explorerTree;
    private final ExplorerTreeController treeController;
    private final RestFactory restFactory;
    private boolean allowFolderSelection;
    private String caption = "Choose item";
    private List<String> includedTypes;
    private String initialQuickFilter;
    private Consumer<ExplorerNode> selectionChangeConsumer = e -> {
    };

    /// Made by GIN, with the chooser it shows.
    ///
    /// @param eventBus                         The application's event bus, which the dialog is
    ///                                         shown on.
    /// @param view                             The chooser.
    /// @param restFactory                      Looks up a document chosen by its reference.
    /// @param explorerTreeControllerProvider   Makes what fills the tree.
    /// @param uiConfigCache                    Gives the quick filter's help link.
    /// @param quickFilterContextHandlerFactory Makes what the quick filter's context menu offers.
    @Inject
    public ExplorerPopupController(final EventBus eventBus,
                                   final ExplorerPopup view,
                                   final RestFactory restFactory,
                                   final Provider<ExplorerTreeController> explorerTreeControllerProvider,
                                   final UiConfigCache uiConfigCache,
                                   final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        initWidget(view);

        explorerTree = view.getTree();
        treeController = make(explorerTreeControllerProvider).attach(explorerTree);
        treeController.setIncludedRootTypes(ExplorerConstants.SYSTEM_TYPE);
        // The popup takes what the tree chooses, as it is chosen and as the tree chooses it
        explorerTree.setOnChoose((row, selectionType) -> setSelectedTreeItem(resolve(row), selectionType, false));
        explorerTree.setOnInitialSelect(selection ->
                setSelectedTreeItem(resolve(selection), new SelectionType(), true));
        // A picker's keys: Down in the filter goes to the chosen document, Up from the first item
        // back to the filter, and typing in the tree goes to the filter; moving chooses
        explorerTree.setSelectionFollowsFocus(true);
        explorerTree.setFilterKeys(view::focus, view::typeInFilter);
        view.setOnFilterDown(explorerTree::focusSelected);
        view.setOnFilterChange(treeController::changeNameFilter);
        treeController.setOnFilterError(view::setFilterError);
        view.setFilterContextHandler(quickFilterContextHandlerFactory.create(
                ExplorerTreeFilter.QUICK_FILTER_CONTEXT, this, () -> taskMonitor().createTaskMonitor()));
        setIncludeNullSelection(true);

        uiConfigCache.get(uiConfig -> {
            // Closed (e.g. a one-use chooser) before the config came
            if (uiConfig != null && !isDisposed()) {
                view.setFilterHelp(() -> QuickFilterTooltipUtil.createTooltip(
                        "Choose Item Quick Filter",
                        ExplorerTreeFilter.FIELD_DEFINITIONS,
                        uiConfig.getHelpUrlQuickFilter()));
            }
        });
    }

    /// @param selectionChangeConsumer Told the document chosen, when it is set and when the user
    ///                                chooses it.
    public void setOnSelectionChange(final Consumer<ExplorerNode> selectionChangeConsumer) {
        this.selectionChangeConsumer = selectionChangeConsumer;
    }

    /// @return The item chosen in the tree, or null.
    public ExplorerNode getCurrentSelection() {
        return explorerTree.getSelectionModel().getSelected();
    }

    /// Shows the chooser, which stays with what owns this when it is closed, to be shown again.
    ///
    /// @param consumer Given the document chosen, or null for none, when OK is pressed; the
    ///                 chooser then closes.
    public void show(final Consumer<DocRef> consumer) {
        show(ControllerDialog.show(this), hideAfter(consumer), PopupType.OK_CANCEL_DIALOG);
    }

    /// Shows the chooser for one use, owned by `opener` until it is closed.
    ///
    /// @param opener   What shows it.
    /// @param consumer Given the document chosen, or null for none, when OK is pressed; the
    ///                 chooser then closes.
    public void showOnce(final HasControllerNode opener, final Consumer<DocRef> consumer) {
        showOnce(opener, consumer, PopupType.OK_CANCEL_DIALOG);
    }

    /// As [#showOnce(HasControllerNode, Consumer)], as a dialog of a given type, e.g. one with a
    /// button to create a document.
    ///
    /// @param opener    What shows it.
    /// @param consumer  Given the document chosen, or null for none; the chooser then closes.
    /// @param popupType The dialog's type, e.g. [PopupType#CREATE_OK_CANCEL_DIALOG].
    public void showOnce(final HasControllerNode opener,
                         final Consumer<DocRef> consumer,
                         final PopupType popupType) {
        show(ControllerDialog.showOnce(opener, this), hideAfter(consumer), popupType);
    }

    /// As [#showOnce(HasControllerNode, Consumer)], for a caller that closes the chooser itself,
    /// e.g. once it has checked what was chosen, with `e.hide()`, or keeps it open with
    /// `e.reset()`.
    ///
    /// @param opener   What shows it.
    /// @param consumer Given the document chosen, or null for none, and the request to close.
    public void showOnce(final HasControllerNode opener,
                         final BiConsumer<DocRef, HidePopupRequestEvent> consumer) {
        show(ControllerDialog.showOnce(opener, this), consumer, PopupType.OK_CANCEL_DIALOG);
    }

    /// @param includeNullSelection Whether "None" is offered, to choose no document.
    public void setIncludeNullSelection(final boolean includeNullSelection) {
        treeController.setIncludeNullSelection(includeNullSelection);
    }

    /// Fetches the tree again, with the quick filter the chooser opens with.
    public void refresh() {
        // Refresh gets called on show so no point doing it before then
        view.setFilter(initialQuickFilter);
        treeController.setInitialNameFilter(initialQuickFilter);
        treeController.reset(initialQuickFilter);
        treeController.setEnsureVisible(explorerTree.getSelectionModel().getSelected());
        treeController.refresh();
    }

    /// @param includedTypes The types of document that may be chosen.
    public void setIncludedTypes(final String... includedTypes) {
        this.includedTypes = Arrays.asList(includedTypes);
        treeController.setIncludedTypes(includedTypes);
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

    /// @param requiredPermissions The permissions the user must have on the chosen document.
    public void setRequiredPermissions(final DocumentPermission... requiredPermissions) {
        treeController.setRequiredPermissions(requiredPermissions);
    }

    /// @return The document chosen, or null.
    public DocRef getSelectedEntityReference() {
        return NullSafe.get(getSelectedEntityData(), ExplorerNode::getDocRef);
    }

    /// Chooses a document, looking it up in the explorer.
    ///
    /// @param docRef The document, or null for none.
    public void setSelectedEntityReference(final DocRef docRef) {
        setSelectedEntityReference(docRef, null);
    }

    /// Chooses a document, looking it up in the explorer.
    ///
    /// @param docRef        The document, or null for none.
    /// @param onSetSelected Run once it is chosen, or null.
    public void setSelectedEntityReference(final DocRef docRef, final Runnable onSetSelected) {
        if (docRef != null) {
            restFactory
                    .create(EXPLORER_RESOURCE)
                    .method(res -> res.getFromDocRef(docRef))
                    .onSuccess(explorerNode -> {
                        setSelectedEntityData(explorerNode);
                        NullSafe.run(onSetSelected);
                    })
                    .owner(this)
                    .taskMonitorFactory(taskMonitor())
                    .exec();
        } else {
            clearSelected();
            NullSafe.run(onSetSelected);
        }
    }

    /// Chooses a document already looked up in the explorer.
    ///
    /// @param explorerNode The document's explorer node, or null for none.
    public void setSelectedNode(final ExplorerNode explorerNode) {
        if (explorerNode != null) {
            setSelectedEntityData(explorerNode);
        } else {
            clearSelected();
        }
    }

    /// @param allowFolderSelection Whether a folder may be chosen.
    public void setAllowFolderSelection(final boolean allowFolderSelection) {
        this.allowFolderSelection = allowFolderSelection;
    }

    /// @param caption The dialog's caption, by convention "Choose <Thing>" in title case; it
    ///                defaults to "Choose item".
    public void setCaption(final String caption) {
        this.caption = caption;
    }

    /// @param initialQuickFilter The quick filter the chooser opens with.
    public void setInitialQuickFilter(final String initialQuickFilter) {
        this.initialQuickFilter = initialQuickFilter;
    }

    private void show(final ShowPopupEvent.Builder builder,
                      final BiConsumer<DocRef, HidePopupRequestEvent> consumer,
                      final PopupType popupType) {
        // What was chosen when it opened, put back if it is cancelled (moving through the tree
        // chooses)
        final ExplorerNode initialSelection = explorerTree.getSelectionModel().getSelected();
        refresh();
        final PopupSize popupSize = PopupSize.resizable(500, 550);

        builder.popupType(popupType)
                .popupSize(popupSize)
                .caption(caption)
                // Modal, as it asks for a choice: the page behind can't be used, and Tab stays in it
                .modal(true)
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final ExplorerNode selected = getSelectedEntityData();
                        if (isSelectionAllowed(selected)) {
                            selectionChangeConsumer.accept(selected);
                            explorerTree.getSelectionModel().setSelected(selected);
                            consumer.accept(NullSafe.get(selected, ExplorerNode::getDocRef), e);
                        } else {
                            AlertEvent.fireError(this, "You must choose a valid item.", e::reset);
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
                                    explorerNode -> consumer.accept(explorerNode.getDocRef(), e));
                        }
                    } else {
                        restoreSelection(initialSelection);
                        e.hide();
                    }
                })
                .fire();
    }

    // Gives the consumer what was chosen, alerting if it fails, then closes the chooser
    private BiConsumer<DocRef, HidePopupRequestEvent> hideAfter(final Consumer<DocRef> consumer) {
        return (docRef, e) -> {
            try {
                consumer.accept(docRef);
            } catch (final RuntimeException ex) {
                AlertEvent.fireErrorFromException(this, ex, null);
            } finally {
                e.hide();
            }
        };
    }

    private void restoreSelection(final ExplorerNode selection) {
        if (selection == null) {
            explorerTree.getSelectionModel().clear();
        } else {
            explorerTree.getSelectionModel().setSelected(selection);
        }
    }

    private void setSelectedTreeItem(final ExplorerNode selected,
                                     final SelectionType selectionType,
                                     final boolean initial) {
        // Is the selection type valid?
        if (isSelectionAllowed(selected)) {
            // Drop-down pickers need to know what the initial selection was so that they can
            // update the name of their selected item properly.
            if (initial) {
                selectionChangeConsumer.accept(selected);
                // Tabbing into the tree lands on the chosen document, once its rows are drawn
                Scheduler.get().scheduleDeferred(explorerTree::showSelectedAsTabStop);

            } else if (selectionType.isDoubleSelect()) {
                selectionChangeConsumer.accept(selected);
                explorerTree.getSelectionModel().setSelected(selected);
                ControllerDialog.pressAction(this, DialogAction.OK);
            } else {
                view.announce(NullSafe.getOrElse(selected, ExplorerNode::getName, "None") + " chosen");
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


    private static ExplorerNode resolve(final ExplorerNode selection) {
        if (ExplorerTree.NULL_SELECTION.equals(selection)) {
            return null;
        }

        return selection;
    }
}
