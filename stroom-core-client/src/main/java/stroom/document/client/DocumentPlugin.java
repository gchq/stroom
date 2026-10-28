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
 */

package stroom.document.client;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.event.SelectContentTabEvent;
import stroom.core.client.ContentManager;
import stroom.core.client.HasSave;
import stroom.core.client.TabPlugin;
import stroom.core.client.event.CloseContentEvent;
import stroom.core.client.event.CloseContentEvent.Callback;
import stroom.core.client.event.CloseContentEvent.DirtyMode;
import stroom.core.client.event.CloseContentEvent.Handler;
import stroom.core.client.event.ShowFullScreenEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.document.client.event.OpenDocumentEvent.CommonDocLinkTab;
import stroom.document.client.event.ShowCreateDocumentDialogEvent;
import stroom.entity.client.component.DocumentEditorHost;
import stroom.entity.client.presenter.HasCommonTabs;
import stroom.explorer.shared.ExplorerNode;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.task.client.SimpleTask;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerNode;

import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class DocumentPlugin<D> extends TabPlugin implements HasSave {

    private final DocumentTabManager documentTabManager = new DocumentTabManager();
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
    public final DocumentEditorHost<?> open(final DocRef docRef,
                                     final boolean forceOpen,
                                     final boolean fullScreen,
                                     final TaskMonitorFactory taskMonitorFactory) {
        return open(docRef, forceOpen, fullScreen, true, null, null, false, taskMonitorFactory);
    }

    public final DocumentEditorHost<?> open(final DocRef docRef,
                                     final boolean forceOpen,
                                     final boolean fullScreen,
                                     final boolean selectDefaultTab,
                                     final Consumer<Controller> callbackOnOpen,
                                     final TaskMonitorFactory taskMonitorFactory) {
        return open(docRef, forceOpen, fullScreen, selectDefaultTab, null, callbackOnOpen, false, taskMonitorFactory);
    }

    public final DocumentEditorHost<?> open(final DocRef docRef,
                                     final boolean forceOpen,
                                     final boolean fullScreen,
                                     final CommonDocLinkTab selectedLinkTab,
                                     final Consumer<Controller> callbackOnOpen,
                                     final boolean duplicate,
                                     final TaskMonitorFactory taskMonitorFactory) {
        return open(docRef, forceOpen, fullScreen, true, selectedLinkTab, callbackOnOpen, duplicate,
                taskMonitorFactory);
    }

    /**
     * 4. This method will open a document and show it in the content pane with the desired
     * selectedTab
     */
    public DocumentEditorHost<?> open(final DocRef docRef,
                                     final boolean forceOpen,
                                     final boolean fullScreen,
                                     final boolean selectDefaultTab,
                                     final CommonDocLinkTab selectedLinkTab,
                                     final Consumer<Controller> callbackOnOpen,
                                     final boolean duplicate,
                                     final TaskMonitorFactory taskMonitorFactory) {
        DocumentEditorHost<?> host = null;
        final TaskMonitor taskMonitor = taskMonitorFactory.createTaskMonitor();
        final Task task = new SimpleTask("Opening: " + docRef);
        try {
            // Start spinning.
            taskMonitor.onStart(task);

            final List<DocumentTabData> existing = documentTabManager.get(docRef);
            // If we already have a tab item for this document then make sure it is
            // visible.
            if (!existing.isEmpty() && !duplicate) {
                @SuppressWarnings("SequencedCollectionMethodCanBeUsed") // Not in GWT
                final DocumentTabData existingTab = existing.get(0);
                // Tell the content presenter to select this existing tab.
                SelectContentTabEvent.fire(this, existingTab);

                if (existingTab instanceof DocumentEditorHost) {
                    host = (DocumentEditorHost<D>) existingTab;

                    if (callbackOnOpen != null) {
                        callbackOnOpen.accept(host);
                    }
                }

                if (selectedLinkTab != null) {
//                    GWT.log("existing - " + existingTab.getClass().getName());
                    if (existingTab instanceof final HasCommonTabs hasCommonTabs) {
                        hasCommonTabs.selectCommonTab(selectedLinkTab);
                    }
                }
            } else if (forceOpen) {
                // If the item isn't already open but we are forcing it open then, create a new
                // editor and register it as open.
                // The editor owns everything made for it; the content manager owns it while its tab
                // is open, and releases it when the tab is closed
                host = createEditor();

                if (host instanceof final HasMultipleInstances hasMultipleInstances) {
                    final List<DocumentTabData> tabs = documentTabManager.get(docRef);
                    if (!tabs.isEmpty()) {
                        final Optional<Integer> maxInstance = tabs.stream()
                                .filter(t -> t instanceof HasMultipleInstances)
                                .map(t -> (HasMultipleInstances) t)
                                .map(HasMultipleInstances::getInstance)
                                .max(Integer::compare);
                        if (maxInstance.isPresent()) {
                            hasMultipleInstances.setInstance(maxInstance.get() + 1);
                        }
                    }
                }

                // Register the tab as being open.
                documentTabManager.put(docRef, host);

                // Load the document and show the tab.
                final CloseContentEvent.Handler closeHandler = new EntityCloseHandler(host);
                showDocument(
                        docRef,
                        host,
                        closeHandler,
                        host,
                        fullScreen,
                        selectDefaultTab,
                        selectedLinkTab,
                        callbackOnOpen,
                        taskMonitorFactory);
            }

        } finally {
            // Stop spinning.
            taskMonitor.onEnd(task);
        }

        return host;
    }

    protected final void showDocument(final DocRef docRef,
                                final DocumentEditorHost<?> host,
                                final Handler closeHandler,
                                final DocumentTabData tabData,
                                final TaskMonitorFactory taskMonitorFactory) {
        showDocument(
                docRef,
                host,
                closeHandler,
                tabData,
                false,
                true,
                null,
                null,
                taskMonitorFactory);
    }

    @SuppressWarnings("unchecked")
    protected void showDocument(final DocRef docRef,
                                final DocumentEditorHost<?> host,
                                final Handler closeHandler,
                                final DocumentTabData tabData,
                                final boolean fullScreen,
                                final boolean selectDefaultTab,
                                final CommonDocLinkTab selectedCommonTab,
                                final Consumer<Controller> callbackOnOpen,
                                final TaskMonitorFactory taskMonitorFactory) {
        final RestErrorHandler errorHandler = caught ->
                AlertEvent.fireError(
                        DocumentPlugin.this,
                        "Unable to load document " + DocRefUtil.createTypedDocRefString(docRef), caught.getMessage(),
                        null);

        final Consumer<D> loadConsumer = doc -> {
            if (doc == null) {
                AlertEvent.fireError(DocumentPlugin.this,
                        "Unable to load document " + DocRefUtil.createTypedDocRefString(docRef),
                        null);
            } else {
                if (selectedCommonTab != null) {
                    host.selectCommonTab(selectedCommonTab);
                }
                // Check document permissions, then read the newly loaded document
                securityContext
                        .hasDocumentPermission(
                                docRef,
                                DocumentPermission.EDIT,
                                allowUpdate -> {
                                    host.setReadOnly(!allowUpdate);
                                    ((DocumentEditorHost<D>) host).read(getDocRef(doc), doc);
                                    // Open the tab.
                                    if (fullScreen) {
                                        showFullScreen(docRef, tabData, host);
                                    } else {
                                        contentManager.open(closeHandler,
                                                tabData,
                                                host,
                                                host,
                                                callbackOnOpen);
                                    }
                                },
                                throwable -> AlertEvent.fireErrorFromException(this, throwable, null),
                                taskMonitorFactory);
            }
        };

        // Load the document and show the tab.
        load(docRef, loadConsumer, errorHandler, taskMonitorFactory);
    }

    private void showFullScreen(final DocRef docRef,
                                final DocumentTabData tabData,
                                final DocumentEditorHost<?> host) {
        // No tab is closed for a document shown full screen, so it is forgotten once its editor is
        // disposed, else opening it again would select a tab that doesn't exist
        host.getControllerNode().add(() -> documentTabManager.remove(docRef, tabData));
        ShowFullScreenEvent.fire(this, host);
    }

    /**
     * 5. This method will save a document.
     */
    public void save(final DocumentTabData tabData) {
        save(tabData, () -> {
        });
    }

    public void save(final DocumentTabData tabData, final Runnable onComplete) {
        if (tabData instanceof DocumentEditorHost<?>) {
            final DocumentEditorHost<D> presenter = (DocumentEditorHost<D>) tabData;
            if (presenter.isDirty()) {
                D document = presenter.getEntity();
                document = presenter.write(document);
                if (document != null) {
                    final String validationError = getPreSaveError(document);
                    if (validationError != null) {
                        AlertEvent.fireWarn(this, validationError, onComplete::run);
                        return;
                    }
                    final D finalDocument = document;
                    save(getDocRef(document), document,
                            presenter.getPostSaveCallback(),
                            doc -> {
                                presenter.read(getDocRef(doc), doc);
                                onComplete.run();
                            },
                            throwable -> {
                                AlertEvent.fireError(
                                        this,
                                        "Unable to save document "
                                        + DocRefUtil.createTypedDocRefString(getDocRef(finalDocument)),
                                        throwable.getMessage(), null);
                                onComplete.run();
                            },
                            presenter);
                    return;
                }
            }
        }
        onComplete.run();
    }

    /**
     * Called after {@link DocumentEditorHost#write} but before the document is sent to the server.
     * Subclasses may override to block the save when the document is in an invalid state.
     *
     * @param doc The document as produced by the presenter's write step.
     * @return A user-facing validation error message to display, or {@code null} if the
     *         document is valid and the save should proceed.
     */
    protected String getPreSaveError(final D doc) {
        return null;
    }

    @SuppressWarnings("unchecked")
    public void saveAs(final DocumentTabData tabData,
                       final ExplorerNode explorerNode) {
        final DocRef docRef = explorerNode.getDocRef();
        if (tabData instanceof DocumentEditorHost<?>) {
            final DocumentEditorHost<D> presenter = (DocumentEditorHost<D>) tabData;

            final Consumer<ExplorerNode> newDocumentConsumer = newNode -> {
                final DocRef newDocRef = newNode.getDocRef();
                final Consumer<D> saveConsumer = saved -> {
                    // Read the new document into this presenter.
                    presenter.setReadOnly(false);
                    presenter.read(newDocRef, saved);
                    // Record that the open document has been switched.
                    documentTabManager.remove(docRef, tabData);
                    documentTabManager.put(newDocRef, tabData);
                };

                final Consumer<D> loadConsumer = document -> {
                    // Write to the newly created document.
                    document = presenter.write(document);
                    // Save the new document and read it back into the presenter.
                    save(newDocRef,
                            document,
                            presenter.getPostSaveAsCallback(),
                            saveConsumer,
                            null,
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
        for (final DocumentTabData tabData : documentTabManager.getAll()) {
            save(tabData);
        }
    }

    @Override
    public boolean isDirty() {
        for (final DocumentTabData tabData : documentTabManager.getAll()) {
            if (tabData instanceof final DocumentEditorHost<?> presenter) {
                if (presenter.isDirty()) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isDirty(final DocRef docRef) {
        final List<DocumentTabData> tabDataList = documentTabManager.get(docRef);
        for (final DocumentTabData tabData : tabDataList) {
            if (tabData instanceof final DocumentEditorHost<?> presenter) {
                if (presenter.isDirty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 11. This method will reload a document.
     */
    @SuppressWarnings("unchecked")
    public void reload(final DocRef docRef) {
        // Get the existing tab data for this document.
        final List<DocumentTabData> tabDataList = documentTabManager.get(docRef);
        // If we have an document edit presenter then reload the document.
        for (final DocumentTabData tabData : tabDataList) {
            if (tabData instanceof DocumentEditorHost<?>) {
                final DocumentEditorHost<D> presenter = (DocumentEditorHost<D>) tabData;

                // Reload the document.
                load(docRef,
                        doc -> {
                            // Read the reloaded document.
                            presenter.read(getDocRef(doc), doc);
                        },
                        throwable -> {
                        },
                        presenter);
            }
        }
    }

    public List<DocumentTabData> getOpenDocuments(final List<DocRef> docRefs) {
        return NullSafe.stream(docRefs)
                .map(documentTabManager::get)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
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
        documentTabManager.remove(tabData.getDocRef(), tabData);
    }

    protected abstract DocumentEditorHost<?> createEditor();

    public abstract void load(final DocRef docRef,
                              final Consumer<D> resultConsumer,
                              final RestErrorHandler errorHandler,
                              final TaskMonitorFactory taskMonitorFactory);

    public abstract void save(final DocRef docRef,
                              final D document,
                              final Consumer<D> resultConsumer,
                              final RestErrorHandler errorHandler,
                              final TaskMonitorFactory taskMonitorFactory);

    /**
     * Extension to the class API to handle a callback after save completes.
     * Default is to call the old save() method for back compatibility.
     * Override if you return non-null from DocumentEditorHost.getPostSaveCallback()
     * otherwise you'll get an IllegalStateException from this method.
     */
    public void save(final DocRef docRef,
                     final D document,
                     final BiConsumer<D, Consumer<D>> postSaveCallback,
                     final Consumer<D> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        if (postSaveCallback != null) {
            throw new IllegalStateException("PostSaveCallback is defined but "
                                            + "DocumentPlugin.save() is not reimplemented");
        } else {
            save(docRef, document, resultConsumer, errorHandler, taskMonitorFactory);
        }
    }

    protected abstract DocRef getDocRef(D document);

    public abstract String getType();

    /**
     * Returns the initialisation handler for this document type.
     *
     * <p>The default implementation returns a no-op handler that immediately
     * signals completion, meaning the document editor opens without any
     * additional user interaction. Override this method to provide a custom
     * dialog that collects required configuration before the editor opens.</p>
     *
     * @return a non-null {@link DocInitialisationHandler}; the default is a
     *         no-op that calls {@code onComplete.accept(true)} immediately
     */
    public DocInitialisationHandler getInitialisationHandler() {
        return (docRef, onComplete, tmf) -> onComplete.accept(true);
    }

    public void create(final String documentName,
                       final Consumer<D> resultConsumer,
                       final RestErrorHandler errorHandler,
                       final TaskMonitorFactory taskMonitorFactory) {
        throw new RuntimeException("Not yet implemented");
    }

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
                if (tabData instanceof DocumentEditorHost<?>) {
                    final DocumentEditorHost<D> presenter = (DocumentEditorHost<D>) tabData;
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
                                   final DocumentEditorHost<D> presenter,
                                   final boolean ok) {
            if (ok) {
                // Tell the presenter we are closing.
                presenter.onClose();
                // Cleanup reference to this tab data.
                removeTabData(tabData);
            }
            // Tell the callback to close the tab if ok.
            // Closing the tab releases the editor and all its presenters
            callback.closeTab(ok);
        }
    }
}
