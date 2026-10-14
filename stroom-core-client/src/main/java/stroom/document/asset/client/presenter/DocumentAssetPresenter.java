/*
 * Copyright 2025 Crown Copyright
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

package stroom.document.asset.client.presenter;

import stroom.alert.client.event.AlertCallback;
import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.AbstractDoc;
import stroom.document.asset.client.presenter.DocumentAssetAddItemDialogPresenter.DialogType;
import stroom.document.asset.client.presenter.DocumentAssetPresenter.DocumentAssetView;
import stroom.document.asset.client.presenter.assets.DocumentAssetImageResource;
import stroom.document.asset.client.presenter.assets.DocumentAssetTreeItem;
import stroom.document.asset.shared.DocumentAssetResource;
import stroom.document.asset.shared.DocumentAssetSaveAsParameters;
import stroom.document.asset.shared.DocumentAssetUpdateContent;
import stroom.document.asset.shared.DocumentAssetUpdateDelete;
import stroom.document.asset.shared.DocumentAssetUpdateNewFile;
import stroom.document.asset.shared.DocumentAssetUpdateRename;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.RefreshDocumentEvent;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.DocPresenter;
import stroom.entity.client.presenter.HasToolbar;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.util.client.Console;
import stroom.util.shared.ResourceKey;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.util.client.Rect;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Tree;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Provider;

/**
 * Shows the Assets - images, css etc - associated with the Visualisation.
 */
