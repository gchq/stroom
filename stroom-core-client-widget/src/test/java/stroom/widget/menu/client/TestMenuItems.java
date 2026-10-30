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

package stroom.widget.menu.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestMenuItems {

    private static final MenuKey MAIN = new MenuKey("Main");
    private static final MenuKey ADMIN = new MenuKey("Admin");
    private static final MenuKey MONITORING = new MenuKey("Monitoring");
    private static final MenuKey NESTED = new MenuKey("Nested");

    @Test
    void subMenuWithItems_isShown() {
        final MenuItems menuItems = new MenuItems();
        menuItems.addMenuItem(MAIN, parent(menuItems, ADMIN, "Administration", 1));
        menuItems.addMenuItem(ADMIN, item("Properties", 1));

        assertThat(texts(menuItems.getMenuItems(MAIN))).containsExactly("Administration");
    }

    @Test
    void subMenuWithNoItems_isLeftOut() {
        // As a plugin that adds the Administration sub menu but puts its own item elsewhere
        final MenuItems menuItems = new MenuItems();
        menuItems.addMenuItem(MAIN, parent(menuItems, ADMIN, "Administration", 1));
        menuItems.addMenuItem(MAIN, parent(menuItems, MONITORING, "Monitoring", 3));
        menuItems.addMenuItem(MONITORING, item("Node Groups", 10));

        assertThat(texts(menuItems.getMenuItems(MAIN))).containsExactly("Monitoring");
    }

    @Test
    void subMenuWithOnlySeparators_isLeftOut() {
        final MenuItems menuItems = new MenuItems();
        menuItems.addMenuItem(MAIN, parent(menuItems, ADMIN, "Administration", 1));
        menuItems.addMenuItem(MAIN, item("About", 2));
        menuItems.addMenuItem(ADMIN, new Separator(1));

        assertThat(texts(menuItems.getMenuItems(MAIN))).containsExactly("About");
    }

    @Test
    void subMenuWhoseOnlyItemIsAnEmptySubMenu_isLeftOut() {
        final MenuItems menuItems = new MenuItems();
        menuItems.addMenuItem(MAIN, parent(menuItems, ADMIN, "Administration", 1));
        menuItems.addMenuItem(MAIN, item("About", 2));
        menuItems.addMenuItem(ADMIN, parent(menuItems, NESTED, "Nested", 1));

        assertThat(texts(menuItems.getMenuItems(MAIN))).containsExactly("About");
        assertThat(menuItems.getMenuItems(ADMIN)).isEmpty();
    }

    @Test
    void subMenuThatContainsItself_isLeftOut() {
        final MenuItems menuItems = new MenuItems();
        menuItems.addMenuItem(MAIN, parent(menuItems, ADMIN, "Administration", 1));
        menuItems.addMenuItem(MAIN, item("About", 2));
        menuItems.addMenuItem(ADMIN, parent(menuItems, ADMIN, "Again", 1));

        assertThat(texts(menuItems.getMenuItems(MAIN))).containsExactly("About");
    }

    @Test
    void separatorsLeftAtTheEnds_areRemoved() {
        final MenuItems menuItems = new MenuItems();
        menuItems.addMenuItem(MAIN, item("About", 1));
        menuItems.addMenuItem(MAIN, new Separator(2));
        menuItems.addMenuItem(MAIN, parent(menuItems, ADMIN, "Administration", 3));

        assertThat(texts(menuItems.getMenuItems(MAIN))).containsExactly("About");
    }

    private static Item parent(final MenuItems menuItems,
                               final MenuKey menuKey,
                               final String text,
                               final int priority) {
        return new KeyedParentMenuItem.Builder()
                .priority(priority)
                .text(text)
                .menuItems(menuItems)
                .menuKey(menuKey)
                .build();
    }

    private static Item item(final String text, final int priority) {
        return new IconMenuItem.Builder()
                .priority(priority)
                .text(text)
                .command(() -> {
                })
                .build();
    }

    private static List<String> texts(final List<Item> items) {
        return items.stream()
                .map(item -> item instanceof final MenuItem menuItem
                        ? menuItem.getText().asString()
                        : "-")
                .toList();
    }
}
