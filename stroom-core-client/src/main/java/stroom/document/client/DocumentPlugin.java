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

package stroom.document.client;

import com.google.gwt.core.client.GWT;
import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.event.SelectContentTabEvent;
import stroom.core.client.ContentManager;
import stroom.core.client.ContentManager.CloseCallback;
import stroom.core.client.ContentManager.CloseHandler;
import stroom.core.client.presenter.Plugin;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.entity.shared.DocumentServiceReadAction;
import stroom.entity.shared.DocumentServiceWriteAction;
import stroom.explorer.client.event.HighlightExplorerNodeEvent;
import stroom.explorer.shared.ExplorerNode;
import stroom.docref.DocRef;
import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskStartEvent;
import stroom.docref.SharedObject;
import stroom.widget.util.client.Future;

import java.util.HashMap;
import java.util.Map;

public abstract class DocumentPlugin<D extends SharedObject> extends Plugin {
    private final ClientDispatchAsync dispatcher;
    private final Map<DocRef, DocumentTabData> documentToTabDataMap = new HashMap<>();
    private final Map<DocumentTabData, DocRef> tabDataToDocumentMap = new HashMap<>();
    private final ContentManager contentManager;
    private final DocumentPluginEventManager documentPluginEventManager;

    public DocumentPlugin(final EventBus eventBus,
                          final ClientDispatchAsync dispatcher,
                          final ContentManager contentManager,
                          final DocumentPluginEventManager documentPluginEventManager) {
        super(eventBus);
        this.contentManager = contentManager;

        this.dispatcher = dispatcher;

        this.documentPluginEventManager = documentPluginEventManager;

        // Register this plugin.
        final String type = getType();
        if (null != type) {
            documentPluginEventManager.registerPlugin(type, this);
        }
    }

    protected void registerAsPluginForType(final String type) {
        this.documentPluginEventManager.registerPlugin(type, this);
    }

//    /**
//     * 1. This method will create a new document and show it in the content pane.
//     */
//    void createDocument(final Presenter<?, ?> popup, final DocRef folder, final String name, final PermissionInheritance permissionInheritance) {
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
     * 4. This method will open an document and show it in the content pane.
     */
    @SuppressWarnings("unchecked")
    public DocumentEditPresenter<?, D> open(final DocRef docRef, final boolean forceOpen) {
        DocumentEditPresenter<?, D> presenter = null;

        final DocumentTabData existing = documentToTabDataMap.get(docRef);
        // If we already have a tab item for this document then make sure it is
        // visible.
        if (existing != null) {
            // Start spinning.
            TaskStartEvent.fire(this, "Opening document");

            // Tell the content presenter to select this existing tab.
            SelectContentTabEvent.fire(this, existing);

            // Stop spinning.
            TaskEndEvent.fire(DocumentPlugin.this);

            if (existing instanceof DocumentEditPresenter) {
                presenter = (DocumentEditPresenter<?, D>) existing;
            }

        } else if (forceOpen) {
            // Start spinning.
            TaskStartEvent.fire(this, "Opening document");

            // If the item isn't already open but we are forcing it open then,
            // create a new presenter and register it as open.
            final DocumentEditPresenter<?, D> documentEditPresenter = (DocumentEditPresenter<?, D>) createEditor();
            presenter = documentEditPresenter;

            if (documentEditPresenter instanceof DocumentTabData) {
                final DocumentTabData tabData = (DocumentTabData) documentEditPresenter;

                // Register the tab as being open.
                documentToTabDataMap.put(docRef, tabData);
                tabDataToDocumentMap.put(tabData, docRef);

                // Load the document and show the tab.
                load(docRef)
                        .onSuccess(doc -> {
                            try {
                                if (doc != null) {
                                    // Read the newly loaded document.
                                    documentEditPresenter.read(getDocRef(doc), doc);

                                    // Open the tab.
                                    final CloseHandler closeHandler = new EntityCloseHandler(tabData);
                                    contentManager.open(closeHandler, tabData, documentEditPresenter);
                                }
                            } finally {
                                // Stop spinning.
                                TaskEndEvent.fire(DocumentPlugin.this);
                            }
                        })
                        .onFailure(caught -> {
                            GWT.log(caught.getMessage());
                            // Stop spinning.
                            TaskEndEvent.fire(DocumentPlugin.this);
                        });

            } else {
                // Stop spinning.
                TaskEndEvent.fire(DocumentPlugin.this);
            }
        }

        return presenter;
    }

