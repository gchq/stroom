/*
 * Copyright 2026 Crown Copyright
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

package stroom.data.client;

import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.Plugin;
import stroom.data.client.presenter.DataUploadPresenter;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

/// Adds an "Upload Data" item to the Tools menu, so data can be uploaded to any feed
/// without first opening that feed.
@Singleton
public class DataUploadPlugin extends Plugin {

    private final ClientSecurityContext securityContext;
    private final Provider<DataUploadPresenter> dataUploadPresenterProvider;

    @Inject
    public DataUploadPlugin(final EventBus eventBus,
                            final ClientSecurityContext securityContext,
                            final Provider<DataUploadPresenter> dataUploadPresenterProvider) {
        super(eventBus);
        this.securityContext = securityContext;
        this.dataUploadPresenterProvider = dataUploadPresenterProvider;
    }

    /// Adds the "Upload Data" item to the Tools menu if the user can import data.
    ///
    /// @param event The event used to add items to the menu bar.
    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        // Sits with Import (201) and Export (202), after the separator they add at 200.
        if (securityContext.hasAppPermission(AppPermission.IMPORT_DATA_PERMISSION)) {
            event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU,
                    new IconMenuItem.Builder()
                            .priority(203)
                            .icon(SvgImage.UPLOAD)
                            .text("Upload Data")
                            .command(() -> dataUploadPresenterProvider.get().show(null, null))
                            .build());
        }
    }
}
