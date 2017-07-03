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

package stroom.security.client.presenter;

import com.google.gwt.user.client.Command;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.app.client.MenuKeys;
import stroom.app.client.presenter.Plugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.CurrentUser;
import stroom.security.client.event.ChangePasswordEvent;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.KeyedParentMenuItem;

public class ChangePasswordPlugin extends Plugin {
    private final CurrentUser currentUser;

    @Inject
    public ChangePasswordPlugin(final EventBus eventBus, final CurrentUser currentUser) {
        super(eventBus);
        this.currentUser = currentUser;
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        if (currentUser != null) {
            event.getMenuItems().addMenuItem(MenuKeys.MAIN_MENU,
                    new KeyedParentMenuItem(4, "User", event.getMenuItems(), MenuKeys.USER_MENU));
            event.getMenuItems().addMenuItem(MenuKeys.USER_MENU, new IconMenuItem(1, GlyphIcons.PASSWORD, GlyphIcons.PASSWORD, "Change Password", null, true, new Command() {
                @Override
                public void execute() {
                    ChangePasswordEvent.fire(ChangePasswordPlugin.this, currentUser.getUserRef(), false);
                }
            }));
        }
    }
}
