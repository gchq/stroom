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


package stroom.gwt.workbench.client.widgets.menu;

import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.IconParentMenuItem;
import stroom.widget.menu.client.presenter.InfoMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.Separator;
import stroom.widget.menu.client.presenter.SimpleMenuItem;
import stroom.widget.menu.client.presenter.SimpleParentMenuItem;

import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.Command;

import java.util.Arrays;
import java.util.List;

/// Builds Stroom's menu items for the `Widgets/Menu/*` stories.
final class MenuWidgets {

    private MenuWidgets() {
        // Static utility
    }

    /// An item with an icon and a command.
    ///
    /// @param text    The item's text.
    /// @param icon    The item's icon.
    /// @param command What choosing it does.
    /// @return The item's builder, for options such as `action` (the shortcut) or `highlight`.
    static IconMenuItem.Builder icon(final String text, final SvgImage icon, final Command command) {
        return new IconMenuItem.Builder()
                .text(text)
                .icon(icon)
                .command(command);
    }

    /// A disabled item with an icon.
    ///
    /// @param text The item's text.
    /// @param icon The item's icon.
    /// @return The item's builder.
    static IconMenuItem.Builder iconDisabled(final String text, final SvgImage icon) {
        return new IconMenuItem.Builder()
                .text(text)
                .icon(icon)
                .disabledIcon(icon)
                .enabled(false);
    }

    /// An item with no icon.
    ///
    /// @param text    The item's text.
    /// @param command What choosing it does.
    /// @return The item.
    static Item simple(final String text, final Command command) {
        return new SimpleMenuItem.Builder()
                .text(text)
                .command(command)
                .build();
    }

    /// A row of information, which can't be chosen.
    ///
    /// @param text The row's text.
    /// @return The item.
    static Item info(final String text) {
        return InfoMenuItem.builder()
                .text(SafeHtmlUtils.fromString(text))
                .build();
    }

    /// A separator.
    ///
    /// @return The item.
    static Item separator() {
        return new Separator(0);
    }

    /// An item that opens a submenu of its children.
    ///
    /// @param text     The item's text.
    /// @param icon     The item's icon, or null for none.
    /// @param children The submenu's items.
    /// @return The item.
    static Item parent(final String text, final SvgImage icon, final Item... children) {
        final List<Item> list = Arrays.asList(children);
        if (icon == null) {
            return new SimpleParentMenuItem(0, SafeHtmlUtils.fromString(text), null, list);
        }
        return new IconParentMenuItem.Builder()
                .text(text)
                .icon(icon)
                .children(list)
                .build();
    }
}
