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
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.tab.client.presenter.TabData;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;
import javax.inject.Singleton;

@Singleton
public class FolderRootPlugin extends DocumentPlugin<DocRef> implements TabData {
    private final ContentManager contentManager;
    private final Provider<FolderRootPresenter> editorProvider;
    private final ClientSecurityContext securityContext;

    @Inject
    public FolderRootPlugin(final EventBus eventBus,
                            final Provider<FolderRootPresenter> editorProvider,
                            final ContentManager contentManager,
                            final DocumentPluginEventManager entityPluginEventManager,
                            final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, entityPluginEventManager, securityContext);
        this.contentManager = contentManager;
        this.editorProvider = editorProvider;
        this.securityContext = securityContext;
    }

//    @Override
//    protected void onBind() {
//        super.onBind();
//
//        // 4. Handle explorer events and open items as required.
//        registerHandler(
//                getEventBus().addHandler(ExplorerTreeSelectEvent.getType(), event -> {
//                    final SelectionType selectionType = event.getSelectionType();
//                    if (!selectionType.isRightClick() && !selectionType.isMultiSelect()) {
//                        final ExplorerNode selected = event.getSelectionModel().getSelected();
//                        if (selected != null && ExplorerConstants.SYSTEM.equals(selected.getType())) {
//                            if (presenter == null && selectionType.isDoubleSelect()) {
//                                // If the presenter is null then we haven't got
//                                // this tab open.
//                                // Create a new presenter.
//                                presenter = createEditor();
//                            }
//
//                            if (presenter != null) {
//                                final CloseContentEvent.Handler closeHandler = evt -> {
//                                    // Give the content manager the ok to
//                                    // close the tab.
//                                    evt.getCallback().closeTab(true);
//
//                                    // After we close the tab set the
//                                    // presenter back to null so
//                                    // that we can open it again.
//                                    presenter = null;
//                                };
//                                contentManager.open(closeHandler, presenter, presenter);
//                            }
//                        }
//                    }
//                }));
//    }

    protected FolderRootPresenter createEditor() {
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
        // Root folder is just a constant so no load needed
//        resultConsumer.accept(ExplorerConstants.SYSTEM_DOC_REF);
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
                                final MyPresenterWidget<?> myPresenterWidget,
                                final Handler closeHandler,
                                final DocumentTabData tabData,
                                final boolean fullScreen,
                                final CommonDocLinkTab selectedTab,
                                final Consumer<MyPresenterWidget<?>> callbackOnOpen,
                                final TaskMonitorFactory taskMonitorFactory) {
        if (myPresenterWidget instanceof FolderRootPresenter) {
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
