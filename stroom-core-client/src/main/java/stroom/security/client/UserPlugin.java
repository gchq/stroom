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
import stroom.security.client.presenter.UserTabPresenter;
import stroom.security.shared.AppPermission;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class UserPlugin extends MonitoringPlugin<UserTabPresenter> {

    private final ClientSecurityContext securityContext;

    @Inject
    public UserPlugin(final EventBus eventBus,
                      final ContentManager contentManager,
                      final Provider<UserTabPresenter> presenterProvider,
                      final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, presenterProvider, securityContext);
        this.securityContext = securityContext;
    }

    @Override
    protected void addChildItems(final BeforeRevealMenubarEvent event) {
        MenuKeys.addUserMenu(event.getMenuItems());
        addMenuItem(event);
    }

    @Override
    protected AppPermission getRequiredAppPermission() {
        return null;
    }

    @Override
    protected Action getOpenAction() {
        return Action.GOTO_USER_PROFILE;
    }

    @Override
    public final void open() {
        open(presenter -> {
            presenter.setUserRef(securityContext.getUserRef());
        });
    }

    private void addMenuItem(final BeforeRevealMenubarEvent event) {
        final IconMenuItem userProfileMenuItem;
        userProfileMenuItem = new IconMenuItem.Builder()
                .priority(20)
                .icon(UserTabPlugin.USER_ICON)
                .text("User Profile")
                .action(getOpenAction())
                .command(this::open)
                .build();
        event.getMenuItems()
                .addMenuItem(MenuKeys.USER_MENU, userProfileMenuItem);
    }
}
