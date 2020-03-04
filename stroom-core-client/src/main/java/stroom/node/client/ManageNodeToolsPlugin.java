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

package stroom.node.client;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.AlertEvent;
import stroom.core.client.MenuKeys;
import stroom.hyperlink.client.Hyperlink;
import stroom.hyperlink.client.HyperlinkEvent;
import stroom.hyperlink.client.HyperlinkType;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.UiConfig;
import stroom.widget.menu.client.presenter.IconMenuItem;

public class ManageNodeToolsPlugin extends NodeToolsPlugin {

    private final UiConfigCache clientPropertyCache;

    @Inject
    public ManageNodeToolsPlugin(final EventBus eventBus,
                                 final ClientSecurityContext securityContext,
                                 final UiConfigCache clientPropertyCache) {
        super(eventBus, securityContext);
        this.clientPropertyCache = clientPropertyCache;
    }

    @Override
    protected void addChildItems(final BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(PermissionNames.MANAGE_VOLUMES_PERMISSION)) {
            clientPropertyCache.get()
                .onSuccess(uiConfig -> {
                    addIndexVolumesMenuItem(event, uiConfig);
                })
                .onFailure(caught -> AlertEvent.fireError(ManageNodeToolsPlugin.this, caught.getMessage(), null));
        }
    }

    private void addIndexVolumesMenuItem(final BeforeRevealMenubarEvent event,
                                         final UiConfig uiConfig) {
        final IconMenuItem volumeMenuItem;
        final SvgPreset icon = SvgPresets.VOLUMES;
        final String indexVolumesUiUrl = uiConfig.getUrl().getIndexVolumes();
        if (indexVolumesUiUrl != null && indexVolumesUiUrl.trim().length() > 0) {
            volumeMenuItem = new IconMenuItem(4, icon, null, "Index Volumes", null, true, () -> {
                final Hyperlink hyperlink = new Hyperlink.Builder()
                        .text("Index Volumes")
                        .href(indexVolumesUiUrl)
                        .type(HyperlinkType.TAB + "|Index Volumes")
                        .icon(icon)
                        .build();
                HyperlinkEvent.fire(this, hyperlink);
            });

        } else {
            volumeMenuItem = new IconMenuItem(5, icon, icon, "Index Volumes UI is not configured!", null, false, null);
        }
        event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU, volumeMenuItem);
    }
}
