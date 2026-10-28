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

package stroom.folder.client;

import stroom.core.client.ContentManager;
import stroom.core.client.event.CloseContentEvent.Handler;
import stroom.dispatch.client.RestErrorHandler;
import stroom.docref.DocRef;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.document.client.DocumentTabData;
import stroom.document.client.event.OpenDocumentEvent.CommonDocLinkTab;
import stroom.entity.client.component.DocumentEditorHost;
import stroom.explorer.shared.ExplorerConstants;
import stroom.security.client.api.ClientSecurityContext;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;
import javax.inject.Singleton;

@Singleton
public class FolderPlugin extends DocumentPlugin<DocRef> {

    private final EventBus eventBus;
    private final Provider<FolderTabs> folderTabsProvider;
    private final ClientSecurityContext securityContext;
    private final ContentManager contentManager;

    @Inject
    public FolderPlugin(final EventBus eventBus,
                        final Provider<FolderTabs> folderTabsProvider,
                        final ContentManager contentManager,
                        final DocumentPluginEventManager entityPluginEventManager,
                        final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, entityPluginEventManager, securityContext);
        this.eventBus = eventBus;
        this.folderTabsProvider = folderTabsProvider;
        this.securityContext = securityContext;
        this.contentManager = contentManager;
    }

    @Override
    protected DocumentEditorHost<?> createEditor() {
        return FolderTabs.canOpen(securityContext)
                ? new DocumentEditorHost<>(eventBus, folderTabsProvider.get(), ExplorerConstants.FOLDER_TYPE)
                : null;
    }

    @Override
    public void load(final DocRef docRef,
                     final Consumer<DocRef> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {

    }

    @Override
    public void save(final DocRef docRef,
                     final DocRef document,
                     final Consumer<DocRef> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {

    }

    @Override
    protected void showDocument(final DocRef docRef,
                                final DocumentEditorHost<?> host,
                                final Handler closeHandler,
                                final DocumentTabData tabData,
                                final boolean fullScreen,
                                final boolean selectDefaultTab,
                                final CommonDocLinkTab selectedTab,
                                final Consumer<Controller> callbackOnOpen,
                                final Runnable callbackOnFailure,
                                final TaskMonitorFactory taskMonitorFactory) {
        // Nothing in a folder's tab can be changed
        readFolder(host, docRef);

        if (selectedTab != null) {
            host.selectCommonTab(selectedTab);
        }

        // Open the tab.
        contentManager.open(closeHandler, tabData, host, host, callbackOnOpen);
    }

    // A folder's document is its reference, as it has nothing to load
    @SuppressWarnings("unchecked")
    static void readFolder(final DocumentEditorHost<?> host, final DocRef folder) {
        host.setReadOnly(true);
        ((DocumentEditorHost<DocRef>) host).read(folder, folder);
    }

    @Override
    protected DocRef getDocRef(final DocRef document) {
        return document;
    }

    @Override
    public String getType() {
        return ExplorerConstants.FOLDER_TYPE;
    }
}
