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

package stroom.preferences.client.component;


/// What can be done to the shortcuts in the Keyboard Shortcuts tab ([KeyBindingPreferences]), a
/// toolbar button each.
public enum KeyBindingAction {
    /// Adds a shortcut to the selected action.
    ADD,
    /// Changes the selected shortcut.
    CHANGE,
    /// Removes the selected shortcut.
    REMOVE,
    /// Resets the selected action to its default shortcuts.
    RESET,
    /// Resets every action to its default shortcuts.
    RESET_ALL
}
