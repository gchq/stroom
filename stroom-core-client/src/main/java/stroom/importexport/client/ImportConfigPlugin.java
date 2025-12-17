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

package stroom.importexport.client;

import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.Plugin;
import stroom.importexport.client.event.ImportConfigEvent;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Separator;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class ImportConfigPlugin extends Plugin {

    private final ClientSecurityContext securityContext;

    @Inject
    public ImportConfigPlugin(final EventBus eventBus,
                              final ClientSecurityContext securityContext) {
        super(eventBus);
        this.securityContext = securityContext;
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        // Add items to the tools menu.
        if (securityContext.hasAppPermission(AppPermission.IMPORT_CONFIGURATION)) {
            event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU, new Separator(200));
            event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU,
                    new IconMenuItem.Builder()
                            .priority(201)
                            .icon(SvgImage.UPLOAD)
                            .text("Import")
                            .command(() -> ImportConfigEvent.fire(ImportConfigPlugin.this))
                            .build());
        }
    }
}
