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

package stroom.folder.client;

import stroom.core.client.ContentManager;
import stroom.core.client.event.CloseContentEvent.Handler;
import stroom.dispatch.client.RestErrorHandler;
import stroom.docref.DocRef;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.document.client.DocumentTabData;
import stroom.document.client.event.OpenDocumentEvent.CommonDocLinkTab;
import stroom.entity.client.presenter.LinkTabPanelPresenter;
import stroom.explorer.shared.ExplorerConstants;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.task.client.TaskMonitorFactory;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;
import javax.inject.Singleton;

@Singleton
public class FolderPlugin extends DocumentPlugin<DocRef> {

    private final Provider<FolderPresenter> editorProvider;
    private final ClientSecurityContext securityContext;
    private final ContentManager contentManager;

    @Inject
    public FolderPlugin(final EventBus eventBus,
                        final Provider<FolderPresenter> editorProvider,
                        final ContentManager contentManager,
                        final DocumentPluginEventManager entityPluginEventManager,
                        final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, entityPluginEventManager, securityContext);
        this.editorProvider = editorProvider;
        this.securityContext = securityContext;
        this.contentManager = contentManager;
    }

    @Override
    protected MyPresenterWidget<?> createEditor() {
        if (securityContext.hasAppPermission(AppPermission.VIEW_DATA_PERMISSION) ||
            securityContext.hasAppPermission(AppPermission.MANAGE_PROCESSORS_PERMISSION)) {
            return editorProvider.get();
        }

        return null;
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
                                final MyPresenterWidget<?> myPresenterWidget,
                                final Handler closeHandler,
                                final DocumentTabData tabData,
                                final boolean fullScreen,
                                final CommonDocLinkTab selectedTab,
                                final Consumer<MyPresenterWidget<?>> callbackOnOpen,
                                final TaskMonitorFactory taskMonitorFactory) {
        if (myPresenterWidget instanceof FolderPresenter) {
            ((FolderPresenter) myPresenterWidget).read(docRef);
        } else if (myPresenterWidget instanceof FolderRootPresenter) {
            ((FolderRootPresenter) myPresenterWidget).read();
        }

        if (selectedTab != null && myPresenterWidget instanceof LinkTabPanelPresenter) {
            ((LinkTabPanelPresenter) myPresenterWidget).selectCommonTab(selectedTab);
        }

        // Open the tab.
        contentManager.open(closeHandler, tabData, myPresenterWidget);
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
