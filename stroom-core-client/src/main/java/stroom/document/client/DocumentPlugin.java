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

package stroom.document.client;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.event.SelectContentTabEvent;
import stroom.core.client.ContentManager;
import stroom.core.client.HasSave;
import stroom.core.client.event.CloseContentEvent;
import stroom.core.client.event.CloseContentEvent.Callback;
import stroom.core.client.event.CloseContentEvent.DirtyMode;
import stroom.core.client.event.CloseContentEvent.Handler;
import stroom.core.client.event.ShowFullScreenEvent;
import stroom.core.client.presenter.Plugin;
import stroom.dispatch.client.RestErrorHandler;
import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent.CommonDocLinkTab;
import stroom.document.client.event.ShowCreateDocumentDialogEvent;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.entity.client.presenter.LinkTabPanelPresenter;
import stroom.explorer.shared.ExplorerNode;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.task.client.HasTaskMonitorFactory;
import stroom.task.client.SimpleTask;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;

import com.google.gwt.core.client.GWT;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class DocumentPlugin<D> extends Plugin implements HasSave {

    private final Map<DocRef, DocumentTabData> documentToTabDataMap = new HashMap<>();
    private final Map<DocumentTabData, DocRef> tabDataToDocumentMap = new HashMap<>();
    private final ContentManager contentManager;
    private final ClientSecurityContext securityContext;

    public DocumentPlugin(final EventBus eventBus,
                          final ContentManager contentManager,
                          final DocumentPluginEventManager documentPluginEventManager,
                          final ClientSecurityContext securityContext) {
        super(eventBus);
        this.contentManager = contentManager;
        this.securityContext = securityContext;

        // Register this plugin.
        final String type = getType();
        if (null != type) {
            documentPluginEventManager.registerPlugin(type, this);
        }
    }

//
//    protected void registerAsPluginForType(final String type) {
//        this.documentPluginEventManager.registerPlugin(type, this);
//    }
//
//    /**
//     * 1. This method will create a new document and show it in the content pane.
//     */
//    void createDocument(final Presenter<?, ?> popup,
//    final DocRef folder,
//    final String name,
//    final PermissionInheritance permissionInheritance) {
//        create(getType(), name, folder, permissionInheritance).onSuccess(docRef -> {
//            // Hide the create document presenter.
//            HidePopupEvent.fire(DocumentPlugin.this, popup);
//
//            highlight(docRef);
//
//            // Open the item in the content pane.
//            open(docRef, true);
//        });
//    }

    /**
     * 4. This method will open a document and show it in the content pane.
     */
    public MyPresenterWidget<?> open(final DocRef docRef,
                                     final boolean forceOpen,
                                     final boolean fullScreen,
                                     final TaskMonitorFactory taskMonitorFactory) {
        return open(docRef, forceOpen, fullScreen, null, null, taskMonitorFactory);
    }

    /**
     * 4. This method will open a document and show it in the content pane with the desired
     * selectedTab
     */
    @SuppressWarnings("unchecked")
    public MyPresenterWidget<?> open(final DocRef docRef,
                                     final boolean forceOpen,
                                     final boolean fullScreen,
                                     final CommonDocLinkTab selectedLinkTab,
                                     final Consumer<MyPresenterWidget<?>> callbackOnOpen,
                                     final TaskMonitorFactory taskMonitorFactory) {
        MyPresenterWidget<?> presenter = null;
        final TaskMonitor taskMonitor = taskMonitorFactory.createTaskMonitor();
        final Task task = new SimpleTask("Opening: " + docRef);
        try {
            // Start spinning.
            taskMonitor.onStart(task);

            final DocumentTabData existing = documentToTabDataMap.get(docRef);
            // If we already have a tab item for this document then make sure it is
            // visible.
            if (existing != null) {
                // Tell the content presenter to select this existing tab.
                SelectContentTabEvent.fire(this, existing);

                if (existing instanceof DocumentEditPresenter) {
                    presenter = (DocumentEditPresenter<?, D>) existing;

                    if (callbackOnOpen != null) {
                        callbackOnOpen.accept(presenter);
                    }
                }

                if (selectedLinkTab != null) {
                    GWT.log("existing - " + existing.getClass().getName());
                    if (existing instanceof DocumentEditTabPresenter<?, ?>) {
                        ((DocumentEditTabPresenter<?, ?>) existing).selectCommonTab(selectedLinkTab);
                    } else if (existing instanceof LinkTabPanelPresenter) {
                        ((LinkTabPanelPresenter) existing).selectCommonTab(selectedLinkTab);
                    }
                }
            } else if (forceOpen) {
                // If the item isn't already open but we are forcing it open then,
                // create a new presenter and register it as open.
                final MyPresenterWidget<?> documentEditPresenter = createEditor();
                presenter = documentEditPresenter;

                if (documentEditPresenter != null) {
                    ((HasTaskMonitorFactory) documentEditPresenter).setTaskMonitorFactory(taskMonitorFactory);
                }

                if (documentEditPresenter instanceof DocumentTabData) {
                    final DocumentTabData tabData = (DocumentTabData) documentEditPresenter;

                    // Register the tab as being open.
                    documentToTabDataMap.put(docRef, tabData);
                    tabDataToDocumentMap.put(tabData, docRef);

                    // Load the document and show the tab.
                    final CloseContentEvent.Handler closeHandler = new EntityCloseHandler(tabData);
                    showDocument(
                            docRef,
                            documentEditPresenter,
                            closeHandler,
                            tabData,
                            fullScreen,
                            selectedLinkTab,
                            callbackOnOpen,
                            taskMonitorFactory);
                }
            }

        } finally {
            // Stop spinning.
            taskMonitor.onEnd(task);
        }

        return presenter;
    }

    @SuppressWarnings("unchecked")
    protected void showDocument(final DocRef docRef,
                                final MyPresenterWidget<?> documentEditPresenter,
                                final Handler closeHandler,
                                final DocumentTabData tabData,
                                final boolean fullScreen,
                                final TaskMonitorFactory taskMonitorFactory) {
        showDocument(
                docRef,
                documentEditPresenter,
                closeHandler,
                tabData,
                fullScreen,
                null,
                null,
                taskMonitorFactory);
    }

    @SuppressWarnings("unchecked")
    protected void showDocument(final DocRef docRef,
                                final MyPresenterWidget<?> myPresenterWidget,
                                final Handler closeHandler,
                                final DocumentTabData tabData,
                                final boolean fullScreen,
                                final CommonDocLinkTab selectedTab,
                                final Consumer<MyPresenterWidget<?>> callbackOnOpen,
                                final TaskMonitorFactory taskMonitorFactory) {
        final RestErrorHandler errorHandler = caught ->
                AlertEvent.fireError(
                        DocumentPlugin.this,
                        "Unable to load document " + docRef, caught.getMessage(),
                        null);

        final Consumer<D> loadConsumer = doc -> {
            if (doc == null) {
                AlertEvent.fireError(DocumentPlugin.this, "Unable to load document " + docRef, null);
            } else {
                if (selectedTab != null) {
                    if (myPresenterWidget instanceof DocumentEditTabPresenter<?, ?>) {
                        ((DocumentEditTabPresenter<?, ?>) myPresenterWidget).selectCommonTab(selectedTab);
                    } else if (myPresenterWidget instanceof LinkTabPanelPresenter) {
                        ((LinkTabPanelPresenter) myPresenterWidget).selectCommonTab(selectedTab);
                    }
                }
                // Read the newly loaded document.
                if (myPresenterWidget instanceof HasDocumentRead) {
                    // Check document permissions and read.
                    securityContext
                            .hasDocumentPermission(
                                    docRef,
                                    DocumentPermission.EDIT,
                                    allowUpdate -> {
                                        ((HasDocumentRead<D>) myPresenterWidget).read(
                                                getDocRef(doc),
                                                doc,
                                                !allowUpdate);
                                        // Open the tab.
                                        if (fullScreen) {
                                            showFullScreen(myPresenterWidget);
                                        } else {
                                            contentManager.open(closeHandler,
                                                    tabData,
                                                    myPresenterWidget,
                                                    myPresenterWidget,
                                                    callbackOnOpen);
                                        }
                                    },
                                    throwable -> AlertEvent.fireErrorFromException(this, throwable, null),
                                    taskMonitorFactory);
                } else {
                    // Open the tab.
                    if (fullScreen) {
                        showFullScreen(myPresenterWidget);
                    } else {
                        contentManager.open(closeHandler, tabData, myPresenterWidget);
                    }
                }
            }
        };

        // Load the document and show the tab.
        load(docRef, loadConsumer, errorHandler, taskMonitorFactory);
    }

    private void showFullScreen(final MyPresenterWidget<?> documentEditPresenter) {
        ShowFullScreenEvent.fire(this, documentEditPresenter);
    }

    /**
     * 5. This method will save a document.
     */
    @SuppressWarnings("unchecked")
    public void save(final DocumentTabData tabData) {
        if (tabData instanceof DocumentEditPresenter<?, ?>) {
            final DocumentEditPresenter<?, D> presenter = (DocumentEditPresenter<?, D>) tabData;
            if (presenter.isDirty()) {
                D document = presenter.getEntity();
                document = presenter.write(document);
                final D finalDocument = document;

                save(getDocRef(document), document,
                        doc -> presenter.read(getDocRef(doc), doc, presenter.isReadOnly()),
                        throwable -> AlertEvent.fireError(
                                this,
                                "Unable to save document " + finalDocument,
                                throwable.getMessage(), null),
                        presenter);
            }
        }
    }

    /**
     * Called when saving a document, just prior to it being saved.
     * Subclasses should override this to implement custom save validation/confirmation.
     *
     * @param doc The doc after onWrite() has been called.
     * @return True to continue with the save, else the save will be aborted.
     */
    public boolean validateBeforeSave(final D doc) {
        return true;
    }

    @SuppressWarnings("unchecked")
    public void saveAs(final DocumentTabData tabData,
                       final ExplorerNode explorerNode) {
        final DocRef docRef = explorerNode.getDocRef();
        if (tabData instanceof DocumentEditPresenter<?, ?>) {
            final DocumentEditPresenter<?, D> presenter = (DocumentEditPresenter<?, D>) tabData;

            final Consumer<ExplorerNode> newDocumentConsumer = newNode -> {
                final DocRef newDocRef = newNode.getDocRef();
                final Consumer<D> saveConsumer = saved -> {
                    // Read the new document into this presenter.
                    presenter.read(newDocRef, saved, false);
                    // Record that the open document has been switched.
                    documentToTabDataMap.remove(docRef);
                    documentToTabDataMap.put(newDocRef, tabData);
                    tabDataToDocumentMap.put(tabData, newDocRef);
                };

                final Consumer<D> loadConsumer = document -> {
                    // Write to the newly created document.
                    document = presenter.write(document);
                    // Save the new document and read it back into the presenter.
                    save(newDocRef, document, saveConsumer, null,
                            presenter);
                };

                // If the user has created a new document then load it.
                load(newDocRef, loadConsumer, throwable -> {
                }, presenter);
            };

            // Ask the user to create a new document.
            ShowCreateDocumentDialogEvent.fire(
                    DocumentPlugin.this,
                    "Save '" + docRef.getName() + "' as",
                    explorerNode,
                    docRef.getType(),
                    docRef.getName(),
                    true,
                    newDocumentConsumer);
        }
    }

    @Override
    public void save() {
        for (final DocumentTabData tabData : tabDataToDocumentMap.keySet()) {
            save(tabData);
        }
    }

    @Override
    public boolean isDirty() {
        for (final DocumentTabData tabData : tabDataToDocumentMap.keySet()) {
            if (tabData instanceof DocumentEditPresenter<?, ?>) {
                final DocumentEditPresenter<?, ?> presenter = (DocumentEditPresenter<?, ?>) tabData;
                if (presenter.isDirty()) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isDirty(final DocRef docRef) {
        final DocumentTabData tabData = documentToTabDataMap.get(docRef);
        if (tabData instanceof DocumentEditPresenter<?, ?>) {
            final DocumentEditPresenter<?, ?> presenter = (DocumentEditPresenter<?, ?>) tabData;
            return presenter.isDirty();
        }
        return false;
    }

//    // /**
//    // * 2. This method will close a tab in the content pane.
//    // */
//    // @SuppressWarnings("unchecked")
//    // public void close(final EntityTabData tabData,
//    // final boolean logoffAfterClose) {
//    // if (tabData != null && tabData instanceof EntityEditPresenter<?, ?>) {
//    // final EntityEditPresenter<?, E> presenter = (EntityEditPresenter<?, E>)
//    // tabData;
//    // if (presenter.isDirty()) {
//    // ConfirmEvent
//    // .fire(EntityPlugin.this,
//    // presenter.getEntity().getType()
//    // + " '"
//    // + presenter.getEntity().getName()
//    // + "' has unsaved changes. Are you sure you want to close this item?",
//    // new ConfirmCallback() {
//    // @Override
//    // public void onResult(final boolean result) {
//    // if (result) {
//    // presenter.closing();
//    // removeTab(tabData, logoffAfterClose);
//    // }
//    // }
//    // });
//    // } else {
//    // presenter.closing();
//    // removeTab(tabData, logoffAfterClose);
//    // }
//    // }
//    // }
//    //
//    // /**
//    // * 3. This method will close all open tabs in the content pane.
//    // */
//    // public void closeAll(final boolean logoffAfterClose) {
//    // final List<EntityTabData> tabs = new ArrayList<EntityTabData>(
//    // tabDataToEntityMap.keySet());
//    // for (final EntityTabData tabData : tabs) {
//    // close(tabData, logoffAfterClose);
//    // }
//    // }
//
//
//
//    /**
//     * 8.2. This method will move an document.
//     */
//    @SuppressWarnings("unchecked")
//    void moveDocument(final PresenterWidget<?> popup, final DocRef document,
//    final DocRef folder, final PermissionInheritance permissionInheritance) {
//        // Find out if we currently have the document open.
//        final DocumentTabData tabData = documentToTabDataMap.get(document);
//        if (tabData != null && tabData instanceof EntityEditPresenter<?, ?>) {
//            final EntityEditPresenter<?, D> editPresenter = (EntityEditPresenter<?, D>) tabData;
//            // Find out if the existing item is dirty.
//            if (editPresenter.isDirty()) {
//                ConfirmEvent.fire(DocumentPlugin.this,
//                        "You must save changes to " + document.getType() + " '"
//                                + document.getDisplayValue()
//                                + "' before it can be moved. Would you like to save the current changes now?",
//                        result -> {
//                            if (result) {
//                                editPresenter.write(editPresenter.getEntity());
//                                moveDocument(popup, document, folder, permissionInheritance, editPresenter);
//                            }
//                        });
//            } else {
//                moveDocument(popup, document, folder, permissionInheritance, editPresenter);
//            }
//        } else {
//            moveDocument(popup, document, folder, permissionInheritance, null);
//        }
//    }
//
//    private void moveDocument(final PresenterWidget<?> popup, final DocRef document,
//    final DocRef folder, final PermissionInheritance permissionInheritance,
//                              final EntityEditPresenter<?, D> editPresenter) {
//        move(document, folder, permissionInheritance).onSuccess(newDocRef -> {
//            // Hide the copy document presenter.
//            HidePopupEvent.fire(DocumentPlugin.this, popup);
//
//            // Select it in the explorer tree.
//            highlight(newDocRef);
//
//            // Reload the document if we were editing it.
//            if (editPresenter != null) {
//                load(newDocRef).onSuccess(editPresenter::read);
//            }
//        });
//    }
//
//    /**
//     * 9. This method will rename an document.
//     */
//    @SuppressWarnings("unchecked")
//    void renameDocument(final PresenterWidget<?> dialog, final DocRef document,
//                        final String name) {
//        // Find out if we currently have the document open.
//        final DocumentTabData tabData = documentToTabDataMap.get(document);
//        if (tabData != null && tabData instanceof EntityEditPresenter<?, ?>) {
//            final EntityEditPresenter<?, D> editPresenter = (EntityEditPresenter<?, D>) tabData;
//            // Find out if the existing item is dirty.
//            if (editPresenter.isDirty()) {
//                ConfirmEvent.fire(DocumentPlugin.this,
//                        "You must save changes to " + document.getType() + " '"
//                                + document.getDisplayValue()
//                                + "' before it can be renamed. Would you like to save the current changes now?",
//                        result -> {
//                            if (result) {
//                                editPresenter.write(editPresenter.getEntity());
//                                renameDocument(dialog, document, name, editPresenter);
//                            }
//                        });
//            } else {
//                renameDocument(dialog, document, name, editPresenter);
//            }
//        } else {
//            renameDocument(dialog, document, name, null);
//        }
//    }
//
//    private void renameDocument(final PresenterWidget<?> popup, final DocRef document, final String name,
//                                final EntityEditPresenter<?, D> editPresenter) {
//        rename(document, name).onSuccess(newDocRef -> {
//            // Hide the rename document presenter.
//            HidePopupEvent.fire(DocumentPlugin.this, popup);
//
//            // Select it in the explorer tree.
//            highlight(newDocRef);
//
//            // Reload the document if we were editing it.
//            if (editPresenter != null) {
//                load(newDocRef).onSuccess(editPresenter::read);
//            }
//        });
//    }
//
//    /**
//     * 10. This method will delete an document.
//     */
//    @SuppressWarnings("unchecked")
//    void deleteDocument(final DocRef document) {
//        // Find out if we currently have the document open.
//        final DocumentTabData tabData = documentToTabDataMap.get(document);
//        if (tabData != null && tabData instanceof EntityEditPresenter<?, ?>) {
//            final EntityEditPresenter<?, D> editPresenter = (EntityEditPresenter<?, D>) tabData;
//            // Find out if the existing item is dirty.
//            if (editPresenter.isDirty()) {
//                ConfirmEvent.fire(DocumentPlugin.this,
//                        "You have unsaved changed for " + document.getType() + " '"
//                                + document.getDisplayValue() + "'.  Are you sure you want to delete it?",
//                        result -> {
//                            if (result) {
//                                deleteDocument(document, tabData);
//                            }
//                        });
//            } else {
//                ConfirmEvent.fire(DocumentPlugin.this,
//                        "You have " + document.getType() + " '" + document.getDisplayValue()
//                                + "' currently open for editing. Are you sure you want to delete it?",
//                        result -> {
//                            if (result) {
//                                deleteDocument(document, tabData);
//                            }
//                        });
//            }
//        } else {
//            ConfirmEvent.fire(DocumentPlugin.this, "Are you sure you want to delete " + document.getType() + " '"
//                    + document.getDisplayValue() + "'?", result -> {
//                if (result) {
//                    deleteDocument(document, null);
//                }
//            });
//        }
//    }

    /**
     * 11. This method will reload a document.
     */
    @SuppressWarnings("unchecked")
    public void reload(final DocRef docRef) {
        // Get the existing tab data for this document.
        final DocumentTabData tabData = documentToTabDataMap.get(docRef);
        // If we have an document edit presenter then reload the document.
        if (tabData instanceof DocumentEditPresenter<?, ?>) {
            final DocumentEditPresenter<?, D> presenter = (DocumentEditPresenter<?, D>) tabData;

            // Reload the document.
            load(docRef,
                    doc -> {
                        // Read the reloaded document.
                        presenter.read(getDocRef(doc), doc, presenter.isReadOnly());
                    },
                    throwable -> {
                    },
                    presenter);
        }
    }

    public List<DocumentTabData> getOpenDocuments(final List<DocRef> docRefs) {
        return NullSafe.stream(docRefs)
                .map(documentToTabDataMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

//    private void deleteDocument(final DocRef document, final DocumentTabData tabData) {
//        delete(document).onSuccess(e -> {
//            if (tabData != null) {
//                // Cleanup reference to this tab data.
//                removeTabData(tabData);
//                contentManager.forceClose(tabData);
//            }
//            // Refresh the explorer tree so the document is marked as deleted.
//            RefreshExplorerTreeEvent.fire(DocumentPlugin.this);
//        });
//    }

    private void removeTabData(final DocumentTabData tabData) {
        final DocRef docRef = tabDataToDocumentMap.remove(tabData);
        documentToTabDataMap.remove(docRef);
    }

    protected abstract MyPresenterWidget<?> createEditor();

    public abstract void load(final DocRef docRef,
                              final Consumer<D> resultConsumer,
                              final RestErrorHandler errorHandler,
                              final TaskMonitorFactory taskMonitorFactory);

    public abstract void save(final DocRef docRef,
                              final D document,
                              final Consumer<D> resultConsumer,
                              final RestErrorHandler errorHandler,
                              final TaskMonitorFactory taskMonitorFactory);

    protected abstract DocRef getDocRef(D document);

    public abstract String getType();


    // --------------------------------------------------------------------------------


    private class EntityCloseHandler implements CloseContentEvent.Handler {

        private final DocumentTabData tabData;

        EntityCloseHandler(final DocumentTabData tabData) {
            this.tabData = tabData;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void onCloseRequest(final CloseContentEvent event) {
            if (tabData != null) {
                if (tabData instanceof DocumentEditPresenter<?, ?>) {
                    final DocumentEditPresenter<?, D> presenter = (DocumentEditPresenter<?, D>) tabData;
                    final DirtyMode dirtyMode = event.getDirtyMode();
                    if (presenter.isDirty() && DirtyMode.FORCE != dirtyMode) {
                        if (DirtyMode.CONFIRM_DIRTY == dirtyMode) {
                            final DocRef docRef = getDocRef(presenter.getEntity());
                            ConfirmEvent.fire(DocumentPlugin.this,
                                    docRef.getType() + " '" + docRef.getName()
                                    + "' has unsaved changes. Are you sure you want to close this item?",
                                    result ->
                                            actuallyClose(tabData, event.getCallback(), presenter, result));
                        } else if (DirtyMode.SKIP_DIRTY == dirtyMode) {
                            // Do nothing
                        } else {
                            throw new RuntimeException("Unexpected DirtyMode: " + dirtyMode);
                        }
                    } else {
                        actuallyClose(tabData, event.getCallback(), presenter, true);
                    }
                } else {
                    // Cleanup reference to this tab data.
                    removeTabData(tabData);
                    // Tell the callback to close the tab.
                    event.getCallback().closeTab(true);
                }
            }
        }

        private void actuallyClose(final DocumentTabData tabData,
                                   final Callback callback,
                                   final DocumentEditPresenter<?, D> presenter,
                                   final boolean ok) {
            if (ok) {
                // Tell the presenter we are closing.
                presenter.onClose();
                // Cleanup reference to this tab data.
                removeTabData(tabData);
            }
            // Tell the callback to close the tab if ok.
            callback.closeTab(ok);
        }
    }
}
