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
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.AlertEvent;
import stroom.core.client.MenuKeys;
import stroom.hyperlink.client.Hyperlink;
import stroom.hyperlink.client.HyperlinkEvent;
import stroom.hyperlink.client.HyperlinkType;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.config.global.client.presenter.ManageGlobalPropertyPresenter;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

public class ManageNodeToolsPlugin extends NodeToolsPlugin {
    private final Provider<ManageGlobalPropertyPresenter> manageGlobalPropertyPresenter;

    private final UiConfigCache clientPropertyCache;

    @Inject
    public ManageNodeToolsPlugin(final EventBus eventBus,
                                 final ClientSecurityContext securityContext,
                                 final UiConfigCache clientPropertyCache,
                                 final Provider<ManageGlobalPropertyPresenter> manageGlobalPropertyPresenter) {
        super(eventBus, securityContext);
        this.manageGlobalPropertyPresenter = manageGlobalPropertyPresenter;
        this.clientPropertyCache = clientPropertyCache;
    }

    @Override
    protected void addChildItems(final BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(PermissionNames.MANAGE_VOLUMES_PERMISSION)) {
            clientPropertyCache.get()
                .onSuccess(result -> {
                    final IconMenuItem usersMenuItem;
                    final SvgPreset icon = SvgPresets.VOLUMES;
                    final String indexVolumesUiUrl = result.getUrlConfig().getIndexVolumes();
                    if (indexVolumesUiUrl != null && indexVolumesUiUrl.trim().length() > 0) {
                        usersMenuItem = new IconMenuItem(4, icon, null, "Index Volumes", null, true, () -> {
                            final Hyperlink hyperlink = new Hyperlink.Builder()
                                    .text("Index Volumes")
                                    .href(indexVolumesUiUrl)
                                    .type(HyperlinkType.TAB + "|Index Volumes")
                                    .icon(icon)
                                    .build();
                            HyperlinkEvent.fire(this, hyperlink);
                        });
                    } else {
                        usersMenuItem = new IconMenuItem(5, icon, icon, "Index Volumes UI is not configured!", null, false, null);
                    }

                    event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU, usersMenuItem);

                })
                .onFailure(caught -> AlertEvent.fireError(ManageNodeToolsPlugin.this, caught.getMessage(), null));
        }
        if (getSecurityContext().hasAppPermission(PermissionNames.MANAGE_PROPERTIES_PERMISSION)) {
            event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU,
                    new IconMenuItem(90, SvgPresets.PROPERTIES, SvgPresets.PROPERTIES, "Properties", null, true, () -> {
                        final PopupSize popupSize = new PopupSize(1000, 600, true);
                        ShowPopupEvent.fire(ManageNodeToolsPlugin.this, manageGlobalPropertyPresenter.get(),
                                PopupType.CLOSE_DIALOG, null, popupSize, "System Properties", null, null);
                    }));
        }
    }
}
