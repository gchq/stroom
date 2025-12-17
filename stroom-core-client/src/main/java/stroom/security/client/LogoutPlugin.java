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

import stroom.alert.client.event.ConfirmEvent;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.Plugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.api.event.RequestLogoutEvent;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Separator;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class LogoutPlugin extends Plugin {

    @Inject
    public LogoutPlugin(final EventBus eventBus) {
        super(eventBus);
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        super.onReveal(event);

        MenuKeys.addUserMenu(event.getMenuItems());
        event.getMenuItems().addMenuItem(MenuKeys.USER_MENU, new Separator(30));
        event.getMenuItems().addMenuItem(MenuKeys.USER_MENU,
                new IconMenuItem.Builder()
                        .priority(40)
                        .icon(SvgImage.LOGOUT)
                        .text("Logout")
                        .command(() ->
                                ConfirmEvent.fire(LogoutPlugin.this, "Are you sure you want to logout?", result -> {
                                    if (result) {
                                        RequestLogoutEvent.fire(LogoutPlugin.this);
                                    }
                                }))
                        .build());
    }
}
