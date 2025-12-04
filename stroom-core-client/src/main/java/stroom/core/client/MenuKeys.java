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

package stroom.core.client;

import stroom.widget.menu.client.presenter.KeyedParentMenuItem;
import stroom.widget.menu.client.presenter.MenuItems;
import stroom.widget.menu.client.presenter.MenuKey;

public class MenuKeys {

    public static final MenuKey MAIN_MENU = new MenuKey("Main Menu");
    public static final MenuKey NAVIGATION_MENU = new MenuKey("Navigation Menu");
    public static final MenuKey ADMINISTRATION_MENU = new MenuKey("Administration Menu");
    public static final MenuKey ANNOTATION_MENU = new MenuKey("Annotation Menu");
    public static final MenuKey TOOLS_MENU = new MenuKey("Tools Menu");
    public static final MenuKey SECURITY_MENU = new MenuKey("Security Menu");
    public static final MenuKey MONITORING_MENU = new MenuKey("Monitoring Menu");
    public static final MenuKey USER_MENU = new MenuKey("User Menu");
    public static final MenuKey HELP_MENU = new MenuKey("Help Menu");

    public static void addAdministrationMenu(final MenuItems menuItems) {
        menuItems.addMenuItem(
                MenuKeys.MAIN_MENU,
                new KeyedParentMenuItem.Builder()
                        .priority(1)
                        .text("Administration")
                        .menuItems(menuItems)
                        .menuKey(MenuKeys.ADMINISTRATION_MENU)
                        .build());
    }

    public static void addAnnotationMenu(final MenuItems menuItems) {
        menuItems.addMenuItem(
                MenuKeys.MAIN_MENU,
                new KeyedParentMenuItem.Builder()
                        .priority(1)
                        .text("Annotations")
                        .menuItems(menuItems)
                        .menuKey(MenuKeys.ANNOTATION_MENU)
                        .build());
    }

    public static void addSecurityMenu(final MenuItems menuItems) {
        menuItems.addMenuItem(
                MenuKeys.MAIN_MENU,
                new KeyedParentMenuItem.Builder()
                        .priority(8)
                        .text("Security")
                        .menuItems(menuItems)
                        .menuKey(MenuKeys.SECURITY_MENU)
                        .build());
    }

    public static void addUserMenu(final MenuItems menuItems) {
        menuItems.addMenuItem(MenuKeys.MAIN_MENU,
                new KeyedParentMenuItem.Builder()
                        .priority(10)
                        .text("User")
                        .menuItems(menuItems)
                        .menuKey(MenuKeys.USER_MENU)
                        .build());
    }

    public static void addHelpMenu(final MenuItems menuItems) {
        menuItems.addMenuItem(MenuKeys.MAIN_MENU,
                new KeyedParentMenuItem.Builder()
                        .priority(100)
                        .text("Help")
                        .menuItems(menuItems)
                        .menuKey(MenuKeys.HELP_MENU)
                        .build());
    }

    public static void addNavigationMenu(final MenuItems menuItems) {
        menuItems.addMenuItem(
                MenuKeys.MAIN_MENU,
                new KeyedParentMenuItem.Builder()
                        .priority(6)
                        .text("Navigation")
                        .menuItems(menuItems)
                        .menuKey(MenuKeys.NAVIGATION_MENU)
                        .build());
    }
}
