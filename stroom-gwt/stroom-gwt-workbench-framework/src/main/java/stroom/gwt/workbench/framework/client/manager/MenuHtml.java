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

package stroom.gwt.workbench.framework.client.manager;

import stroom.gwt.workbench.framework.client.BrowserUtil;
import stroom.gwt.workbench.framework.client.shortcuts.KeyCombo;

import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.UriUtils;

import java.util.List;

/// Renders the workbench's menus and the bits of markup shared by the manager's views.
public final class MenuHtml {

    /// The suffix added to an item's id when its secondary action is clicked.
    public static final String SECONDARY_SUFFIX = ":secondary";

    private MenuHtml() {
        // Static utility
    }

    /// @param groups The groups of items, separated by lines.
    /// @return The markup of the menu.
    public static SafeHtml render(final List<List<MenuItem>> groups) {
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        // Only leave room for icons if any item has one, as in Storybook
        final boolean hasIcons = groups.stream()
                .flatMap(List::stream)
                .anyMatch(item -> item.getIcon() != null || item.isToggle());
        builder.appendHtmlConstant("<div class=\"wbm-menu\" role=\"menu\">");
        for (final List<MenuItem> group : groups) {
            builder.appendHtmlConstant("<ul class=\"wbm-menu__group\">");
            for (final MenuItem item : group) {
                renderItem(builder, item, hasIcons);
            }
            builder.appendHtmlConstant("</ul>");
        }
        builder.appendHtmlConstant("</div>");
        return builder.toSafeHtml();
    }

    private static void renderItem(final SafeHtmlBuilder builder, final MenuItem item, final boolean hasIcons) {
        builder.appendHtmlConstant("<li class=\"wbm-menu__item"
                                   + (item.isActive()
                ? " wbm-menu__item--active"
                : "")
                                   + "\">");
        if (item.getExternalUrl() != null) {
            builder.appendHtmlConstant("<a class=\"wbm-menu__button\" role=\"menuitem\" target=\"_blank\" "
                                       + "rel=\"noopener noreferrer\" href=\"")
                    .appendEscaped(UriUtils.fromString(item.getExternalUrl()).asString())
                    .appendHtmlConstant("\" ");
        } else {
            builder.appendHtmlConstant("<button type=\"button\" class=\"wbm-menu__button\" role=\"menuitem\" ");
        }
        builder.appendHtmlConstant(MenuItem.ATTR_ID + "=\"").appendEscaped(item.getId())
                .appendHtmlConstant("\">");

        if (hasIcons) {
            builder.appendHtmlConstant("<span class=\"wbm-menu__icon\"");
            if (HtmlTokens.isSafeColour(item.getIconColour())) {
                builder.appendHtmlConstant(" style=\"color: ").appendEscaped(item.getIconColour())
                        .appendHtmlConstant("\"");
            }
            builder.appendHtmlConstant(">");
            if (item.getIcon() != null) {
                appendIcon(builder, "wbm-icon", item.getIcon());
            }
            builder.appendHtmlConstant("</span>");
        }
        builder.appendHtmlConstant("<span class=\"wbm-menu__label\">")
                .appendEscaped(item.getLabel())
                .appendHtmlConstant("</span>");

        if (item.getSecondaryAction() != null) {
            builder.appendHtmlConstant("<span class=\"wbm-menu__secondary\" role=\"button\" "
                                       + MenuItem.ATTR_ID + "=\"")
                    .appendEscaped(item.getId() + SECONDARY_SUFFIX)
                    .appendHtmlConstant("\">")
                    .appendEscaped(item.getSecondaryAction())
                    .appendHtmlConstant("</span>");
        }
        if (item.getRightText() != null) {
            builder.appendHtmlConstant("<span class=\"wbm-menu__right\">")
                    .appendEscaped(item.getRightText())
                    .appendHtmlConstant("</span>");
        }
        if (item.getShortcut() != null) {
            appendShortcut(builder, item.getShortcut());
        }
        if (item.getExternalUrl() != null) {
            builder.appendHtmlConstant("<span class=\"wbm-menu__right\">");
            appendIcon(builder, "wbm-icon", "wbm-icon-external");
            builder.appendHtmlConstant("</span></a>");
        } else {
            builder.appendHtmlConstant("</button>");
        }
        builder.appendHtmlConstant("</li>");
    }

    /// Appends a keyboard shortcut as Storybook shows it, i.e. a grey chip of keys.
    ///
    /// @param builder The builder to append to.
    /// @param combo   The shortcut.
    public static void appendShortcut(final SafeHtmlBuilder builder, final KeyCombo combo) {
        builder.appendHtmlConstant("<span class=\"wbm-shortcut\">");
        for (final String part : combo.getDisplayParts()) {
            builder.appendHtmlConstant("<kbd>").appendEscaped(part).appendHtmlConstant("</kbd>");
        }
        builder.appendHtmlConstant("</span>");
    }

    /// Appends an icon that references one of the SVG symbols in workbench-manager.html.
    ///
    /// The class and symbol are put into the markup unescaped so must be constants from the code,
    /// never values from the URL, the preview or the server.
    ///
    /// @param builder   The builder to append to.
    /// @param className The CSS class(es) of the icon, e.g. `wbm-icon`, may be null. Only lower
    ///                  case letters, digits, `-`, `_` and spaces are allowed.
    /// @param symbolId  The id of the symbol, e.g. `wbm-icon-info`. Only lower case letters,
    ///                  digits, `-` and `_` are allowed.
    /// @throws IllegalArgumentException If the class or symbol has other characters.
    public static void appendIcon(final SafeHtmlBuilder builder,
                                  final String className,
                                  final String symbolId) {
        builder.appendHtmlConstant("<svg" + (className != null
                ? " class=\"" + HtmlTokens.requireSafeClassList(className) + "\""
                : "") + " aria-hidden=\"true\"><use href=\"#" + HtmlTokens.requireSafeToken(symbolId)
                                   + "\"></use></svg>");
    }

    /// @param target An element that was clicked in a menu.
    /// @return The id of the clicked menu item, or null if the click wasn't on an item.
    public static String getClickedItemId(final Element target) {
        final Element item = BrowserUtil.closest(target, "[" + MenuItem.ATTR_ID + "]");
        return item != null
                ? item.getAttribute(MenuItem.ATTR_ID)
                : null;
    }
}
