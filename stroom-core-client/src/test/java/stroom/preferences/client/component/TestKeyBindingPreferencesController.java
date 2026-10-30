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

import stroom.preferences.client.model.KeyBindingRow;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;
import stroom.widget.util.client.KeyShortcut;

import com.google.gwt.junit.GWTMockUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestKeyBindingPreferencesController {

    @BeforeEach
    void setUp() {
        // Loading a controller or component loads GWT classes that GWT.create in a browser only
        GWTMockUtilities.disarm();
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void testRowPerShortcut() {
        final Map<Action, List<KeyShortcut>> bindings = KeyBinding.getDefaultBindings();
        final List<KeyBindingRow> rows = KeyBindingPreferencesController.rows(bindings, null);

        // A row for each of an action's shortcuts
        for (final KeyShortcut shortcut : bindings.get(Action.REFRESH)) {
            assertThat(rows).contains(new KeyBindingRow(Action.REFRESH, shortcut, false));
        }
        // An action with no shortcut has one row with none
        bindings.put(Action.GOTO_USERS, new ArrayList<>());
        assertThat(KeyBindingPreferencesController.rows(bindings, null))
                .contains(new KeyBindingRow(Action.GOTO_USERS, null, false));
    }

    @Test
    void testFilter() {
        final Map<Action, List<KeyShortcut>> bindings = KeyBinding.getDefaultBindings();

        // By the action's name, ignoring case and spaces around it
        final List<KeyBindingRow> refresh = KeyBindingPreferencesController.rows(bindings, "  REFRESH LIST ");
        assertThat(refresh).isNotEmpty();
        assertThat(refresh).allMatch(row -> row.getAction() == Action.REFRESH);

        // By its category's name
        assertThat(KeyBindingPreferencesController.rows(bindings, "Lists"))
                .anyMatch(row -> row.getAction() == Action.MOVE_UP);

        // A blank filter matches every row
        assertThat(KeyBindingPreferencesController.rows(bindings, " "))
                .hasSameSizeAs(KeyBindingPreferencesController.rows(bindings, null));

        // Nothing matches
        assertThat(KeyBindingPreferencesController.rows(bindings, "no such action")).isEmpty();
    }

    @Test
    void testStatus() {
        final KeyShortcut defaultShortcut = KeyBinding.getDefaultShortcuts(Action.REFRESH).getFirst();
        assertThat(KeyBindingPreferences.status(new KeyBindingRow(Action.REFRESH, defaultShortcut, false), true))
                .isEqualTo("Default");
        assertThat(KeyBindingPreferences.status(new KeyBindingRow(Action.REFRESH, defaultShortcut, true), true))
                .isEqualTo("Built in");
        assertThat(KeyBindingPreferences.status(new KeyBindingRow(Action.REFRESH, null, false), true))
                .isEmpty();
        final KeyShortcut changed = KeyShortcut.parse("Alt+Shift+F9");
        assertThat(KeyBindingPreferences.status(new KeyBindingRow(Action.REFRESH, changed, false), true))
                .isEqualTo("Changed");
    }

    @Test
    void testStatusTurnedOff() {
        // A character key shortcut, while they are turned off
        final KeyShortcut characterKey = KeyBinding.getDefaultShortcuts(Action.MOVE_UP).stream()
                .filter(KeyShortcut::isCharacterKeyShortcut)
                .findFirst()
                .orElseThrow();
        final KeyBindingRow row = new KeyBindingRow(Action.MOVE_UP, characterKey, false);
        assertThat(KeyBindingPreferences.status(row, false)).isEqualTo("Default, turned off");
        assertThat(KeyBindingPreferences.status(row, true)).isEqualTo("Default");
    }
}
