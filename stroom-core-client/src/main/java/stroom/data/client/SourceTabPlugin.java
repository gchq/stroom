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

package stroom.data.client;

import stroom.alert.client.event.AlertEvent;
import stroom.content.client.event.SelectContentTabEvent;
import stroom.core.client.ContentManager;
import stroom.core.client.ContentManager.CloseCallback;
import stroom.core.client.ContentManager.CloseHandler;
import stroom.core.client.presenter.Plugin;
import stroom.data.client.presenter.SourceTabPresenter;
import stroom.docref.DocRef;
import stroom.explorer.client.event.HighlightExplorerNodeEvent;
import stroom.explorer.shared.ExplorerNode;
import stroom.pipeline.shared.SourceLocation;
import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskStartEvent;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class SourceTabPlugin extends Plugin {

    private final Map<SourceKey, SourceTabPresenter> keyToPresenterMap = new HashMap<>();
    private final Map<SourceTabPresenter, SourceKey> presenterToKeyMap = new HashMap<>();

    private final ContentManager contentManager;
    private final Provider<SourceTabPresenter> sourceTabPresenterProvider;

    @Inject
    public SourceTabPlugin(final EventBus eventBus,
                           final ContentManager contentManager,
                           final Provider<SourceTabPresenter> sourceTabPresenterProvider) {
        super(eventBus);
        this.contentManager = contentManager;
        this.sourceTabPresenterProvider = sourceTabPresenterProvider;

        // Register this plugin.
//        final String type = getType();
//        if (null != type) {
//            documentPluginEventManager.registerPlugin(type, this);
//        }
    }

//
//    protected void registerAsPluginForType(final String type) {
//        this.documentPluginEventManager.registerPlugin(type, this);
//    }
//
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
    public SourceTabPresenter open(final SourceLocation sourceLocation,
                                   final boolean forceOpen) {
        SourceTabPresenter presenter = null;
        final SourceKey sourceKey = new SourceKey(sourceLocation);

        final SourceTabPresenter existing = keyToPresenterMap.get(sourceKey);
        // If we already have a tab item for this document then make sure it is
        // visible.
        if (existing != null) {
            // Start spinning.
            TaskStartEvent.fire(this, "Opening document");

            // Tell the content presenter to select this existing tab.
            SelectContentTabEvent.fire(this, existing);

            // Stop spinning.
            TaskEndEvent.fire(SourceTabPlugin.this);

            presenter = existing;

        } else if (forceOpen) {
            // Start spinning.
            TaskStartEvent.fire(this, "Opening document");

            // If the item isn't already open but we are forcing it open then,
            // create a new presenter and register it as open.

            final SourceTabPresenter sourceTabPresenter = createPresenter(sourceKey);
            presenter = sourceTabPresenter;
            sourceTabPresenter.setSourceLocation(sourceLocation);

            // Register the tab as being open.
            keyToPresenterMap.put(sourceKey, sourceTabPresenter);
            presenterToKeyMap.put(sourceTabPresenter, sourceKey);

            final CloseHandler closeHandler = createCloseHandler(sourceTabPresenter);

            // Load the document and show the tab.
            showTab(sourceKey, sourceTabPresenter, closeHandler);
        }

        return presenter;
    }

    protected void showTab(final SourceKey sourceKey,
                           final SourceTabPresenter sourceTabPresenter,
                           final CloseHandler closeHandler) {

        final Consumer<Throwable> errorConsumer = caught -> {
            AlertEvent.fireError(
                    SourceTabPlugin.this,
                    "Unable to load source " + sourceKey,
                    caught.getMessage(),
                    null);
            // Stop spinning.
            TaskEndEvent.fire(SourceTabPlugin.this);
        };

        try {
            if (sourceKey == null) {
                AlertEvent.fireError(
                        SourceTabPlugin.this,
                        "Unable to load source " + sourceKey,
                        null);
            } else {
                // Open the tab.
                contentManager.open(
                        closeHandler,
                        sourceTabPresenter,
                        sourceTabPresenter);
            }
        } finally {
            // Stop spinning.
            TaskEndEvent.fire(SourceTabPlugin.this);
        }

        // Load the document and show the tab.
//        load(docRef, loadConsumer, errorConsumer);
    }



    /**
     * 11. This method will reload an document.
     */
//    @SuppressWarnings("unchecked")
//    public void reload(final DocRef docRef) {
//        // Get the existing tab data for this document.
//        final DocumentTabData tabData = keyToPresenterMap.get(docRef);
//        // If we have an document edit presenter then reload the document.
//        if (tabData != null && tabData instanceof DocumentEditPresenter<?, ?>) {
//            final DocumentEditPresenter<?, D> presenter = (DocumentEditPresenter<?, D>) tabData;
//
//            // Start spinning.
//            TaskStartEvent.fire(this, "Reloading document");
//
//            // Reload the document.
//            load(docRef,
//                    doc -> {
//                        // Read the reloaded document.
//                        presenter.read(getDocRef(doc), doc);
//
//                        // Stop spinning.
//                        TaskEndEvent.fire(SourceTabPlugin.this);
//                    },
//                    throwable -> {
//                    });
//        }
//    }

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

    private void removeTabData(final SourceTabPresenter sourceTabPresenter) {
        final SourceKey sourceKey = presenterToKeyMap.remove(sourceTabPresenter);
        keyToPresenterMap.remove(sourceKey);
    }

    /**
     * This method will highlight the supplied document item in the explorer tree.
     */
    public void highlight(final DocRef docRef) {
        // Open up parent items.
        final ExplorerNode documentData = ExplorerNode.create(docRef);
        HighlightExplorerNodeEvent.fire(SourceTabPlugin.this, documentData);
    }

    private SourceTabPresenter createPresenter(final SourceKey sourceKey) {
        SourceTabPresenter sourceTabPresenter = sourceTabPresenterProvider.get();
        sourceTabPresenter.setSourceKey(sourceKey);
        return sourceTabPresenter;
    }

//    public abstract void load(final DocRef docRef, final Consumer<D> resultConsumer, final Consumer<Throwable> errorConsumer);


//    protected abstract DocRef getDocRef(D document);

//    public abstract String getType();

    private CloseHandler createCloseHandler(final SourceTabPresenter sourceTabPresenter) {
        return (CloseCallback closeCallback) -> {
            if (sourceTabPresenter != null) {
                    // Tell the presenter we are closing.
//                    sourceTabPresenter.onClose();
                    // Cleanup reference to this tab data.
                    removeTabData(sourceTabPresenter);
                // Tell the callback to close the tab if ok.
                closeCallback.closeTab(true);
            }
        };
    }

//    private class EntityCloseHandler implements CloseHandler {
//        private final DocumentTabData tabData;
//
//        EntityCloseHandler(final DocumentTabData tabData) {
//            this.tabData = tabData;
//        }
//
//        @Override
//        @SuppressWarnings("unchecked")
//        public void onCloseRequest(final CloseCallback callback) {
//            if (tabData != null) {
//                if (tabData instanceof DocumentEditPresenter<?, ?>) {
//                    final DocumentEditPresenter<?, D> presenter = (DocumentEditPresenter<?, D>) tabData;
//                    if (presenter.isDirty()) {
//                        final DocRef docRef = getDocRef(presenter.getEntity());
//                        ConfirmEvent.fire(SourceTabPlugin.this,
//                                docRef.getType() + " '" + docRef.getName()
//                                        + "' has unsaved changes. Are you sure you want to close this item?",
//                                result -> actuallyClose(tabData, callback, presenter, result));
//                    } else {
//                        actuallyClose(tabData, callback, presenter, true);
//                    }
//                } else {
//                    // Cleanup reference to this tab data.
//                    removeTabData(tabData);
//                    // Tell the callback to close the tab.
//                    callback.closeTab(true);
//                }
//            }
//        }
//
//        private void actuallyClose(final DocumentTabData tabData, final CloseCallback callback,
//                                   final DocumentEditPresenter<?, D> presenter, final boolean ok) {
//            if (ok) {
//                // Tell the presenter we are closing.
//                presenter.onClose();
//                // Cleanup reference to this tab data.
//                removeTabData(tabData);
//            }
//            // Tell the callback to close the tab if ok.
//            callback.closeTab(ok);
//        }
//    }

}
