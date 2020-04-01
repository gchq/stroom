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

package stroom.data.store.impl.fs.client;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.core.client.MenuKeys;
import stroom.data.store.impl.fs.client.presenter.ManageFSVolumesPresenter;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.node.client.NodeToolsPlugin;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.svg.client.SvgPresets;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

public class ManageFSVolumesPlugin extends NodeToolsPlugin {
    private final Provider<ManageFSVolumesPresenter> manageVolumesPresenter;

    @Inject
    public ManageFSVolumesPlugin(final EventBus eventBus, final ClientSecurityContext securityContext, final Provider<ManageFSVolumesPresenter> manageVolumesPresenter) {
        super(eventBus, securityContext);
        this.manageVolumesPresenter = manageVolumesPresenter;
    }

    @Override
    protected void addChildItems(final BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(PermissionNames.MANAGE_VOLUMES_PERMISSION)) {
            event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU,
                    new IconMenuItem(2, SvgPresets.VOLUMES, SvgPresets.VOLUMES, "Data Volumes", null, true, () -> {
                        final PopupSize popupSize = new PopupSize(1000, 600, true);
                        ShowPopupEvent.fire(ManageFSVolumesPlugin.this, manageVolumesPresenter.get(),
                                PopupType.CLOSE_DIALOG, null, popupSize, "Data Volumes", null, null);
                    }));
        }
    }
}
