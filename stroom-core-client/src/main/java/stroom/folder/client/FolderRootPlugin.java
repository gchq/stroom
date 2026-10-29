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
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.component.client.Controller;
import stroom.widget.tab.client.TabData;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;
import javax.inject.Singleton;

@Singleton
public class FolderRootPlugin extends DocumentPlugin<DocRef> implements TabData {
    private final ContentManager contentManager;
    private final EventBus eventBus;
    private final Provider<FolderTabs> folderTabsProvider;
    private final ClientSecurityContext securityContext;

    @Inject
    public FolderRootPlugin(final EventBus eventBus,
                            final Provider<FolderTabs> folderTabsProvider,
                            final ContentManager contentManager,
                            final DocumentPluginEventManager entityPluginEventManager,
                            final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, entityPluginEventManager, securityContext);
        this.contentManager = contentManager;
        this.eventBus = eventBus;
        this.folderTabsProvider = folderTabsProvider;
        this.securityContext = securityContext;
    }

    @Override
    protected DocumentEditorHost<DocRef> createEditor() {
        return FolderTabs.canOpen(securityContext)
                ? new DocumentEditorHost<>(eventBus, folderTabsProvider.get(), ExplorerConstants.SYSTEM_TYPE)
                : null;
    }

    @Override
    public void load(final DocRef docRef,
                     final Consumer<DocRef> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        // The root folder is a constant, so there is nothing to load
    }

    @Override
    public void save(final DocRef docRef,
                     final DocRef document,
                     final Consumer<DocRef> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        // Nothing to do here, root folder is special
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
        FolderPlugin.readFolder(host, ExplorerConstants.SYSTEM_DOC_REF);

        if (selectedTab != null) {
            host.selectCommonTab(selectedTab);
        }

        // Open the tab.
        contentManager.open(closeHandler, tabData, host, host, callbackOnOpen);
    }

    @Override
    protected DocRef getDocRef(final DocRef document) {
        return ExplorerConstants.SYSTEM_DOC_REF;
    }

    @Override
    public String getType() {
        return ExplorerConstants.SYSTEM_TYPE;
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.DOCUMENT_SYSTEM;
    }

    @Override
    public String getLabel() {
        return ExplorerConstants.SYSTEM;
    }

    @Override
    public boolean isCloseable() {
        return true;
    }
}
