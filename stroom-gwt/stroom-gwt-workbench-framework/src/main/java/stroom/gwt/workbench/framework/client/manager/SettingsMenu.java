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
import stroom.gwt.workbench.framework.client.shortcuts.ShortcutAction;
import stroom.gwt.workbench.framework.client.shortcuts.Shortcuts;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;

import java.util.List;

/// The menu shown by the settings (cog) button at the top of the sidebar, with the same items as
/// React Storybook's, apart from the onboarding guide.
public class SettingsMenu {

    private static final String BUTTON_ID = "wbm-settings-button";
    private static final String ABOUT = "about";
    private static final String SHORTCUTS = "shortcuts";

    private final ShortcutHandler shortcutHandler;
    private final LayoutController layout;
    private final Element button;
    private Popover popover;

    /// @param shortcutHandler The keyboard shortcuts, to show in the menu and run when clicked.
    /// @param layout          The layout, to show which parts are visible.
    public SettingsMenu(final ShortcutHandler shortcutHandler, final LayoutController layout) {
        this.shortcutHandler = shortcutHandler;
        this.layout = layout;
        button = Document.get().getElementById(BUTTON_ID);
        BrowserUtil.addListener(button, "click", event -> toggle());
    }

    private void toggle() {
        popover = Popover.toggle(button, render(), Popover.Align.START, this::onClick);
    }

    /// Updates the menu if it is open, e.g. after the layout changes.
    public void refresh() {
        if (popover != null && popover.getElement().getParentElement() != null) {
            popover.setContent(render());
        }
    }

    private SafeHtml render() {
        final Shortcuts shortcuts = shortcutHandler.getShortcuts();
        return MenuHtml.render(MenuItem.groups(
                List.of(
                        MenuItem.of(ABOUT, "About the workbench").icon("wbm-icon-info"),
                        MenuItem.of(SHORTCUTS, "Keyboard shortcuts")
                                .icon("wbm-icon-command")
                                .shortcut(shortcuts.get(ShortcutAction.SHORTCUTS_PAGE))),
                List.of(
                        toggleItem(ShortcutAction.TOGGLE_NAV, "Show sidebar", layout.isSidebarVisible()),
                        toggleItem(ShortcutAction.TOOLBAR, "Show toolbar", layout.isToolbarVisible()),
                        toggleItem(ShortcutAction.TOGGLE_PANEL, "Show addons panel", layout.isPanelVisible()),
                        actionItem(ShortcutAction.PREV_COMPONENT, "Previous component"),
                        actionItem(ShortcutAction.NEXT_COMPONENT, "Next component"),
                        actionItem(ShortcutAction.PREV_STORY, "Previous story"),
                        actionItem(ShortcutAction.NEXT_STORY, "Next story"),
                        actionItem(ShortcutAction.COLLAPSE_ALL, "Collapse all"))));
    }

    private MenuItem toggleItem(final ShortcutAction action, final String label, final boolean on) {
        return MenuItem.of(action.getId(), label)
                .toggle(on)
                .shortcut(shortcutHandler.getShortcuts().get(action));
    }

    private MenuItem actionItem(final ShortcutAction action, final String label) {
        return MenuItem.of(action.getId(), label)
                .shortcut(shortcutHandler.getShortcuts().get(action));
    }

    private void onClick(final Element target) {
        final String itemId = MenuHtml.getClickedItemId(target);
        if (itemId == null || "docs".equals(itemId)) {
            return;
        }
        final ShortcutAction action;
        if (ABOUT.equals(itemId)) {
            action = ShortcutAction.ABOUT_PAGE;
        } else if (SHORTCUTS.equals(itemId)) {
            action = ShortcutAction.SHORTCUTS_PAGE;
        } else {
            action = ShortcutAction.fromId(itemId);
        }
        final boolean isToggle = action == ShortcutAction.TOGGLE_NAV
                                 || action == ShortcutAction.TOOLBAR
                                 || action == ShortcutAction.TOGGLE_PANEL;
        if (!isToggle) {
            Popover.hideCurrent();
        }
        if (action != null) {
            shortcutHandler.run(action);
        }
        refresh();
    }
}
