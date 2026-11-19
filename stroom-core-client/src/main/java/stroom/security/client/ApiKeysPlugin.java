/*
 * Copyright 2023 Crown Copyright
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

package stroom.security.client;

import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.core.client.MonitoringPlugin;
import stroom.document.client.DocumentPluginRegistry;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.component.ApiKeysController;
import stroom.security.client.event.OpenApiKeysScreenEvent;
import stroom.security.shared.AppPermission;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.IconMenuItem;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class ApiKeysPlugin extends MonitoringPlugin<ApiKeysController> {

    public static final String SCREEN_NAME = "API Keys";
    public static final SvgImage ICON = SvgImage.KEY;

    @Inject
    public ApiKeysPlugin(final EventBus eventBus,
                         final ContentManager eventManager,
                         final ClientSecurityContext securityContext,
                         final Provider<ApiKeysController> apiKeysControllerAsyncProvider,
                         final DocumentPluginRegistry documentPluginRegistry) {
        super(eventBus, eventManager, apiKeysControllerAsyncProvider, securityContext, documentPluginRegistry);

        getEventBus().addHandler(OpenApiKeysScreenEvent.getType(), event -> {
            open(apiKeysController ->
                    apiKeysController.showOwner(event.getUserRef()));
        });
    }

    @Override
    protected void addChildItems(final BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(getRequiredAppPermission())) {
            MenuKeys.addSecurityMenu(event.getMenuItems());
            addMenuItem(event);
        }
    }

    @Override
    protected AppPermission getRequiredAppPermission() {
        return AppPermission.MANAGE_API_KEYS;
    }

    @Override
    protected Action getOpenAction() {
        return Action.GOTO_API_KEYS;
    }

    private void addMenuItem(final BeforeRevealMenubarEvent event) {
        final IconMenuItem apiKeysMenuItem;
        apiKeysMenuItem = new IconMenuItem.Builder()
                .priority(60)
                .icon(ICON)
                .text(SCREEN_NAME)
                .action(getOpenAction())
                .command(this::open)
                .build();
        event.getMenuItems().addMenuItem(MenuKeys.SECURITY_MENU, apiKeysMenuItem);
    }

    @Override
    public String getType() {
        return ApiKeysController.TAB_TYPE;
    }
}