    /**
     * 5. This method will save a document.
     */
    @SuppressWarnings("unchecked")
    public void save(final DocumentTabData tabData) {
        if (tabData != null && tabData instanceof DocumentEditPresenter<?, ?>) {
            final DocumentEditPresenter<?, D> presenter = (DocumentEditPresenter<?, D>) tabData;
            if (presenter.isDirty()) {
                final D document = presenter.getEntity();
                presenter.write(document);
                save(getDocRef(document), document).onSuccess(doc -> presenter.read(getDocRef(doc), doc));
            }
        }
    }

    /**
     * 7. This method will save an document as a copy with a different name.
     */
    void saveAll() {
        for (final DocumentTabData tabData : tabDataToDocumentMap.keySet()) {
            save(tabData);
        }
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
//    void moveDocument(final PresenterWidget<?> popup, final DocRef document, final DocRef folder, final PermissionInheritance permissionInheritance) {
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
//    private void moveDocument(final PresenterWidget<?> popup, final DocRef document, final DocRef folder, final PermissionInheritance permissionInheritance,
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
     * 11. This method will reload an document.
     */
    @SuppressWarnings("unchecked")
    public void reload(final DocRef docRef) {
        // Get the existing tab data for this document.
        final DocumentTabData tabData = documentToTabDataMap.get(docRef);
        // If we have an document edit presenter then reload the document.
        if (tabData != null && tabData instanceof DocumentEditPresenter<?, ?>) {
            final DocumentEditPresenter<?, D> presenter = (DocumentEditPresenter<?, D>) tabData;

            // Start spinning.
            TaskStartEvent.fire(this, "Reloading document");

            // Reload the document.
            load(docRef).onSuccess(doc -> {
                // Read the reloaded document.
                presenter.read(getDocRef(doc), doc);

                // Stop spinning.
                TaskEndEvent.fire(DocumentPlugin.this);
            });
        }
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

    /**
     * This method will highlight the supplied document item in the explorer tree.
     */
    public void highlight(final DocRef docRef) {
        // Open up parent items.
        final ExplorerNode documentData = ExplorerNode.create(docRef);
        HighlightExplorerNodeEvent.fire(DocumentPlugin.this, documentData);
    }

    protected abstract DocumentEditPresenter<?, ?> createEditor();

    public Future<D> load(final DocRef docRef) {
        return dispatcher.exec(new DocumentServiceReadAction<>(docRef));
    }

    public Future<D> save(final DocRef docRef, final D document) {
        return dispatcher.exec(new DocumentServiceWriteAction<>(docRef, document));
    }

    protected abstract DocRef getDocRef(D document);

    public abstract String getType();

    private class EntityCloseHandler implements CloseHandler {
        private final DocumentTabData tabData;

        EntityCloseHandler(final DocumentTabData tabData) {
            this.tabData = tabData;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void onCloseRequest(final CloseCallback callback) {
            if (tabData != null && tabData instanceof DocumentEditPresenter<?, ?>) {
                final DocumentEditPresenter<?, D> presenter = (DocumentEditPresenter<?, D>) tabData;
                if (presenter.isDirty()) {
                    final DocRef docRef = getDocRef(presenter.getEntity());
                    ConfirmEvent.fire(DocumentPlugin.this,
                            docRef.getType() + " '" + docRef.getName()
                                    + "' has unsaved changes. Are you sure you want to close this item?",
                            result -> actuallyClose(tabData, callback, presenter, result));
                } else {
                    actuallyClose(tabData, callback, presenter, true);
                }
            }
        }

        private void actuallyClose(final DocumentTabData tabData, final CloseCallback callback,
                                   final DocumentEditPresenter<?, D> presenter, final boolean ok) {
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
