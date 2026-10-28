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

package stroom.preferences.client;

import stroom.core.client.MenuKeys;
import stroom.core.client.Plugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.preferences.client.component.KeyboardShortcutsController;
import stroom.preferences.client.component.UserPreferencesDialogController;
import stroom.svg.shared.SvgImage;
import stroom.widget.component.client.AppRoot;
import stroom.widget.menu.client.IconMenuItem;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class UserPreferencesPlugin extends Plugin {

    private final Provider<UserPreferencesDialogController> preferencesProvider;
    private final Provider<KeyboardShortcutsController> keyboardShortcutsProvider;

    private final AppRoot appRoot;

    @Inject
    public UserPreferencesPlugin(final EventBus eventBus,
                                 final Provider<UserPreferencesDialogController> preferencesProvider,
                                 final Provider<KeyboardShortcutsController> keyboardShortcutsProvider,
                                 final AppRoot appRoot) {
        super(eventBus);
        this.appRoot = appRoot;
        this.preferencesProvider = preferencesProvider;
        this.keyboardShortcutsProvider = keyboardShortcutsProvider;

        KeyBinding.addCommand(getOpenAction(), this::open);
        KeyBinding.addCommand(Action.SHOW_KEY_BINDS, this::showKeyboardShortcuts);
    }

    private Action getOpenAction() {
        return Action.GOTO_USER_PREFERENCES;
    }

    private void open() {
        // Made for one use, and disposed once it is closed
        preferencesProvider.get().show(appRoot);
    }

    private void showKeyboardShortcuts() {
        // Made for one use, and disposed once it is closed
        keyboardShortcutsProvider.get().show(appRoot);
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        super.onReveal(event);

        MenuKeys.addUserMenu(event.getMenuItems());
        event.getMenuItems().addMenuItem(MenuKeys.USER_MENU,
                new IconMenuItem.Builder()
                        .priority(10)
                        .icon(SvgImage.SETTINGS)
                        .text("Preferences")
                        .action(getOpenAction())
                        .command(this::open)
                        .build());
        event.getMenuItems().addMenuItem(MenuKeys.USER_MENU,
                new IconMenuItem.Builder()
                        .priority(11)
                        .icon(SvgImage.HELP_OUTLINE)
                        .text("Keyboard Shortcuts")
                        .action(Action.SHOW_KEY_BINDS)
                        .command(this::showKeyboardShortcuts)
                        .build());
    }
}
