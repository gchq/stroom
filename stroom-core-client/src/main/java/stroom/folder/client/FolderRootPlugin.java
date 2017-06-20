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

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.app.client.ContentManager;
import stroom.app.client.ContentManager.CloseHandler;
import stroom.app.client.presenter.Plugin;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.EntityPluginEventManager;
import stroom.entity.shared.FolderService;
import stroom.explorer.client.event.ExplorerTreeSelectEvent;
import stroom.explorer.client.presenter.ExplorerTreePresenter;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.ExplorerData;
import stroom.security.client.ClientSecurityContext;
import stroom.streamstore.shared.Stream;
import stroom.streamtask.shared.StreamProcessor;
import stroom.svg.client.SvgIcon;
import stroom.util.client.ImageUtil;
import stroom.svg.client.Icon;
import stroom.widget.tab.client.presenter.ImageIcon;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.util.client.SelectionType;

public class FolderRootPlugin extends Plugin implements TabData {
    private final ContentManager contentManager;
    private final Provider<FolderRootPresenter> editorProvider;
    private final ClientSecurityContext securityContext;

    private FolderRootPresenter presenter;

    @Inject
    public FolderRootPlugin(final EventBus eventBus, final ExplorerTreePresenter explorerTreePresenter,
                            final Provider<FolderRootPresenter> editorProvider, final ClientDispatchAsync dispatcher, final ClientSecurityContext securityContext,
                            final ContentManager contentManager, final EntityPluginEventManager entityPluginEventManager) {
        super(eventBus);
        this.contentManager = contentManager;
        this.editorProvider = editorProvider;
        this.securityContext = securityContext;
    }

    @Override
    protected void onBind() {
        super.onBind();

        // 4. Handle explorer events and open items as required.
        registerHandler(
                getEventBus().addHandler(ExplorerTreeSelectEvent.getType(), event -> {
                    final SelectionType selectionType = event.getSelectionType();
                    if (!selectionType.isRightClick() && !selectionType.isMultiSelect()) {
                        final ExplorerData selected = event.getSelectionModel().getSelected();
                        if (selected != null && FolderService.ROOT.equals(selected.getType())) {
                            if (presenter == null && selectionType.isDoubleSelect()) {
                                // If the presenter is null then we haven't got
                                // this tab open.
                                // Create a new presenter.
                                presenter = createEditor();
                            }

                            if (presenter != null) {
                                final CloseHandler closeHandler = callback -> {
                                    // Give the content manager the ok to
                                    // close the tab.
                                    callback.closeTab(true);

                                    // After we close the tab set the
                                    // presenter back to null so
                                    // that we can open it again.
                                    presenter = null;
                                };

                                contentManager.open(closeHandler, presenter, presenter);
                            }
                        }
                    }
                }));
    }

    private FolderRootPresenter createEditor() {
        if (securityContext.hasAppPermission(Stream.VIEW_DATA_PERMISSION) || securityContext.hasAppPermission(StreamProcessor.MANAGE_PROCESSORS_PERMISSION)) {
            return editorProvider.get();
        }

        return null;
    }

    @Override
    public Icon getIcon() {
        return new SvgIcon(ImageUtil.getImageURL() + DocumentType.DOC_IMAGE_URL + FolderService.ROOT, 18, 18);
    }

    @Override
    public String getLabel() {
        return FolderService.ROOT;
    }

    @Override
    public boolean isCloseable() {
        return true;
    }
}
