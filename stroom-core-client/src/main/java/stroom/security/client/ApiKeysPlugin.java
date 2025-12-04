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

package stroom.security.client;

import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.MonitoringPlugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.event.OpenApiKeysScreenEvent;
import stroom.security.client.presenter.ApiKeysPresenter;
import stroom.security.shared.AppPermission;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class ApiKeysPlugin extends MonitoringPlugin<ApiKeysPresenter> {

    public static final String SCREEN_NAME = "Manage API Keys";
    public static final Preset ICON = SvgPresets.KEY;

    @Inject
    public ApiKeysPlugin(final EventBus eventBus,
                         final ContentManager eventManager,
                         final ClientSecurityContext securityContext,
                         final Provider<ApiKeysPresenter> apiKeysPresenterAsyncProvider) {
        super(eventBus, eventManager, apiKeysPresenterAsyncProvider, securityContext);

        registerHandler(getEventBus().addHandler(OpenApiKeysScreenEvent.getType(), event -> {
            open(apiKeysPresenter ->
                    apiKeysPresenter.showOwner(event.getUserRef()));
        }));
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
}
