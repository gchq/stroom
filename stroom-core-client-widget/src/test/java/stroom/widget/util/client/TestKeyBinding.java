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

package stroom.widget.util.client;

import stroom.widget.util.client.KeyBinding.Action;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestKeyBinding {

    @AfterEach
    void tearDown() {
        // The bindings are static, so put the defaults back for other tests
        KeyBinding.applyUserBindings(null, true);
    }

    @Test
    void hasTheDefaultShortcuts() {
        assertThat(KeyBinding.getShortcut(Action.ITEM_SAVE)).isEqualTo("Ctrl+S");
        assertThat(KeyBinding.getShortcut(Action.GOTO_USER_PREFERENCES)).isEqualTo("G U");
        assertThat(KeyBinding.getShortcut(Action.MOVE_UP)).isEqualTo("W");
        // Only a fixed binding
        assertThat(KeyBinding.getShortcut(Action.SELECT)).isEqualTo("Space");
        // None at all
        assertThat(KeyBinding.getShortcut(Action.GOTO_USERS)).isNull();
    }

    @Test
    void hasNoDefaultShortcutsThatClash() {
        final Map<Action, List<KeyShortcut>> defaults = KeyBinding.getDefaultBindings();

        defaults.forEach((action, shortcuts) -> shortcuts.forEach(shortcut -> {
            assertThat(KeyBinding.findClashes(defaults, action, shortcut))
                    .as("Clashes with %s for %s", shortcut, action)
                    .isEmpty();
            assertThat(KeyBinding.findFixedClash(shortcut))
                    .as("Fixed clash with %s for %s", shortcut, action)
                    .isNull();
        }));
    }

    @Test
    void onlyHasConfigurableActionsWithACategory() {
        assertThat(KeyBinding.getConfigurableActions())
                .contains(Action.MOVE_UP, Action.ITEM_SAVE, Action.GOTO_USERS, Action.CREATE_XSLT)
                .doesNotContain(Action.SELECT, Action.OK, Action.CLOSE, Action.EXECUTE)
                .allMatch(action -> action.getCategory() != null);
    }

    @Test
    void changesAShortcut() {
        KeyBinding.applyUserBindings(userBindings(Action.ITEM_SAVE, "Ctrl+Alt+S"), true);

        assertThat(KeyBinding.getShortcut(Action.ITEM_SAVE)).isEqualTo("Ctrl+Alt+S");
        // Others keep their defaults
        assertThat(KeyBinding.getShortcut(Action.ITEM_SAVE_ALL)).isEqualTo("Ctrl+Shift+S");
    }

    @Test
    void addsAShortcutToAnActionWithNone() {
        KeyBinding.applyUserBindings(userBindings(Action.GOTO_USERS, "Alt+U"), true);

        assertThat(KeyBinding.getShortcut(Action.GOTO_USERS)).isEqualTo("Alt+U");
    }

    @Test
    void removesAllShortcutsOfAnAction() {
        KeyBinding.applyUserBindings(userBindings(Action.MOVE_UP), true);

        // The fixed binding is still there
        assertThat(KeyBinding.getShortcut(Action.MOVE_UP)).isEqualTo("Up");
    }

    @Test
    void turnsOffCharacterKeyShortcuts() {
        final Map<Action, List<KeyShortcut>> resolved = KeyBinding.resolve(null, false);

        assertThat(resolved.get(Action.MOVE_UP)).isEmpty();
        assertThat(resolved.get(Action.GOTO_USER_PREFERENCES)).isEmpty();
        assertThat(resolved.get(Action.FOCUS_FILTER)).isEmpty();
        assertThat(texts(resolved.get(Action.ITEM_SAVE))).containsExactly("Ctrl+S");
        assertThat(texts(resolved.get(Action.HELP))).containsExactly("F1");
        // Double Shift isn't a character key shortcut
        assertThat(texts(resolved.get(Action.FIND))).containsExactly("Alt+Shift+F", "Shift Shift");

        KeyBinding.applyUserBindings(null, false);
        assertThat(KeyBinding.getShortcut(Action.MOVE_UP)).isEqualTo("Up");
        assertThat(KeyBinding.getShortcut(Action.GOTO_USER_PREFERENCES)).isNull();
    }

    @Test
    void givesAChangedShortcutPriorityOverADefault() {
        // Ctrl+E is the default for Recent Items, but the user has given it to Locate
        final Map<Action, List<KeyShortcut>> resolved = KeyBinding.resolve(
                userBindings(Action.LOCATE, "Ctrl+E"), true);

        assertThat(texts(resolved.get(Action.LOCATE))).containsExactly("Ctrl+E");
        assertThat(resolved.get(Action.RECENT_ITEMS)).isEmpty();
    }

    @Test
    void ignoresAShortcutThatClashesWithAFixedBinding() {
        final Map<Action, List<KeyShortcut>> resolved = KeyBinding.resolve(
                userBindings(Action.ITEM_SAVE, "Space", "Ctrl+A", "Up X", "Ctrl+Alt+S"), true);

        assertThat(texts(resolved.get(Action.ITEM_SAVE))).containsExactly("Ctrl+Alt+S");
    }

    @Test
    void givesAChangedSingleKeyPriorityOverTheSequencesItStarts() {
        // G starts all the 'Go to' sequences, which can't be used while G is a shortcut
        final Map<Action, List<KeyShortcut>> resolved = KeyBinding.resolve(
                userBindings(Action.ITEM_SAVE, "G"), true);

        assertThat(texts(resolved.get(Action.ITEM_SAVE))).containsExactly("G");
        assertThat(resolved.get(Action.GOTO_USER_PREFERENCES)).isEmpty();
    }

    @Test
    void ignoresUnknownActionsAndInvalidShortcuts() {
        final Map<String, List<String>> userBindings = new HashMap<>();
        userBindings.put("NOT_AN_ACTION", Collections.singletonList("Ctrl+S"));
        userBindings.put(Action.SELECT.name(), Collections.singletonList("Ctrl+Q"));
        userBindings.put(Action.ITEM_SAVE.name(), List.of("Nonsense", "Ctrl+Alt+S", "Ctrl+Alt+S"));

        final Map<Action, List<KeyShortcut>> bindings = KeyBinding.fromUserBindings(userBindings);

        assertThat(bindings).doesNotContainKey(Action.SELECT);
        assertThat(texts(bindings.get(Action.ITEM_SAVE))).containsExactly("Ctrl+Alt+S");
    }

    @Test
    void savesOnlyTheActionsThatDifferFromTheDefaults() {
        final Map<Action, List<KeyShortcut>> bindings = KeyBinding.getDefaultBindings();
        assertThat(KeyBinding.toUserBindings(bindings)).isNull();

        bindings.get(Action.ITEM_SAVE).add(KeyShortcut.parse("Ctrl+Alt+S"));
        bindings.get(Action.MOVE_UP).clear();

        final Map<String, List<String>> userBindings = KeyBinding.toUserBindings(bindings);
        assertThat(userBindings)
                .containsOnlyKeys(Action.ITEM_SAVE.name(), Action.MOVE_UP.name());
        assertThat(userBindings.get(Action.ITEM_SAVE.name())).containsExactly("Ctrl+S", "Ctrl+Alt+S");
        assertThat(userBindings.get(Action.MOVE_UP.name())).isEmpty();

        // And back again
        assertThat(KeyBinding.fromUserBindings(userBindings)).isEqualTo(bindings);
    }

    @Test
    void findsClashesWithOtherActions() {
        final Map<Action, List<KeyShortcut>> bindings = KeyBinding.getDefaultBindings();

        assertThat(KeyBinding.findClashes(bindings, Action.LOCATE, KeyShortcut.parse("Ctrl+E")))
                .containsOnlyKeys(Action.RECENT_ITEMS);
        // Not with its own shortcut
        assertThat(KeyBinding.findClashes(bindings, Action.RECENT_ITEMS, KeyShortcut.parse("Ctrl+E")))
                .isEmpty();
        // C starts all the 'Create' sequences
        assertThat(KeyBinding.findClashes(bindings, Action.ITEM_SAVE, KeyShortcut.parse("C")).keySet())
                .allMatch(action -> action.getCategory() == KeyBinding.Category.CREATE)
                .hasSize(14);
    }

    @Test
    void findsClashesWithFixedBindings() {
        assertThat(KeyBinding.findFixedClash(KeyShortcut.parse("Space"))).isEqualTo(Action.SELECT);
        assertThat(KeyBinding.findFixedClash(KeyShortcut.parse("Ctrl+Enter"))).isEqualTo(Action.OK);
        assertThat(KeyBinding.findFixedClash(KeyShortcut.parse("Down X"))).isEqualTo(Action.MOVE_DOWN);
        assertThat(KeyBinding.findFixedClash(KeyShortcut.parse("Ctrl+Down"))).isNull();
    }

    private static Map<String, List<String>> userBindings(final Action action, final String... shortcuts) {
        final Map<String, List<String>> userBindings = new HashMap<>();
        userBindings.put(action.name(), new ArrayList<>(List.of(shortcuts)));
        return userBindings;
    }

    private static List<String> texts(final List<KeyShortcut> shortcuts) {
        return shortcuts.stream()
                .map(KeyShortcut::toString)
                .collect(Collectors.toList());
    }
}
