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

import stroom.preferences.client.component.KeyboardShortcutsController.ShortcutRow;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestKeyboardShortcutsController {

    @BeforeEach
    void setUp() {
        KeyBinding.applyUserBindings(null, true);
    }

    @AfterEach
    void tearDown() {
        // The bindings are static, so put the defaults back for other tests
        KeyBinding.applyUserBindings(null, true);
    }

    @Test
    void listsTheActionsWithShortcutsInTheirGroupsThenTheBuiltInOnes() {
        final List<ShortcutRow> rows = KeyboardShortcutsController.getRows(null);

        assertThat(rows).contains(
                new ShortcutRow("Save", "Ctrl+S", "General"),
                new ShortcutRow("Refresh list", "Alt+R", "General"),
                new ShortcutRow("Go to next tab", "Alt+PageDown", "General"),
                new ShortcutRow("Show keyboard shortcuts", "Shift+/", "General"),
                new ShortcutRow("Move up", "W, K, Up", "Lists"),
                new ShortcutRow("Rename item", "F2", "Explorer"),
                new ShortcutRow("Show item info", "Alt+Enter", "Explorer"),
                new ShortcutRow("Close saved tabs", "Alt+Shift+S", "General"),
                new ShortcutRow("Go to Properties", "G P", "Go to"),
                // Enter once, not also its other key codes (MacEnter, a line feed)
                new ShortcutRow("OK, or run a query", "Ctrl+Enter", KeyboardShortcutsController.BUILT_IN),
                new ShortcutRow("Close, or stop a query", "Esc", KeyboardShortcutsController.BUILT_IN),
                new ShortcutRow("Open", "Enter", KeyboardShortcutsController.BUILT_IN));
        // An action with no shortcut isn't listed
        assertThat(rows).noneMatch(row -> row.action().equals(Action.GOTO_USERS.getDisplayName()));
        // The groups in order: General, Lists, Explorer, Go to, Create, then built in
        assertThat(rows.stream().map(ShortcutRow::group).distinct())
                .containsExactly("General", "Lists", "Explorer", "Go to", "Create",
                        KeyboardShortcutsController.BUILT_IN);
    }

    @Test
    void showsTheShortcutsAsTheUserHasThem() {
        final Map<String, List<String>> userBindings = new HashMap<>();
        userBindings.put(Action.REFRESH.name(), List.of("Alt+Shift+R"));
        KeyBinding.applyUserBindings(userBindings, false);

        final List<ShortcutRow> rows = KeyboardShortcutsController.getRows(null);

        assertThat(rows).contains(new ShortcutRow("Refresh list", "Alt+Shift+R", "General"));
        // Character key shortcuts are turned off: '?' and the 'Go to' sequences aren't listed
        assertThat(rows).noneMatch(row -> row.action().equals("Show keyboard shortcuts"));
        assertThat(rows).noneMatch(row -> row.group().equals("Go to"));
        assertThat(rows).contains(new ShortcutRow("Move up", "Up", "Lists"));
    }

    @Test
    void filtersByActionShortcutOrGroupIgnoringCase() {
        assertThat(KeyboardShortcutsController.getRows("  REFRESH "))
                .containsExactly(new ShortcutRow("Refresh list", "Alt+R", "General"));
        assertThat(KeyboardShortcutsController.getRows("alt+page"))
                .extracting(ShortcutRow::action)
                .containsExactly("Go to next tab", "Go to previous tab");
        assertThat(KeyboardShortcutsController.getRows("create"))
                .allMatch(row -> row.group().equals("Create"))
                .isNotEmpty();
        assertThat(KeyboardShortcutsController.getRows("no such thing")).isEmpty();
        assertThat(KeyboardShortcutsController.getRows(" ")).hasSameSizeAs(KeyboardShortcutsController.getRows(null));
    }
}
