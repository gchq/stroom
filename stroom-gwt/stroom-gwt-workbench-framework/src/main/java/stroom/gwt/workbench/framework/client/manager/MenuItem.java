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

import stroom.gwt.workbench.framework.client.shortcuts.KeyCombo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// An item in a workbench menu, see [MenuHtml#render(List)].
public final class MenuItem {

    /// The attribute holding an item's id on its element.
    public static final String ATTR_ID = "data-menu-item";

    private final String id;
    private final String label;
    private String icon;
    private String iconColour;
    private boolean active;
    private boolean showCheckWhenActive;
    private KeyCombo shortcut;
    private String rightText;
    private String externalUrl;
    private String secondaryAction;

    private MenuItem(final String id, final String label) {
        this.id = id;
        this.label = label;
    }

    /// @param id    The id passed to the click handler when the item is clicked.
    /// @param label The text of the item.
    /// @return A new item.
    public static MenuItem of(final String id, final String label) {
        return new MenuItem(id, label);
    }

    /// @param icon The id of the icon's SVG symbol, e.g. `wbm-icon-info`.
    /// @return This item.
    public MenuItem icon(final String icon) {
        this.icon = icon;
        return this;
    }

    /// @param iconColour The CSS colour of the icon.
    /// @return This item.
    public MenuItem iconColour(final String iconColour) {
        this.iconColour = iconColour;
        return this;
    }

    /// Shows the item as selected (bold and highlighted), e.g. the current zoom level.
    ///
    /// @param active True if the item is selected.
    /// @return This item.
    public MenuItem active(final boolean active) {
        this.active = active;
        return this;
    }

    /// Shows the item as a toggle with a tick when it is on, e.g. 'Show sidebar'.
    ///
    /// @param on True if the toggle is on.
    /// @return This item.
    public MenuItem toggle(final boolean on) {
        this.active = on;
        this.showCheckWhenActive = true;
        return this;
    }

    /// @param shortcut The keyboard shortcut to show.
    /// @return This item.
    public MenuItem shortcut(final KeyCombo shortcut) {
        this.shortcut = shortcut;
        return this;
    }

    /// @param rightText Text shown at the right of the item, e.g. a count.
    /// @return This item.
    public MenuItem rightText(final String rightText) {
        this.rightText = rightText;
        return this;
    }

    /// Makes the item a link that opens in a new tab, with an external link icon.
    ///
    /// @param externalUrl The URL.
    /// @return This item.
    public MenuItem externalUrl(final String externalUrl) {
        this.externalUrl = externalUrl;
        return this;
    }

    /// Adds a secondary button that appears when hovering over the item, e.g. 'Exclude'.
    ///
    /// @param secondaryAction The label of the button. Clicks are reported with the item's id
    ///                        followed by `:secondary`.
    /// @return This item.
    public MenuItem secondaryAction(final String secondaryAction) {
        this.secondaryAction = secondaryAction;
        return this;
    }

    /// @return The id passed to the click handler when the item is clicked.
    String getId() {
        return id;
    }

    /// @return The text of the item.
    String getLabel() {
        return label;
    }

    /// @return The id of the icon's SVG symbol, a tick for a toggle that is on without its own
    /// icon, or null for none.
    String getIcon() {
        if (icon == null && showCheckWhenActive && active) {
            return "wbm-icon-check";
        }
        return icon;
    }

    /// @return The CSS colour of the icon, or null for the default.
    String getIconColour() {
        return iconColour;
    }

    /// @return True if the item is selected, or a toggle that is on.
    boolean isActive() {
        return active;
    }

    /// @return True if the item is a toggle, see [#toggle(boolean)].
    boolean isToggle() {
        return showCheckWhenActive;
    }

    /// @return The keyboard shortcut to show, or null for none.
    KeyCombo getShortcut() {
        return shortcut;
    }

    /// @return The text shown at the right of the item, or null for none.
    String getRightText() {
        return rightText;
    }

    /// @return The URL the item links to, or null if it isn't a link.
    String getExternalUrl() {
        return externalUrl;
    }

    /// @return The label of the secondary button, or null for none.
    String getSecondaryAction() {
        return secondaryAction;
    }

    /// Creates a list of groups for [MenuHtml#render(List)].
    ///
    /// @param groups The groups of items, separated by lines in the menu.
    /// @return The groups.
    @SafeVarargs
    public static List<List<MenuItem>> groups(final List<MenuItem>... groups) {
        final List<List<MenuItem>> list = new ArrayList<>();
        Collections.addAll(list, groups);
        return list;
    }
}
