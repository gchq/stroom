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
import stroom.core.client.MenuKeys;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.config.global.client.presenter.ManageGlobalPropertyPresenter;
import stroom.node.client.presenter.ManageVolumesPresenter;
import stroom.security.client.ClientSecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.svg.client.SvgPresets;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

public class ManageNodeToolsPlugin extends NodeToolsPlugin {
    private final Provider<ManageVolumesPresenter> manageVolumesPresenter;
    private final Provider<ManageGlobalPropertyPresenter> manageGlobalPropertyPresenter;

    @Inject
    public ManageNodeToolsPlugin(final EventBus eventBus, final ClientSecurityContext securityContext, final Provider<ManageVolumesPresenter> manageVolumesPresenter,
                                 final Provider<ManageGlobalPropertyPresenter> manageGlobalPropertyPresenter) {
        super(eventBus, securityContext);
        this.manageGlobalPropertyPresenter = manageGlobalPropertyPresenter;
        this.manageVolumesPresenter = manageVolumesPresenter;
    }

    @Override
    protected void addChildItems(final BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(PermissionNames.MANAGE_VOLUMES_PERMISSION)) {
            event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU,
                    new IconMenuItem(4, SvgPresets.VOLUMES, SvgPresets.VOLUMES, "Volumes", null, true, () -> {
                        final PopupSize popupSize = new PopupSize(1000, 600, true);
                        ShowPopupEvent.fire(ManageNodeToolsPlugin.this, manageVolumesPresenter.get(),
                                PopupType.CLOSE_DIALOG, null, popupSize, "Volumes", null, null);
                    }));
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
