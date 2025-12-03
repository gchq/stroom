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

package stroom.widget.menu.client.presenter;

import stroom.widget.menu.client.presenter.SimpleMenuItem.Builder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MenuBuilder {

    private final List<Item> items = new ArrayList<>();

    private MenuBuilder() {
    }

    public static MenuBuilder builder() {

        return new MenuBuilder();
    }

    public boolean hasItems() {
        return !items.isEmpty();
    }

    private int getNextPriority() {
        return items.size();
    }

    public MenuBuilder withItem(final Item item) {
        items.add(item);
        return this;
    }

    public MenuBuilder withItemIf(final boolean condition, final Item item) {
        if (condition) {
            items.add(item);
        }
        return this;
    }

    public MenuBuilder withIconMenuItem(final Consumer<IconMenuItem.Builder> iconMenuItemBuilder) {
        return withIconMenuItemIf(true, iconMenuItemBuilder);
    }

    /**
     * @param condition Allows you to build conditionally in a fluent style, i.e. only add an item
     *                  if a condition is met.
     */
    public MenuBuilder withIconMenuItemIf(final boolean condition,
                                          final Consumer<IconMenuItem.Builder> iconMenuItemBuilder) {
        if (condition) {
            final IconMenuItem.Builder builder = new IconMenuItem.Builder()
                    .priority(getNextPriority());
            iconMenuItemBuilder.accept(builder);
            return withItem(builder.build());
        } else {
            return this;
        }
    }

    public MenuBuilder withSimpleMenuItem(final Consumer<SimpleMenuItem.Builder> simpleMenuItemBuilder) {
        return withSimpleMenuItemIf(true, simpleMenuItemBuilder);
    }

    /**
     * @param condition Allows you to build conditionally in a fluent style, i.e. only add an item
     *                  if a condition is met.
     */
    public MenuBuilder withSimpleMenuItemIf(final boolean condition,
                                            final Consumer<SimpleMenuItem.Builder> simpleMenuItemBuilder) {
        if (condition) {
            final SimpleMenuItem.Builder builder = new Builder()
                    .priority(getNextPriority());
            simpleMenuItemBuilder.accept(builder);
            return withItem(builder.build());
        } else {
            return this;
        }
    }

    public MenuBuilder withInfoMenuItem(final Consumer<InfoMenuItem.Builder> infoMenuItemBuilder) {
        return withInfoMenuItemIf(true, infoMenuItemBuilder);
    }

    /**
     * @param condition Allows you to build conditionally in a fluent style, i.e. only add an item
     *                  if a condition is met.
     */
    public MenuBuilder withInfoMenuItemIf(final boolean condition,
                                          final Consumer<InfoMenuItem.Builder> infoMenuItemBuilder) {
        if (condition) {
            final InfoMenuItem.Builder builder = InfoMenuItem.builder();
            infoMenuItemBuilder.accept(builder);
            return withItem(builder.build());
        } else {
            return this;
        }
    }

    public MenuBuilder withSeparator() {
        return withItem(new Separator(getNextPriority()));
    }

    public MenuBuilder withSeparatorIf(final boolean condition) {
        return withItemIf(condition, new Separator(getNextPriority()));
    }

    public List<Item> build() {
        return items;
    }
}
