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
import stroom.gwt.workbench.framework.client.shortcuts.KeyEvents;
import stroom.gwt.workbench.framework.client.shortcuts.ShortcutAction;
import stroom.gwt.workbench.framework.client.shortcuts.Shortcuts;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.client.Event;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// Runs the action for each keyboard shortcut the user presses, whether in the manager or (via a
/// message from the preview) in the canvas. The shortcuts can be changed on the keyboard
/// shortcuts page and are remembered in local storage.
public class ShortcutHandler {

    private final Shortcuts shortcuts = new Shortcuts();
    private final Map<ShortcutAction, Runnable> actions = new EnumMap<>(ShortcutAction.class);
    // Shortcuts that can't be changed, e.g. the zoom tool's, which Storybook doesn't list either
    private final Map<KeyCombo, Runnable> fixedActions = new HashMap<>();
    private final List<Runnable> changeHandlers = new ArrayList<>();
    private boolean suspended;

    /// Loads the user's shortcuts from local storage and starts listening for key presses in the
    /// manager.
    public ShortcutHandler() {
        shortcuts.load(BrowserUtil.getLocalStorage(Shortcuts.STORAGE_KEY));
        Event.addNativePreviewHandler(preview -> {
            if (preview.getTypeInt() == Event.ONKEYDOWN && !suspended) {
                final NativeEvent event = preview.getNativeEvent();
                final KeyCombo combo = KeyEvents.toCombo(event);
                if (combo != null && handle(combo, KeyEvents.isTyping(event))) {
                    event.preventDefault();
                }
            }
        });
    }

    /// @param action   A shortcut action.
    /// @param runnable What to do when the shortcut is pressed.
    public void register(final ShortcutAction action, final Runnable runnable) {
        actions.put(action, runnable);
    }

    /// Registers a shortcut that the user can't change. The keys are reserved so that no other
    /// shortcut can be given them, which would silently hide one or the other.
    ///
    /// @param combo    The keys.
    /// @param label    The name of the shortcut, e.g. `Zoom in`, shown if the user tries to use the
    ///                 keys for another shortcut.
    /// @param runnable What to do when the keys are pressed.
    public void registerFixed(final KeyCombo combo, final String label, final Runnable runnable) {
        fixedActions.put(combo, runnable);
        shortcuts.reserve(combo, label);
    }

    /// @param changeHandler Called whenever the user changes the shortcuts, e.g. to update a
    ///                      shortcut shown on the page.
    public void addChangeHandler(final Runnable changeHandler) {
        changeHandlers.add(changeHandler);
    }

    /// Runs the action for a shortcut, e.g. when chosen from a menu.
    ///
    /// @param action The action.
    public void run(final ShortcutAction action) {
        final Runnable runnable = actions.get(action);
        if (runnable != null) {
            runnable.run();
        }
    }

    /// @return The current shortcuts.
    public Shortcuts getShortcuts() {
        return shortcuts;
    }

    /// Remembers the current shortcuts in local storage.
    public void save() {
        final String saved = shortcuts.save();
        BrowserUtil.setLocalStorage(Shortcuts.STORAGE_KEY, saved.isEmpty()
                ? null
                : saved);
        changeHandlers.forEach(Runnable::run);
    }

    /// Stops shortcuts running, e.g. while the user is recording a new shortcut.
    ///
    /// @param suspended True to stop shortcuts running.
    public void setSuspended(final boolean suspended) {
        this.suspended = suspended;
    }

    /// Runs the action for a key combination, e.g. one forwarded from the preview.
    ///
    /// @param combo  The keys pressed.
    /// @param typing True if the user was typing into a field, so plain keys should be ignored.
    /// @return True if an action was run.
    public boolean handle(final KeyCombo combo, final boolean typing) {
        if (suspended || (typing && combo.isPlainKey())) {
            return false;
        }
        // Fixed shortcuts' keys are reserved so no action can share them
        final ShortcutAction action = shortcuts.find(combo);
        final Runnable runnable = action != null
                ? actions.get(action)
                : fixedActions.get(combo);
        if (runnable == null) {
            return false;
        }
        runnable.run();
        return true;
    }
}
