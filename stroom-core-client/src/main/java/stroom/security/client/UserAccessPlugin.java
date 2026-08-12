/*
 * Copyright 2016-2026 Crown Copyright
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
import stroom.document.client.DocumentPluginRegistry;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.UserAccessPresenter;
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
public class UserAccessPlugin extends MonitoringPlugin<UserAccessPresenter> {

    public static final String SCREEN_NAME = "User Access";
    public static final Preset ICON = SvgPresets.SHIELD;

    @Inject
    public UserAccessPlugin(final EventBus eventBus,
                            final ContentManager eventManager,
                            final ClientSecurityContext securityContext,
                            final Provider<UserAccessPresenter> userAccessPresenterProvider,
                            final DocumentPluginRegistry documentPluginRegistry) {
        super(eventBus, eventManager, userAccessPresenterProvider, securityContext, documentPluginRegistry);
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
        // Same gate as the server enforces on both find and revoke, so the screen is never offered to someone
        // who would only be refused by every call it makes.
        return AppPermission.MANAGE_USERS_PERMISSION;
    }

    @Override
    protected Action getOpenAction() {
        return Action.GOTO_USER_ACCESS;
    }

    private void addMenuItem(final BeforeRevealMenubarEvent event) {
        final IconMenuItem userAccessMenuItem = new IconMenuItem.Builder()
                .priority(65)
                .icon(ICON)
                .text(SCREEN_NAME)
                .action(getOpenAction())
                .command(this::open)
                .build();
        event.getMenuItems().addMenuItem(MenuKeys.SECURITY_MENU, userAccessMenuItem);
    }

    @Override
    public String getType() {
        return UserAccessPresenter.TAB_TYPE;
    }
}
