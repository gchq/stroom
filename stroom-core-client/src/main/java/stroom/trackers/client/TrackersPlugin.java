/*
 *
 *  * Copyright 2018 Crown Copyright
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package stroom.trackers.client;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.AlertEvent;
import stroom.cell.clickable.client.Hyperlink;
import stroom.cell.clickable.client.HyperlinkTarget;
import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.node.client.ClientPropertyCache;
import stroom.node.client.NodeToolsPlugin;
import stroom.node.shared.ClientProperties;
import stroom.security.client.ClientSecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.widget.iframe.client.presenter.IFrameContentPresenter;
import stroom.widget.menu.client.presenter.IconMenuItem;

public class TrackersPlugin extends NodeToolsPlugin {

    private final Provider<IFrameContentPresenter> presenterProvider;
    private final ContentManager contentManager;
    private final ClientPropertyCache clientPropertyCache;

    @Inject
    public TrackersPlugin(final EventBus eventBus,
                          final ClientSecurityContext securityContext,
                          final Provider<IFrameContentPresenter> presenterProvider,
                          final ContentManager contentManager,
                          final ClientPropertyCache clientPropertyCache) {
        super(eventBus, securityContext);
        this.presenterProvider = presenterProvider;
        this.contentManager = contentManager;
        this.clientPropertyCache = clientPropertyCache;
    }

    @Override
    protected void addChildItems(BeforeRevealMenubarEvent event) {
        //TODO what are the correct permissions?
        if (getSecurityContext().hasAppPermission(PermissionNames.MANAGE_JOBS_PERMISSION)) {
            clientPropertyCache.get()
                    .onSuccess(result -> {
                        final IconMenuItem streamTaskMenuItem;
                        final SvgPreset icon = SvgPresets.MONITORING;
                        final String stroomUi = result.get(ClientProperties.STROOM_UI_URL) + "/trackers";
                        if (stroomUi != null && stroomUi.trim().length() > 0) {
                            streamTaskMenuItem = new IconMenuItem(5, icon, null, "Stream Tasks", null, true, () -> {
                                final Hyperlink hyperlink = new Hyperlink.HyperlinkBuilder()
                                        .title("Stream Tasks")
                                        .href(stroomUi)
                                        .target(HyperlinkTarget.STROOM_TAB)
                                        .build();
                                final IFrameContentPresenter presenter = presenterProvider.get();
                                presenter.setHyperlink(hyperlink);
                                presenter.setIcon(icon);
                                contentManager.open(
                                        callback -> {
                                            callback.closeTab(true);
                                            presenter.close();
                                        },
                                        presenter, presenter);
                            });
                        } else {
                            streamTaskMenuItem = new IconMenuItem(5, icon, icon, "Stream Tasks is not configured!", null, false, null);
                        }
                        event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU, streamTaskMenuItem);
                    })
                    .onFailure(caught -> AlertEvent.fireError(TrackersPlugin.this, caught.getMessage(), null));
        }
    }
}
