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

package stroom.gwt.workbench.framework.client.shortcuts;

/// The actions that have keyboard shortcuts, with the same labels, order and default keys as
/// React Storybook's keyboard shortcuts page.
public enum ShortcutAction {
    FULL_SCREEN("fullScreen", "Go full screen", "alt+F"),
    TOGGLE_PANEL("togglePanel", "Toggle addons", "alt+A"),
    PANEL_POSITION("panelPosition", "Toggle addons orientation", "alt+D"),
    TOGGLE_NAV("toggleNav", "Toggle sidebar", "alt+S"),
    TOOLBAR("toolbar", "Toggle toolbar", "alt+T"),
    SEARCH("search", "Focus search", "control+K"),
    FOCUS_NAV("focusNav", "Focus sidebar", "1"),
    FOCUS_IFRAME("focusIframe", "Focus canvas", "2"),
    FOCUS_PANEL("focusPanel", "Focus addons", "3"),
    PREV_COMPONENT("prevComponent", "Previous component", "alt+ArrowUp"),
    NEXT_COMPONENT("nextComponent", "Next component", "alt+ArrowDown"),
    PREV_STORY("prevStory", "Previous story", "alt+ArrowLeft"),
    NEXT_STORY("nextStory", "Next story", "alt+ArrowRight"),
    SHORTCUTS_PAGE("shortcutsPage", "Go to shortcuts page", "control+shift+,"),
    ABOUT_PAGE("aboutPage", "Go to about page", "control+,"),
    COLLAPSE_ALL("collapseAll", "Collapse all items on sidebar", "control+shift+ArrowUp"),
    EXPAND_ALL("expandAll", "Expand all items on sidebar", "control+shift+ArrowDown"),
    REMOUNT("remount", "Reload story", "alt+R"),
    OPEN_IN_EDITOR("openInEditor", "Open story in editor", "alt+shift+E"),
    OPEN_IN_ISOLATION("openInIsolation", "Open story in isolation", "alt+shift+I"),
    COPY_STORY_LINK("copyStoryLink", "Copy story link to clipboard", "alt+shift+L"),
    PREVIOUS_LANDMARK("goToPreviousLandmark", "Go to previous landmark", "shift+F6"),
    NEXT_LANDMARK("goToNextLandmark", "Go to next landmark", "F6");

    private final String id;
    private final String label;
    private final KeyCombo defaultCombo;

    ShortcutAction(final String id, final String label, final String defaultCombo) {
        this.id = id;
        this.label = label;
        this.defaultCombo = KeyCombo.parse(defaultCombo);
    }

    /// @return The id Storybook uses for the shortcut, used when storing custom shortcuts.
    public String getId() {
        return id;
    }

    /// @return The label shown on the keyboard shortcuts page.
    public String getLabel() {
        return label;
    }

    /// @return The default key combination.
    public KeyCombo getDefaultCombo() {
        return defaultCombo;
    }

    /// @param id The id of an action, as returned by [#getId()].
    /// @return The action with the id, or null if there isn't one.
    public static ShortcutAction fromId(final String id) {
        for (final ShortcutAction action : values()) {
            if (action.id.equals(id)) {
                return action;
            }
        }
        return null;
    }
}
