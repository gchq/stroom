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

package stroom.document.asset.client.component;

import stroom.alert.client.event.AlertCallback;
import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.AbstractDoc;
import stroom.document.asset.client.DocumentAssetAddFileCallback;
import stroom.document.asset.client.DocumentAssetState;
import stroom.document.asset.client.DocumentAssetUtils;
import stroom.document.asset.client.component.DocumentAssetAddItemController.DialogType;
import stroom.document.asset.client.component.DocumentAssetsTab.AssetAction;
import stroom.document.asset.shared.DocumentAssetResource;
import stroom.document.asset.shared.DocumentAssetSaveAsParameters;
import stroom.document.asset.shared.DocumentAssetUpdateContent;
import stroom.document.asset.shared.DocumentAssetUpdateDelete;
import stroom.document.asset.shared.DocumentAssetUpdateNewFile;
import stroom.document.asset.shared.DocumentAssetUpdateRename;
import stroom.document.client.editor.HasToolbar;
import stroom.document.client.event.RefreshDocumentEvent;
import stroom.editor.client.component.CodeEditorController;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.util.client.Console;
import stroom.util.shared.ResourceKey;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.widget.menu.client.IconMenuItem;
import stroom.widget.menu.client.Item;
import stroom.widget.menu.client.ShowMenuEvent;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.popup.client.PopupPosition.PopupLocation;
import stroom.widget.util.client.Rect;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/// A document's Assets tab ([DocumentAssetsTab]): its images, CSS, scripts and so on, which the
/// user adds, uploads, renames, deletes and edits as a draft on the server. The draft becomes the
/// document's assets when the document is saved: its owner (e.g. `VisualisationEditor`,
/// `FloorMapEditor`) calls [#onSave] or [#onSaveAs] from the document's post-save callback, and
/// counts the tab's [#isDirty()] as its own.
///
/// @param <D> The document's type.
public class DocumentAssetsTabController<D extends AbstractDoc>
        extends Controller
        implements HasToolbar, DocumentAssetAddFileCallback {

    /// Servlet path - start of the URL for the asset as retrieved via the Servlet
    public static final String ASSET_SERVLET_PATH_PREFIX = "/assets/";

    // Illegal asset name characters - not allowed in any file or folder name
    private static final String ILLEGAL_ASSET_NAME_CHARACTERS = "/:";

    private static final DocumentAssetResource DOCUMENT_ASSET_RESOURCE =
            GWT.create(DocumentAssetResource.class);

    private final DocumentAssetsTab view;
    private final RestFactory restFactory;

    // Tree representing file assets and folders
    private final DocumentAssetTree tree;

    // Dialog that appears when user wants to upload a file
    private final Provider<DocumentAssetUploadFileController> uploadFileDialogProvider;

    // Makes the dialog that adds a file or folder
    private final Provider<DocumentAssetAddItemController> addItemDialogProvider;

    // Makes the dialog that renames the tree item
    private final Provider<DocumentAssetRenameController> editAssetDialogProvider;

    // The selected file's editor
    private final CodeEditorController editor;

    // Set of the node paths that are open when the document is saved
    private final Set<String> treeItemPathToOpenState = new HashSet<>();

    // Items in the Add menu
    private final List<Item> menuItems = new ArrayList<>();

    // State of content edited in editor
    private final DocumentAssetState assetDirtyState = new DocumentAssetState();

    // True if the server has pending draft asset changes that have not yet been saved to live, as
    // reported by the assets' isDirty() on the last fetch. Drives isDirty() (and therefore the
    // Save/revert buttons)
    private boolean serverDirty;

    // Current document - may be null
    private D document;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus                 The application's event bus.
    /// @param view                     The tab.
    /// @param restFactory              Fetches and changes the draft assets.
    /// @param uploadFileDialogProvider Makes the dialog that uploads a file.
    /// @param addItemDialogProvider    Makes the dialog that adds a file or folder.
    /// @param editAssetDialogProvider  Makes the dialog that renames a file or folder.
    /// @param codeEditorProvider       Makes the controller of the file's editor.
    @Inject
    public DocumentAssetsTabController(
            final EventBus eventBus,
            final DocumentAssetsTab view,
            final RestFactory restFactory,
            final Provider<DocumentAssetUploadFileController> uploadFileDialogProvider,
            final Provider<DocumentAssetAddItemController> addItemDialogProvider,
            final Provider<DocumentAssetRenameController> editAssetDialogProvider,
            final Provider<CodeEditorController> codeEditorProvider) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.uploadFileDialogProvider = uploadFileDialogProvider;
        this.addItemDialogProvider = addItemDialogProvider;
        this.editAssetDialogProvider = editAssetDialogProvider;
        initWidget(view);
        this.tree = view.getTree();
        this.editor = make(codeEditorProvider).attach(view.getEditor());

        tree.setOnSelect(item -> onSelectionChange());
        view.setOnAction(this::onAction);

        // Hook up the dirty events on the editor
        own(editor.addValueChangeHandler(event -> onEditorContentChanged()));
        own(editor.addFormatListener(this::onEditorContentChanged));

        // Ensure editor is disabled by default
        disableEditor();

        // Create the Add menu
        menuItems.add(new IconMenuItem.Builder()
                .priority(0)
                .icon(SvgImage.FOLDER)
                .iconColour(IconColour.BLUE)
                .text("New Folder")
                .command(() -> this.onCreateNewItem(false))
                .enabled(true)
                .build());
        menuItems.add(new IconMenuItem.Builder()
                .priority(1)
                .icon(SvgImage.FILE)
                .text("New File")
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
    }

    private void onAction(final AssetAction action) {
        switch (action) {
            case REVERT -> onRevertButtonClick();
            case ADD -> showAddMenu();
            case DELETE -> onDeleteButtonClick();
            case RENAME -> onEditFilename();
            case VIEW -> onViewAsset();
        }
    }

    /// @return Whether there are changes to the assets that the document's Save would save: draft
    /// changes on the server, or an edit to the file shown not yet sent to the draft.
    public boolean isDirty() {
        return serverDirty || assetDirtyState.isDirtyAndNeedsSaveToDraft();
    }

    private boolean isReadOnly() {
        return context(Contexts.READ_ONLY);
    }

    /// Callback from the Add dialog that adds a file that has been uploaded.
    ///
    /// @param parentFolderItem The node that the file has been added to.
    /// @param fileName         The name of the file that was uploaded.
    /// @param resourceKey      The resource key of the file, so that the server can find it later.
    @Override
    public void addUploadedFile(final DocumentAssetTreeItem parentFolderItem,
                                final String fileName,
                                final ResourceKey resourceKey) {

        final String path = DocumentAssetUtils.getNewItemPath(parentFolderItem, fileName);
        DocumentAssetUtils.markOpenClosedStateOpen(parentFolderItem, treeItemPathToOpenState);
        doUpdateNewUploadedFile(path, resourceKey);
    }

    /// Generates a label that doesn't clash with other files/folders in the same directory.
    /// Adds an integer to the end, incrementing until an integer is found that doesn't
    /// clash with anything else.
    ///
    /// @param assetParentItem The tree item that holds the directory.
    /// @param label           The label that we're trying to put into the directory.
    /// @param itemId          The ID of the item with this label. Can be null if this is a new item with no ID
    ///                        yet.
    /// @return A label that doesn't clash with anything else.
    @Override
    public String getNonClashingLabel(final DocumentAssetTreeItem assetParentItem,
                                      final String label,
                                      final String itemId) {
        String nonClashingLabel = label;

        if (assetParentItem != null) {
            int i = 1;
            while (assetParentItem.labelExists(nonClashingLabel, itemId)) {
                nonClashingLabel = DocumentAssetUtils.generateNonClashingLabel(label, i);
                i++;
            }
        } else {
            // Parent is the top of the tree
            int i = 1;
            while (DocumentAssetUtils.labelClashesInTreeRoot(tree, nonClashingLabel, itemId)) {
                nonClashingLabel = DocumentAssetUtils.generateNonClashingLabel(label, i);
                i++;
            }
        }

        return nonClashingLabel;
    }

    /// The tab's buttons, for its document's toolbar. This is asked each time the tab is shown.
    @Override
    public List<Widget> getDocumentButtons() {
        // Ensure state is set correctly
        updateState();

        return List.of(view.getToolbar());
    }

    private void showAddMenu() {
        final Rect relativeRect = new Rect(view.getActionElement(AssetAction.ADD)).grow(3);
        final PopupPosition position = new PopupPosition(relativeRect, PopupLocation.RIGHT);
        ShowMenuEvent.builder()
                .name("Add")
                .items(menuItems)
                .popupPosition(position)
                .opener(view.getActionElement(AssetAction.ADD))
                .allowCloseOnMoveLeft()
                .fire(this);
    }

    /// Shows a document's draft assets, fetched from the server.
    ///
    /// @param docRef   The document.
    /// @param document The document.
    public void read(@SuppressWarnings("unused") final DocRef docRef,
                     final D document) {
        this.document = document;

        // Update UI state
        updateState();

        // Get the asset metadata
        this.fetchDraftAssets(document);
    }

    /// Called by the owning controller (e.g. VisualisationEditor, FloorMapEditor) via the
    /// DocumentPlugin post-save callback, to save the assets.
    /// Requests the server to copy data from the draft area to the live area of the database.
    ///
    /// @param document Document that was returned by the save
    /// @param callback Thing to call when everything is saved.
    public void onSave(final D document, final Consumer<D> callback) {
        this.document = document;

        // Check if the editor content is dirty and needs saving
        if (assetDirtyState.isDirtyAndNeedsSaveToDraft()) {
            doUpdateContent(assetDirtyState.getPathToEditItem(),
                    editor.getText().getBytes(StandardCharsets.UTF_8),
                    () -> doOnWrite(callback),
                    this::doSelectionChangeAfterUpdateContentFailed);
        } else {
            doOnWrite(callback);
        }
    }

    /// Called by the owning controller via the DocumentPlugin post-save callback, to do SaveAs.
    /// SaveAs does the following:
    ///
    /// 1. Copies the live assets to the new document
    /// 2. Copies the draft assets to the new document
    /// 3. Updates any necessary content in the draft assets
    /// 4. Saves the draft into live for the new document
    ///
    /// The source is the editor's document, not the one this tab was last given: the tab is only
    /// given one once it is shown, and Save As must copy the assets whether or not it has been.
    ///
    /// @param source      The document being saved as a copy.
    /// @param newDocument The document we're Saving As.
    /// @param callback    The lamda to call when this chain is complete.
    public void onSaveAs(final D source, final D newDocument, final Consumer<D> callback) {

        Console.info("onSaveAs for " + source + " to " + newDocument);

        String updatedContentPath = null;
        byte[] updatedContent = null;
        if (assetDirtyState.isDirtyAndNeedsSaveToDraft()) {
            updatedContentPath = assetDirtyState.getPathToEditItem();
            updatedContent = editor.getText().getBytes(StandardCharsets.UTF_8);
        }
        final DocumentAssetSaveAsParameters params =
                new DocumentAssetSaveAsParameters(newDocument.getUuid(), updatedContentPath, updatedContent);

        restFactory.create(DOCUMENT_ASSET_RESOURCE)
                .method(r -> r.saveAs(source.getUuid(), params))
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
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    // Called when Add / New Folder or New File is chosen: adds one in the selected folder
    private void onCreateNewItem(final boolean addFile) {
        if (!isReadOnly()) {
            final DocumentAssetTreeItem parentItem =
                    DocumentAssetUtils.findFolderForSelectedItem(
                            tree.getSelectedItem());

            final String path = DocumentAssetUtils.getItemPath(parentItem);
            final DialogType dialogType = addFile
                    ? DialogType.FILE_DIALOG
                    : DialogType.FOLDER_DIALOG;
            addItemDialogProvider.get().show(this, path, ILLEGAL_ASSET_NAME_CHARACTERS, dialogType, name -> {
                final String itemName = getNonClashingLabel(parentItem, name, null);
                DocumentAssetUtils.markOpenClosedStateOpen(parentItem, treeItemPathToOpenState);
                final String newItemPath = DocumentAssetUtils.getNewItemPath(parentItem, itemName);
                if (addFile) {
                    doUpdateNewFile(newItemPath);
                } else {
                    doUpdateNewFolder(newItemPath);
                }
            });
        }

    }

    // Called when the Delete button is clicked: deletes the selected item
    private void onDeleteButtonClick() {
        if (!isReadOnly()) {
            final DocumentAssetTreeItem assetTreeItem = tree.getSelectedItem();
            if (assetTreeItem != null) {
                final String message;
                if (assetTreeItem.isLeaf()) {
                    message = "Are you sure you want to delete the selected file?";
                } else {
                    message = "Are you sure you want to delete the selected folder and all its descendants?";
                }

                ConfirmEvent.fire(this, message,
                        result -> {
                            if (result) {
                                final String path = DocumentAssetUtils.getItemPath(assetTreeItem);
                                doUpdateDelete(path, !assetTreeItem.isLeaf());
                            }
                        });
            }

        }
    }

    // Called when the Rename button is pressed: lets the user rename the selected item
    private void onEditFilename() {
        if (!isReadOnly()) {
            final DocumentAssetTreeItem selectedItem = tree.getSelectedItem();
            if (selectedItem != null) {
                editAssetDialogProvider.get().edit(this,
                        selectedItem.getLabel(),
                        selectedItem.isLeaf(),
                        ILLEGAL_ASSET_NAME_CHARACTERS,
                        name -> {
                            final String oldPath = DocumentAssetUtils.getItemPath(selectedItem);
                            final DocumentAssetTreeItem parentItem = selectedItem.getParentItem();
                            final String text = getNonClashingLabel(parentItem, name, selectedItem.getId());
                            final String newPath = DocumentAssetUtils.getNewItemPath(parentItem, text);
                            doUpdateRename(oldPath, newPath, !selectedItem.isLeaf());
                        });
            }
        }
    }

    // Called when the editor is dirty and needs a save
    private void onEditorContentChanged() {
        assetDirtyState.onAssetContentChanged();
        // The document works out again whether it is dirty, counting this tab's isDirty()
        markChanged();
        updateState();
    }

    // Called when the Revert button is clicked: gets rid of draft changes and goes back to the
    // live version of the assets. Scheduler calls here shouldn't be necessary.
    private void onRevertButtonClick() {

        final String message = "Are you sure you want to lose all your changes?";
        ConfirmEvent.fire(this, message,
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
                                                RefreshDocumentEvent.fire(this, docRef);
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
                                    .owner(this)
                                    .taskMonitorFactory(taskMonitor())
                                    .exec();
                        });
                    }
                });
    }

    // Called when the selection changes
    private void onSelectionChange() {
        if (!isReadOnly()) {
            if (assetDirtyState.isDirtyAndNeedsSaveToDraft()) {
                doUpdateContent(assetDirtyState.getPathToEditItem(),
                        editor.getText().getBytes(StandardCharsets.UTF_8),
                        this::doSelectionChangeAfterUpdateContentSuccess,
                        this::doSelectionChangeAfterUpdateContentFailed);
            } else {
                doSelectionChangeAfterUpdateContentSuccess();
            }
        } else {
            doSelectionChangeAfterUpdateContentSuccess();
        }
    }

    // Called after onSelectionChange() when the saving of existing content fails: selects the
    // previous item again
    private void doSelectionChangeAfterUpdateContentFailed() {
        tree.setSelectedItem(assetDirtyState.getEditItem(), false);
        updateState();
    }

    // Called after the current editor contents has been saved: loads up the new editor contents
    private void doSelectionChangeAfterUpdateContentSuccess() {
        assetDirtyState.onUpdateContentSuccess();

        final DocumentAssetTreeItem selectedItem = tree.getSelectedItem();

        if (selectedItem != null && selectedItem.isLeaf()) {
            final String selectedItemPath = DocumentAssetUtils.getItemPath(selectedItem);
            assetDirtyState.onSelectNewItemAfterSaveOldItem(selectedItem, selectedItemPath);
            fetchDraftContent(document,
                    selectedItemPath,
                    this::doSelectionChangeAfterFetchDraftContentSuccess,
                    this::doSelectItemFetchDraftContentFailed);
        } else {
            doSelectItemFetchDraftContentFailed();
        }
    }

    // Handles a failure to fetch draft content, from doSelectionChangeAfterUpdateContentSuccess()
    // or the fetchDraftContent() error path
    private void doSelectItemFetchDraftContentFailed() {
        disableEditor();
        updateState();
    }

    // Called after the new editor contents has been loaded
    private void doSelectionChangeAfterFetchDraftContentSuccess() {
        updateState();
    }

    // Called when Add / Upload File is chosen: shows the upload dialog, which calls back into this
    // object via the DocumentAssetAddFileCallback interface
    private void onUploadFile() {
        if (!isReadOnly()) {
            final DocumentAssetTreeItem folderItem =
                    DocumentAssetUtils.findFolderForSelectedItem(
                            tree.getSelectedItem());
            final String path = DocumentAssetUtils.getItemPath(folderItem);
            uploadFileDialogProvider.get().show(this, this, folderItem, path, ILLEGAL_ASSET_NAME_CHARACTERS);
        }
    }

    // Called when the user wants to view an asset: opens a new browser window (tab) pointing to
    // the asset via the Servlet
    private void onViewAsset() {
        final DocumentAssetTreeItem selectedItem = tree.getSelectedItem();
        if (selectedItem != null) {
            // Find the document ID
            if (document != null) {
                final String docId = document.getUuid();
                final String relativePath = ASSET_SERVLET_PATH_PREFIX
                                            + docId
                                            + DocumentAssetUtils.getItemPath(selectedItem);
                Window.open(relativePath, "_blank", null);
            }
        }
    }

    // Called from onSave() after any saving of content has happened, if that is necessary
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
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    // Does the REST call to the server to create a new folder, then calls fetchDraftAssets() to
    // reload the tree
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
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    // Does the REST call to create a new file at the path given
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
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    // Does the REST call to create a new file at the path given, where the user has uploaded a
    // file (the upload's resource key)
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
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    // Does the REST call to delete a file or folder: the terminal name in the path is deleted
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
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    // Does the REST call to rename a file or folder
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
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    // Does the REST update for the content of a file (which must not be null but can be empty),
    // then runs the success callback, or the failure callback after showing the alert dialog
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
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    // Clears down the editor and disables it when the item cannot be edited
    private void disableEditor() {
        editor.setText("");
        editor.setReadOnly(true);
        view.setEditorVisible(false);
    }

    // Fetches the content for the asset at the given path (a leaf node's) of the document
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

                        view.setEditorVisible(true);
                        editor.setText(result.getContent());
                        editor.setReadOnly(isReadOnly());
                        editor.setMode(editorMode);
                        successCallback.run();
                    }
                })
                .onFailure(error -> {
                    AlertEvent.fireError(this,
                            "There was an error getting content for '"
                            + path + "': \n" + error.getMessage(),
                            failureCallback);
                })
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    // Called from read() to pull down the assets from the server and show them in the tree
    private void fetchDraftAssets(final D document) {
        final String ownerId = document.getUuid();
        DocumentAssetUtils.storeOpenClosedState(tree, treeItemPathToOpenState);

        restFactory.create(DOCUMENT_ASSET_RESOURCE)
                .method(r -> r.fetchDraftAssets(ownerId))
                .onSuccess(assets -> {
                    // Clear any existing content from the tree
                    tree.clear();

                    // Put the new tree in
                    DocumentAssetUtils.addPathsToTree(tree, assets.getAssets());

                    // Mark the editor content as clean
                    assetDirtyState.onFetchDraftAssets();

                    // Set dirty state from the state of the DB, i.e. whether the user has pending
                    // draft asset changes not yet saved to live.
                    this.serverDirty = assets.isDirty();
                    markChanged();

                    // Restore the open/closed state of the tree
                    DocumentAssetUtils.restoreOpenClosedState(tree, treeItemPathToOpenState);

                    // Make sure UI state is correct
                    updateState();
                })
                .onFailure(error -> AlertEvent.fireError(this,
                        "Error downloading assets for this visualisation: " + error.getMessage(),
                        null))
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    // Sets the state of the UI when things have changed
    private void updateState() {

        if (isReadOnly()) {
            view.setActionEnabled(AssetAction.REVERT, false);
            view.setActionEnabled(AssetAction.ADD, false);
            view.setActionEnabled(AssetAction.DELETE, false);
            view.setActionEnabled(AssetAction.RENAME, false);
            view.setActionEnabled(AssetAction.VIEW, false);
        } else {
            view.setActionEnabled(AssetAction.REVERT, isDirty());

            final DocumentAssetTreeItem item = tree.getSelectedItem();
            if (item == null) {
                // Assume the root item is selected so enable 'add'
                view.setActionEnabled(AssetAction.ADD, true);
                view.setActionEnabled(AssetAction.DELETE, false);
                view.setActionEnabled(AssetAction.RENAME, false);
                view.setActionEnabled(AssetAction.VIEW, false);
                disableEditor();
            } else {
                view.setActionEnabled(AssetAction.ADD, true);
                view.setActionEnabled(AssetAction.DELETE, true);
                view.setActionEnabled(AssetAction.RENAME, true);
                // Only enable this button if tree isn't dirty (otherwise the item might not be on the server)
                // and the thing isn't a folder
                view.setActionEnabled(AssetAction.VIEW, !isDirty() && item.isLeaf());
            }
        }
    }

}
