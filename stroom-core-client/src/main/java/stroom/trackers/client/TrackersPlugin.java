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

package stroom.trackers.client;

import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.node.client.NodeToolsPlugin;
import stroom.security.client.api.ClientSecurityContext;
import stroom.ui.config.client.UiConfigCache;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class TrackersPlugin extends NodeToolsPlugin {

    private final UiConfigCache clientPropertyCache;

    @Inject
    public TrackersPlugin(final EventBus eventBus,
                          final ClientSecurityContext securityContext,
                          final UiConfigCache clientPropertyCache) {
        super(eventBus, securityContext);
        this.clientPropertyCache = clientPropertyCache;
    }

    @Override
    protected void addChildItems(final BeforeRevealMenubarEvent event) {
//        //TODO what are the correct permissions?
//        if (getSecurityContext().hasAppPermission(AppPermissionEnum.MANAGE_JOBS_PERMISSION)) {
//            clientPropertyCache.get()
//                    .onSuccess(result -> {
//                        final IconMenuItem streamTaskMenuItem;
//                        final SvgPreset icon = SvgPresets.MONITORING;
//                        final String stroomUi = result.getUrl().getTrackers();
//                        if (stroomUi != null && stroomUi.trim().length() > 0) {
//                            streamTaskMenuItem = new IconMenuItem(5, icon, null, "Stream Tasks", null, true, () -> {
//                                final Hyperlink hyperlink = new Builder()
//                                        .text("Stream Tasks")
//                                        .href(stroomUi)
//                                        .type(HyperlinkType.TAB.name().toLowerCase())
//                                        .icon(icon)
//                                        .build();
//                                HyperlinkEvent.fire(this, hyperlink);
//                            });
//                        } else {
//                            streamTaskMenuItem = new IconMenuItem(
//                            5, icon, icon, "Stream Tasks is not configured!", null, false, null);
//                        }
//                        event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU, streamTaskMenuItem);
//                    })
//                    .onFailure(caught -> AlertEvent.fireError(TrackersPlugin.this, caught.getMessage(), null));
//        }
    }
}