public class DocumentAssetPresenter<D extends AbstractDoc>
        extends DocPresenter<DocumentAssetView, D>
        implements HasToolbar, DocumentAssetAddFileCallback {

    /**
     * Illegal asset name characters - not allowed in any file or folder name
     */
    private static final String ILLEGAL_ASSET_NAME_CHARACTERS = "/:";

    /**
     * Servlet path - start of the URL for the asset as retrieved via the Servlet
     */
    static final String ASSET_SERVLET_PATH_PREFIX = "/assets/";

    /**
     * REST interface
     */
    private static final DocumentAssetResource DOCUMENT_ASSET_RESOURCE =
            GWT.create(DocumentAssetResource.class);

    /**
     * Rest factory to trigger storing file uploads
     */
    private final RestFactory restFactory;

    /**
     * Tree representing file assets and folders
     */
    private final Tree tree = new Tree(new AssetTreeResources(), true);

    /**
     * Button to revert any changes and go back to live version
     */
    private final InlineSvgButton revertButton = new InlineSvgButton();

    /**
     * Button to add stuff to the tree
     */
    private final InlineSvgButton addButton = new InlineSvgButton();

    /**
     * Button to delete stuff from the tree
     */
    private final InlineSvgButton deleteButton = new InlineSvgButton();

    /**
     * Button to edit stuff from the tree
     */
    private final InlineSvgButton editButton = new InlineSvgButton();

    /**
     * Button to view stuff
     */
    private final InlineSvgButton viewButton = new InlineSvgButton();

    /**
     * Dialog that appears when user wants to upload a file
     */
    private final DocumentAssetUploadFileDialogPresenter uploadFileDialog;

    /**
     * Dialog that appears when user wants to add a folder
     */
    private final DocumentAssetAddItemDialogPresenter addItemDialog;

    /**
     * Dialog that appears when user wants to edit the tree item
     */
    private final DocumentAssetEditAssetDialogPresenter editAssetDialog;

    /**
     * Editor widget
     */
    private final EditorPresenter editorPresenter;

    /**
     * Set of the node paths that are open when the document is saved
     */
    private final Set<String> treeItemPathToOpenState = new HashSet<>();

    /**
     * Items in the context menu
     */
    private final List<Item> menuItems = new ArrayList<>();

    /**
     * State of content edited in editor
     */
    private final DocumentAssetState assetDirtyState = new DocumentAssetState();

    /**
     * True if the server has pending draft asset changes that have not yet been saved to live,
     * as reported by the assets' isDirty() on the last fetch. Drives isDirty()
     * (and therefore the Save/revert buttons) via {@link #hasAssociatedDirty()}.
     */
    private boolean serverDirty;

    /**
     * Current document - may be null
     */
    private D document;

    /**
     * True if the UI is readonly, false if read-write
     */
    private boolean readOnly = false;

    @Inject
    public DocumentAssetPresenter(final EventBus eventBus,
                                        final DocumentAssetView view,
                                        final RestFactory restFactory,
                                        final DocumentAssetUploadFileDialogPresenter uploadFileDialog,
                                        final DocumentAssetAddItemDialogPresenter addItemDialog,
                                        final DocumentAssetEditAssetDialogPresenter editAssetDialog,
                                        final Provider<EditorPresenter> editorPresenterProvider) {
        super(eventBus, view);

        this.restFactory = restFactory;
        this.uploadFileDialog = uploadFileDialog;
        this.addItemDialog = addItemDialog;
        this.editAssetDialog = editAssetDialog;
        this.editorPresenter = editorPresenterProvider.get();

        tree.setStylePrimaryName("visualisation-asset-tree");
        tree.addSelectionHandler(event -> {
            DocumentAssetPresenter.this.onSelectionChange();
        });
        this.getView().setTreeAndEditor(tree, editorPresenter);
    }

    @Override
    protected boolean hasAssociatedDirty() {
        return serverDirty || assetDirtyState.isDirtyAndNeedsSaveToDraft();
    }

    /**
     * Callback from the Add dialog that adds a file that has been uploaded.
     *
     * @param parentFolderItem The node that the file has been added to.
     * @param fileName         The name of the file that was uploaded.
     * @param resourceKey      The resource key of the file, so that the server can find it later.
     */
    @Override
    public void addUploadedFile(final DocumentAssetTreeItem parentFolderItem,
                                final String fileName,
                                final ResourceKey resourceKey) {

        final String path = DocumentAssetPresenterUtils.getNewItemPath(parentFolderItem, fileName);
        DocumentAssetPresenterUtils.markOpenClosedStateOpen(parentFolderItem, treeItemPathToOpenState);
        doUpdateNewUploadedFile(path, resourceKey);
    }

    /**
     * Generates a label that doesn't clash with other files/folders in the same directory.
     * Adds an integer to the end, incrementing until an integer is found that doesn't
     * clash with anything else.
     *
     * @param assetParentItem The tree item that holds the directory.
     * @param label           The label that we're trying to put into the directory.
     * @param itemId          The ID of the item with this label. Can be null if this is a new item with no ID yet.
     * @return A label that doesn't clash with anything else.
     */
    @Override
    public String getNonClashingLabel(final DocumentAssetTreeItem assetParentItem,
                                      final String label,
                                      final String itemId) {
        String nonClashingLabel = label;

        if (assetParentItem != null) {
            int i = 1;
            while (assetParentItem.labelExists(nonClashingLabel, itemId)) {
                nonClashingLabel = DocumentAssetPresenterUtils.generateNonClashingLabel(label, i);
                i++;
            }
        } else {
            // Parent is the Tree itself
            int i = 1;
            while (DocumentAssetPresenterUtils.labelClashesInTreeRoot(tree, nonClashingLabel, itemId)) {
                nonClashingLabel = DocumentAssetPresenterUtils.generateNonClashingLabel(label, i);
                i++;
            }
        }

        return nonClashingLabel;
    }

    /**
     * Sets up the toolbar for the tab.
     */
    @Override
    public List<Widget> getToolbars() {
        revertButton.setSvg(SvgImage.REFRESH);
        revertButton.setTitle("Revert changes");
        revertButton.setVisible(true);
        revertButton.addClickHandler(event -> DocumentAssetPresenter.this.onRevertButtonClick());

        addButton.setSvg(SvgImage.ADD);
        addButton.setTitle("Add file");
        addButton.setVisible(true);

        deleteButton.setSvg(SvgImage.DELETE);
        deleteButton.setTitle("Delete");
        deleteButton.setVisible(true);
        deleteButton.addClickHandler(event -> DocumentAssetPresenter.this.onDeleteButtonClick());

        editButton.setSvg(SvgImage.EDIT);
        editButton.setTitle("Rename");
        editButton.setVisible(true);
        editButton.addClickHandler(event -> DocumentAssetPresenter.this.onEditFilename());

        viewButton.setSvg(SvgImage.EYE);
        viewButton.setTitle("View in browser");
        viewButton.setVisible(true);
        viewButton.addClickHandler(event -> DocumentAssetPresenter.this.onViewAsset());

        final ButtonPanel toolbar = new ButtonPanel();
        toolbar.addButton(revertButton);
        toolbar.addButton(addButton);
        toolbar.addButton(deleteButton);
        toolbar.addButton(editButton);
        toolbar.addButton(viewButton);

        // Ensure state is set correctly
        updateState();

        return List.of(toolbar);
    }

    @Override
    protected void onBind() {
        super.onBind();

        // Hook up the dirty events on the editor
        editorPresenter.addValueChangeHandler(
                event -> this.onEditorContentChanged());
        editorPresenter.addFormatHandler(
                event -> this.onEditorContentChanged());

        // Ensure editor is disabled by default
        disableEditor();

        // Create the Add menu
        menuItems.add(new IconMenuItem.Builder()
                .priority(0)
                .icon(SvgImage.FOLDER)
                .iconColour(IconColour.BLUE)
                .text("Add New Folder")
                .command(() -> this.onCreateNewItem(false))
                .enabled(true)
                .build());
        menuItems.add(new IconMenuItem.Builder()
                .priority(1)
                .icon(SvgImage.FILE)
                .text("Add New File")
                .command(() -> this.onCreateNewItem(true))
                .enabled(true)
                .build());
        menuItems.add(new IconMenuItem.Builder()
                .priority(2)
                .icon(SvgImage.FILE)
                .iconColour(IconColour.BLUE)
                .text("Upload File")
                .command(this::onUploadFile)
                .enabled(true)
                .build());

        addButton.addClickHandler(event -> {
            final Rect relativeRect = new Rect(addButton.getElement()).grow(3);
            final PopupPosition position = new PopupPosition(relativeRect, PopupLocation.RIGHT);
            ShowMenuEvent.builder()
                    .items(menuItems)
                    .popupPosition(position)
                    .allowCloseOnMoveLeft()
                    .fire(this);
        });
    }

    /**
     * Called by DocPresenter, the superclass, when the document is loaded.
     *
     * @param docRef   Document reference
     * @param document Document
     * @param readOnly Whether this doc is readonly
     */
    @Override
    protected void onRead(@SuppressWarnings("unused") final DocRef docRef,
                          final D document,
                          final boolean readOnly) {
        this.document = document;
        this.readOnly = readOnly;

        // Update UI state
        updateState();

        // Get the asset metadata
        this.fetchDraftAssets(document);
    }

    /**
     * Implementation of onWrite() from DocPresenter. Doesn't do anything, as saving is driven by
     * the owning presenter's post-save callback calling onSave() instead.
     */
    @Override
    protected D onWrite(final D document) {
        return document;
    }

    /**
     * Called by the owning presenter (e.g. VisualisationPresenter, FloorMapPresenter) via the
     * DocumentPlugin post-save callback, to save the assets.
     * Requests the server to copy data from the draft area to the live area of the database.
     *
     * @param document Document that was returned by the save
     * @param callback Thing to call when everything is saved.
     */
    public void onSave(final D document, final Consumer<D> callback) {
        this.document = document;

        // Check if the editor content is dirty and needs saving
        if (assetDirtyState.isDirtyAndNeedsSaveToDraft()) {
            doUpdateContent(assetDirtyState.getPathToEditItem(),
                    editorPresenter.getText().getBytes(StandardCharsets.UTF_8),
                    () -> doOnWrite(callback),
                    this::doSelectionChangeAfterUpdateContentFailed);
        } else {
            doOnWrite(callback);
        }
    }

    /**
     * Called by the owning presenter via the DocumentPlugin post-save callback, to do SaveAs.
     * SaveAs does the following:
     * <ol>
     *     <li>Copies the live assets to the new document</li>
     *     <li>Copies the draft assets to the new document</li>
     *     <li>Updates any necessary content in the draft assets</li>
     *     <li>Saves the draft into live for the new document</li>
     * </ol>
     *
     * @param newDocument The document we're Saving As.
     * @param callback    The lamda to call when this chain is complete.
     */
    public void onSaveAs(final D newDocument, final Consumer<D> callback) {

        Console.info("onSaveAs for " + document + " to " + newDocument);

        String updatedContentPath = null;
        byte[] updatedContent = null;
        if (assetDirtyState.isDirtyAndNeedsSaveToDraft()) {
            updatedContentPath = assetDirtyState.getPathToEditItem();
            updatedContent = editorPresenter.getText().getBytes(StandardCharsets.UTF_8);
        }
        final DocumentAssetSaveAsParameters params =
                new DocumentAssetSaveAsParameters(newDocument.getUuid(), updatedContentPath, updatedContent);

        restFactory.create(DOCUMENT_ASSET_RESOURCE)
                .method(r -> r.saveAs(document.getUuid(), params))
                .onSuccess(result -> {
                    if (result) {
                        // Tab becomes the new document so change the reference here
                        this.document = newDocument;
                        // Invoke next in the chain
                        callback.accept(newDocument);
                    } else {
                        AlertEvent.fireError(this,
                                "There was an error saving the document to a new document",
                                null);
                    }
                })
                .onFailure(error -> {
                    AlertEvent.fireError(this,
                            "There was an error saving the document to a new document: " + error.getMessage(),
                            null);
                })
                .taskMonitorFactory(this)
                .exec();
    }

    /**
     * Called when Add button / Add Folder is clicked.
     * Inserts a new folder with the currently selected folder.
     */
    private void onCreateNewItem(final boolean addFile) {
        if (!readOnly) {
            final DocumentAssetTreeItem parentItem =
                    DocumentAssetPresenterUtils.findFolderForSelectedItem(
                            (DocumentAssetTreeItem) tree.getSelectedItem());

            final String path = DocumentAssetPresenterUtils.getItemPath(parentItem);
            final ShowPopupEvent.Builder popupEventBuilder = new ShowPopupEvent.Builder(addItemDialog);
            final DialogType dialogType = addFile
                    ? DialogType.FILE_DIALOG
                    : DialogType.FOLDER_DIALOG;
            addItemDialog.setupPopup(popupEventBuilder, path, ILLEGAL_ASSET_NAME_CHARACTERS, dialogType);
            popupEventBuilder
                    .onHideRequest(event -> {
                        if (event.isOk()) {
                            // Ok pressed
                            if (addItemDialog.isValid()) {
                                final String itemName = getNonClashingLabel(parentItem,
                                        addItemDialog.getView().getName(),
                                        null);
                                DocumentAssetPresenterUtils
                                        .markOpenClosedStateOpen(parentItem, treeItemPathToOpenState);
                                final String newItemPath =
                                        DocumentAssetPresenterUtils.getNewItemPath(parentItem, itemName);
                                if (addFile) {
                                    doUpdateNewFile(newItemPath);
                                } else {
                                    doUpdateNewFolder(newItemPath);
                                }
                                event.hide();
                            } else {
                                AlertEvent.fireWarn(this, "Item name not set", event::reset);
                            }
                        } else {
                            // Cancel pressed
                            event.hide();
                        }
                    })
                    .fire();
        }

    }

    /**
     * Called when the Delete button is clicked.
     * Deletes the currently selected item.
     */
    private void onDeleteButtonClick() {
        if (!readOnly) {
            final DocumentAssetTreeItem assetTreeItem = (DocumentAssetTreeItem) tree.getSelectedItem();
            if (assetTreeItem != null) {
                final String message;
                if (assetTreeItem.isLeaf()) {
                    message = "Are you sure you want to delete the selected file?";
                } else {
                    message = "Are you sure you want to delete the selected folder and all its descendants?";
                }

                ConfirmEvent.fire(DocumentAssetPresenter.this, message,
                        result -> {
                            if (result) {
                                final String path = DocumentAssetPresenterUtils.getItemPath(assetTreeItem);
                                doUpdateDelete(path, !assetTreeItem.isLeaf());
                            }
                        });
            }

        }
    }

    /**
     * Gets dirty events to update the class member variable.
     */
    @Override
    public void onDirty() {
        updateState();
    }

    /**
     * Called when the EDIT button is pressed. Allows users to change the text of
     * a TreeItem.
     */
    private void onEditFilename() {
        if (!readOnly) {
            final DocumentAssetTreeItem selectedItem = (DocumentAssetTreeItem) tree.getSelectedItem();
            if (selectedItem != null) {
                final ShowPopupEvent.Builder popupEventBuilder = new ShowPopupEvent.Builder(editAssetDialog);
                editAssetDialog.setupPopup(popupEventBuilder, selectedItem, ILLEGAL_ASSET_NAME_CHARACTERS);
                popupEventBuilder
                        .onHideRequest(event -> {
                            if (event.isOk()) {
                                if (editAssetDialog.isValid()) {
                                    final String oldPath =
                                            DocumentAssetPresenterUtils.getItemPath(selectedItem);
                                    final DocumentAssetTreeItem parentItem =
                                            (DocumentAssetTreeItem) selectedItem.getParentItem();
                                    final String text = getNonClashingLabel(parentItem,
                                            editAssetDialog.getView().getText(),
                                            editAssetDialog.getView().getId());
                                    final String newPath =
                                            DocumentAssetPresenterUtils.getNewItemPath(parentItem, text);
                                    doUpdateRename(oldPath, newPath, !selectedItem.isLeaf());
                                    event.hide();
                                } else {
                                    AlertEvent.fireWarn(this,
                                            editAssetDialog.getValidationErrorMessage(),
                                            event::reset);
                                }
                            } else {
                                // Cancel pressed
                                event.hide();
                            }
                        })
                        .fire();
            }
        }
    }

    /**
     * Called when the editor is dirty and needs a save.
     */
    private void onEditorContentChanged() {
        assetDirtyState.onAssetContentChanged();
        onChange();
        // Force fire a dirty event to ensure parents re-evaluate their dirty state
        // even if our own dirty state hasn't transitioned.
        DirtyEvent.fire(this, isDirty());
    }

    /**
     * Called when the Revert button is clicked.
     * Gets rid of draft changes and goes back to the live version of the assets.
     * Scheduler calls here shouldn't be necessary.
     */
    private void onRevertButtonClick() {

        final String message = "Are you sure you want to lose all your changes?";
        ConfirmEvent.fire(DocumentAssetPresenter.this, message,
                result -> {
                    if (result) {
                        Scheduler.get().scheduleFinally(() -> {
                            restFactory.create(DOCUMENT_ASSET_RESOURCE)
                                    .method(r -> r.revertDraftFromLive(document.getUuid()))
                                    .onSuccess(revertResult -> {
                                        disableEditor();
                                        if (revertResult) {
                                            // It worked - data reverted
                                            Scheduler.get().scheduleFinally(() -> {
                                                final DocRef docRef = document.asDocRef();
                                                document = null; // Make sure doc is reloaded on refresh
                                                RefreshDocumentEvent.fire(
                                                        DocumentAssetPresenter.this,
                                                        docRef);
                                            });
                                        } else {
                                            AlertEvent.fireError(this,
                                                    "Error reverting to live version",
                                                    null);
                                        }
                                    })
                                    .onFailure(error -> {
                                        disableEditor();
                                        AlertEvent.fireError(this,
                                                "Error reverting to live version: " + error.getMessage(),
                                                null);
                                    })
                                    .taskMonitorFactory(this)
                                    .exec();
                        });
                    }
                });
    }

    /**
     * Called when the selection changes.
     */
    private void onSelectionChange() {
        if (!readOnly) {
            if (assetDirtyState.isDirtyAndNeedsSaveToDraft()) {
                doUpdateContent(assetDirtyState.getPathToEditItem(),
                        editorPresenter.getText().getBytes(StandardCharsets.UTF_8),
                        this::doSelectionChangeAfterUpdateContentSuccess,
                        this::doSelectionChangeAfterUpdateContentFailed);
            } else {
                doSelectionChangeAfterUpdateContentSuccess();
            }
        } else {
            doSelectionChangeAfterUpdateContentSuccess();
        }
    }

    /**
     * Called after onSelectionChange() when the saving of existing content fails.
     * Select back to previous item.
     */
    private void doSelectionChangeAfterUpdateContentFailed() {
        tree.setSelectedItem(assetDirtyState.getEditItem(), false);
        updateState();
    }

    /**
     * Called after the current editor contents has been saved.
     * Loads up the new editor contents.
     */
    private void doSelectionChangeAfterUpdateContentSuccess() {
        assetDirtyState.onUpdateContentSuccess();

        final DocumentAssetTreeItem selectedItem = (DocumentAssetTreeItem) tree.getSelectedItem();

        if (selectedItem != null && selectedItem.isLeaf()) {
            final String selectedItemPath = DocumentAssetPresenterUtils.getItemPath(selectedItem);
            assetDirtyState.onSelectNewItemAfterSaveOldItem(selectedItem, selectedItemPath);
            fetchDraftContent(document,
                    selectedItemPath,
                    this::doSelectionChangeAfterFetchDraftContentSuccess,
                    this::doSelectItemFetchDraftContentFailed);
        } else {
            doSelectItemFetchDraftContentFailed();
        }
    }

    /**
     * Handles a failure to fetch draft content, from doSelectionChangeAfterUpdateContentSuccess()
     * or the fetchDraftContent() error path.
     */
    private void doSelectItemFetchDraftContentFailed() {
        disableEditor();
        updateState();
    }

    /**
     * Called after the new editor contents has been loaded.
     */
    private void doSelectionChangeAfterFetchDraftContentSuccess() {
        updateState();
    }

    /**
     * Called when Add Button / Upload File is clicked.
     * Shows the uploadFileDialog. The dialog calls back into this object via the
     * DocumentAssetAddFileCallback interface.
     */
    private void onUploadFile() {
        if (!readOnly) {
            final DocumentAssetTreeItem folderItem =
                    DocumentAssetPresenterUtils.findFolderForSelectedItem(
                            (DocumentAssetTreeItem) tree.getSelectedItem());
            final String path = DocumentAssetPresenterUtils.getItemPath(folderItem);
            uploadFileDialog.fireShowPopup(this, folderItem, path, ILLEGAL_ASSET_NAME_CHARACTERS);
        }
    }

    /**
     * Called when the user wants to view an asset.
     * Opens a new Browser window (tab) pointing to the asset via the Servlet.
     */
    public void onViewAsset() {
        final DocumentAssetTreeItem selectedItem = (DocumentAssetTreeItem) tree.getSelectedItem();
        if (selectedItem != null) {
            // Find the document ID
            if (document != null) {
                final String docId = document.getUuid();
                final String relativePath = ASSET_SERVLET_PATH_PREFIX
                                            + docId
                                            + DocumentAssetPresenterUtils.getItemPath(selectedItem);
                Window.open(relativePath, "_blank", null);
            }
        }
    }

    /**
     * Called from onWrite() after any saving of content has happened, if that is necessary.
     */
    private void doOnWrite(final Consumer<D> callback) {
        // Transfer draft saves to live
        restFactory.create(DOCUMENT_ASSET_RESOURCE)
                .method(r -> r.saveDraftToLive(document.getUuid()))
                .onSuccess(result -> {
                    if (result) {
                        // Do the original resultConsumer
                        callback.accept(document);
                    } else {
                        AlertEvent.fireError(this,
                                "Error saving assets",
                                null);
                    }
                })
                .onFailure(error -> {
                    AlertEvent.fireError(this,
                            "Error saving assets: " + error.getMessage(),
                            null);
                })
                .taskMonitorFactory(this)
                .exec();
    }

    /**
     * Does the REST call to the server to create a new folder.
     * Then calls fetchDraftAssets() to reload the tree.
     *
     * @param path Where the folder must be created.
     */
    private void doUpdateNewFolder(final String path) {
        Objects.requireNonNull(path);

        restFactory.create(DOCUMENT_ASSET_RESOURCE)
                .method(r -> r.updateNewFolder(document.getUuid(), path))
                .onSuccess(result -> {
                    if (result) {
                        // OK - get the assets again
                        fetchDraftAssets(document);
                    } else {
                        AlertEvent.fireError(this,
                                "There was an error creating a new folder",
                                null);
                    }
                })
                .onFailure(error -> {
                    AlertEvent.fireError(this,
                            "There was an error creating a new folder: " + error.getMessage(),
                            null);
                })
                .taskMonitorFactory(this)
                .exec();
    }

    /**
     * Does the REST call to create a new file.
     *
     * @param path The path and name of the file.
     */
    private void doUpdateNewFile(final String path) {
        Objects.requireNonNull(path);

        final DocumentAssetUpdateNewFile update =
                new DocumentAssetUpdateNewFile(path, null);

        restFactory.create(DOCUMENT_ASSET_RESOURCE)
                .method(r -> r.updateNewFile(document.getUuid(), update))
                .onSuccess(result -> {
                    if (result) {
                        // OK - get the assets again
                        fetchDraftAssets(document);
                    } else {
                        AlertEvent.fireError(this,
                                "There was an error creating a new file",
                                null);
                    }
                })
                .onFailure(error -> {
                    AlertEvent.fireError(this,
                            "There was an error creating a new file: " + error.getMessage(),
                            null);
                })
                .taskMonitorFactory(this)
                .exec();
    }

    /**
     * Does the REST call to create a new file, where the user has uploaded a file.
     *
     * @param path        Where to put the file
     * @param resourceKey The resource key associated with the upload
     */
    private void doUpdateNewUploadedFile(final String path,
                                         final ResourceKey resourceKey) {
        Objects.requireNonNull(path);
        Objects.requireNonNull(resourceKey);

        final DocumentAssetUpdateNewFile update =
                new DocumentAssetUpdateNewFile(path, resourceKey);

        restFactory.create(DOCUMENT_ASSET_RESOURCE)
                .method(r ->
                        r.updateNewUploadedFile(document.getUuid(), update))
                .onSuccess(result -> {
                    if (result) {
                        // OK - get the assets again
                        fetchDraftAssets(document);
                    } else {
                        AlertEvent.fireError(this,
                                "There was an error uploading a new file",
                                null);
                    }
                })
                .onFailure(error -> {
                    AlertEvent.fireError(this,
                            "There was an error uploading a new file: " + error.getMessage(),
                            null);
                })
                .taskMonitorFactory(this)
                .exec();
    }

    /**
     * Does the REST call to delete a file or folder
     *
     * @param path     The path to delete. The terminal name in the path will be deleted.
     * @param isFolder Whether the path refers to a folder or a file.
     */
    private void doUpdateDelete(final String path,
                                final boolean isFolder) {
        Objects.requireNonNull(path);

        final DocumentAssetUpdateDelete update = new DocumentAssetUpdateDelete(path, isFolder);

        restFactory.create(DOCUMENT_ASSET_RESOURCE)
                .method(r ->
                        r.updateDelete(document.getUuid(), update))
                .onSuccess(result -> {
                    if (result) {
                        // Clear the editor - the thing we're editing has disappeared
                        disableEditor();

                        // Get the assets again
                        fetchDraftAssets(document);
                    } else {
                        AlertEvent.fireError(this,
                                "There was an error deleting an item",
                                null);
                    }
                })
                .onFailure(error -> {
                    AlertEvent.fireError(this,
                            "There was an error deleting an item: " + error.getMessage(),
                            null);
                })
                .taskMonitorFactory(this)
                .exec();
    }

    /**
     * Does the REST call to rename a file or folder.
     *
     * @param oldPath  The thing we want to rename
     * @param newPath  The new path to replace the old path
     * @param isFolder Whether the thing being renamed is a folder.
     */
    private void doUpdateRename(final String oldPath,
                                final String newPath,
                                final boolean isFolder) {
        Objects.requireNonNull(oldPath);
        Objects.requireNonNull(newPath);
        final DocumentAssetUpdateRename update = new DocumentAssetUpdateRename(oldPath, newPath, isFolder);
        restFactory.create(DOCUMENT_ASSET_RESOURCE)
                .method(r ->
                        r.updateRename(document.getUuid(), update))
                .onSuccess(result -> {
                    if (result) {
                        // OK - get the assets again
                        fetchDraftAssets(document);
                    } else {
                        AlertEvent.fireError(this,
                                "There was an error renaming an item",
                                null);
                    }
                })
                .onFailure(error -> {
                    AlertEvent.fireError(this,
                            "There was an error renaming an item: " + error.getMessage(),
                            null);
                })
                .taskMonitorFactory(this)
                .exec();
    }

    /**
     * Does the REST update for the content of a file.
     *
     * @param path            The path to the file.
     * @param content         The new content for a file. Must not be null but can be empty.
     * @param successCallback Method to call on success
     * @param failureCallback Method to call on failure, after showing the alert dialog.
     */
    private void doUpdateContent(final String path,
                                 final byte[] content,
                                 final Runnable successCallback,
                                 final AlertCallback failureCallback) {
        Objects.requireNonNull(path);
        Objects.requireNonNull(content);

        final DocumentAssetUpdateContent update =
                new DocumentAssetUpdateContent(path, content);
        restFactory.create(DOCUMENT_ASSET_RESOURCE)
                .method(r ->
                        r.updateContent(document.getUuid(), update))
                .onSuccess(result -> {
                    if (result) {
                        // Next link in chain
                        successCallback.run();
                    } else {
                        AlertEvent.fireError(this,
                                "There was an error updating content",
                                failureCallback);
                    }
                })
                .onFailure(error -> {
                    AlertEvent.fireError(this,
                            "There was an error updating content: " + error.getMessage(),
                            failureCallback);
                })
                .taskMonitorFactory(this)
                .exec();
    }

    /**
     * Called to clear-down the editor and disable it when the item cannot be edited
     */
    private void disableEditor() {
        editorPresenter.setText("");
        editorPresenter.setReadOnly(true);
        editorPresenter.getWidget().setVisible(false);
    }

    /**
     * Fetches the content for the asset at the given path.
     *
     * @param document The document that owns the assets.
     * @param path     The path of the asset. Must be a leaf node path.
     */
    private void fetchDraftContent(final D document,
                                   final String path,
                                   final Runnable successCallback,
                                   final AlertCallback failureCallback) {
        Objects.requireNonNull(document);
        Objects.requireNonNull(path);

        restFactory.create(DOCUMENT_ASSET_RESOURCE)
                .method(r -> r.getDraftContent(document.getUuid(), path))
                .onSuccess(result -> {
                    final String content = result.getContent();
                    if (content == null) {
                        failureCallback.onClose();
                    } else {
                        AceEditorMode editorMode;
                        try {
                            editorMode = AceEditorMode.valueOf(result.getEditorMode());
                        } catch (final IllegalArgumentException e) {
                            Console.warn("Error converting '" + result.getEditorMode()
                                         + "' to an editor mode: " + e.getMessage());
                            editorMode = AceEditorMode.PLAIN_TEXT;
                        }

                        editorPresenter.getWidget().setVisible(true);
                        editorPresenter.setText(result.getContent());
                        editorPresenter.setReadOnly(isReadOnly());
                        editorPresenter.setMode(editorMode);
                        successCallback.run();
                    }
                })
                .onFailure(error -> {
                    AlertEvent.fireError(this,
                            "There was an error getting content for '"
                            + path + "': \n" + error.getMessage(),
                            failureCallback);
                })
                .taskMonitorFactory(this)
                .exec();
    }

    /**
     * Called from onRead() to pull down the assets from the server.
     * Uses REST to grab the assets and display them within the tree control.
     * Async.
     *
     * @param document The document received from the server.
     */
    private void fetchDraftAssets(final D document) {
        final String ownerId = document.getUuid();
        DocumentAssetPresenterUtils.storeOpenClosedState(tree, treeItemPathToOpenState);

        restFactory.create(DOCUMENT_ASSET_RESOURCE)
                .method(r -> r.fetchDraftAssets(ownerId))
                .onSuccess(assets -> {
                    // Clear any existing content from the tree
                    tree.clear();

                    // Put the new tree in
                    DocumentAssetPresenterUtils.addPathsToTree(tree, assets.getAssets());

                    // Mark the editor content as clean
                    assetDirtyState.onFetchDraftAssets();

                    // Set dirty state from the state of the DB, i.e. whether the user has pending
                    // draft asset changes not yet saved to live.
                    this.serverDirty = assets.isDirty();
                    onChange();

                    // Restore the open/closed state of the tree
                    DocumentAssetPresenterUtils.restoreOpenClosedState(tree, treeItemPathToOpenState);

                    // Make sure UI state is correct
                    updateState();
                })
                .onFailure(error -> AlertEvent.fireError(this,
                        "Error downloading assets for this visualisation: " + error.getMessage(),
                        null))
                .taskMonitorFactory(this)
                .exec();
    }

    /**
     * Sets the state of the UI when things have changed.
     */
    private void updateState() {

        if (readOnly) {
            revertButton.setEnabled(false);
            addButton.setEnabled(false);
            deleteButton.setEnabled(false);
            editButton.setEnabled(false);
            viewButton.setEnabled(false);
        } else {
            revertButton.setEnabled(isDirty());

            final DocumentAssetTreeItem item = (DocumentAssetTreeItem) tree.getSelectedItem();
            if (item == null) {
                // Assume the root item is selected so enable 'add'
                addButton.setEnabled(true);
                deleteButton.setEnabled(false);
                editButton.setEnabled(false);
                viewButton.setEnabled(false);
                disableEditor();
            } else {
                addButton.setEnabled(true);
                deleteButton.setEnabled(true);
                editButton.setEnabled(true);
                // Only enable this button if tree isn't dirty (otherwise the item might not be on the server)
                // and the thing isn't a folder
                viewButton.setEnabled(!isDirty() && item.isLeaf());
            }
        }
    }

    // --------------------------------------------------------------------------------

    /**
     * Provides the images for the CellTree
     */
    private static class AssetTreeResources implements Tree.Resources {

        /**
         * Height and width of the image in pixels
         */
        private static final int HEIGHT = 12;
        private static final int WIDTH = 16;
        private static final DocumentAssetImageResource CLOSED =
                new DocumentAssetImageResource(HEIGHT, WIDTH, "/ui/background-images/arrow-right.png");
        private static final DocumentAssetImageResource OPEN =
                new DocumentAssetImageResource(HEIGHT, WIDTH, "/ui/background-images/arrow-down.png");
        private static final DocumentAssetImageResource TRANSPARENT =
                new DocumentAssetImageResource(HEIGHT, WIDTH, "/ui/background-images/transparent-16x16.png");

        @Override
        public ImageResource treeClosed() {
            return CLOSED;
        }

        @Override
        public ImageResource treeLeaf() {
            return TRANSPARENT;
        }

        @Override
        public ImageResource treeOpen() {
            return OPEN;
        }

    }

    // --------------------------------------------------------------------------------

    /**
     * Interface for View.
     */
    public interface DocumentAssetView extends View {

        /**
         * Sets the cell tree within the view.
         */
        void setTreeAndEditor(final Tree cellTree, final EditorPresenter editor);
    }
}
