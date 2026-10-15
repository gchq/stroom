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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class TestShortcuts {

    @Test
    void testDefaults() {
        final Shortcuts shortcuts = new Shortcuts();
        assertThat(shortcuts.get(ShortcutAction.FULL_SCREEN)).isEqualTo(KeyCombo.parse("alt+F"));
        assertThat(shortcuts.find(KeyCombo.parse("control+K"))).isEqualTo(ShortcutAction.SEARCH);
        assertThat(shortcuts.find(KeyCombo.parse("alt+Z"))).isNull();
        assertThat(shortcuts.save()).isEmpty();
    }

    @Test
    void testDefaultsAreUnique() {
        assertThat(Arrays.stream(ShortcutAction.values()).map(ShortcutAction::getDefaultCombo))
                .doesNotHaveDuplicates();
        assertThat(Arrays.stream(ShortcutAction.values()).map(ShortcutAction::getId))
                .doesNotHaveDuplicates();
    }

    @Test
    void testSet() {
        final Shortcuts shortcuts = new Shortcuts();
        assertThat(shortcuts.set(ShortcutAction.FULL_SCREEN, KeyCombo.parse("alt+G"))).isNull();
        assertThat(shortcuts.get(ShortcutAction.FULL_SCREEN)).isEqualTo(KeyCombo.parse("alt+G"));
        assertThat(shortcuts.find(KeyCombo.parse("alt+G"))).isEqualTo(ShortcutAction.FULL_SCREEN);
        assertThat(shortcuts.find(KeyCombo.parse("alt+F"))).isNull();

        // Setting the same combination again is fine
        assertThat(shortcuts.set(ShortcutAction.FULL_SCREEN, KeyCombo.parse("alt+G"))).isNull();
    }

    @Test
    void testSet_conflict() {
        final Shortcuts shortcuts = new Shortcuts();
        assertThat(shortcuts.set(ShortcutAction.FULL_SCREEN, KeyCombo.parse("alt+A")))
                .isEqualTo(ShortcutAction.TOGGLE_PANEL.getLabel());
        // Unchanged
        assertThat(shortcuts.get(ShortcutAction.FULL_SCREEN)).isEqualTo(KeyCombo.parse("alt+F"));
    }

    @Test
    void testSaveAndLoad() {
        final Shortcuts shortcuts = new Shortcuts();
        shortcuts.set(ShortcutAction.FULL_SCREEN, KeyCombo.parse("alt+G"));
        shortcuts.set(ShortcutAction.REMOUNT, KeyCombo.parse("alt+="));
        final String saved = shortcuts.save();

        final Shortcuts loaded = new Shortcuts();
        loaded.load(saved);
        assertThat(loaded.get(ShortcutAction.FULL_SCREEN)).isEqualTo(KeyCombo.parse("alt+G"));
        assertThat(loaded.get(ShortcutAction.TOOLBAR)).isEqualTo(KeyCombo.parse("alt+T"));
        assertThat(loaded.save()).isEqualTo(saved);
    }

    @Test
    void testRestoreDefaults() {
        final Shortcuts shortcuts = new Shortcuts();
        shortcuts.set(ShortcutAction.FULL_SCREEN, KeyCombo.parse("alt+G"));
        shortcuts.restoreDefaults();
        assertThat(shortcuts.get(ShortcutAction.FULL_SCREEN)).isEqualTo(KeyCombo.parse("alt+F"));
        assertThat(shortcuts.save()).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "unknown=alt+G",
            "unknown=",
            "=alt+G",
            "garbage",
            // Conflicts with toggle addons so is ignored
            "fullScreen=alt+A",
    })
    void testLoad_ignoresInvalid(final String saved) {
        final Shortcuts shortcuts = new Shortcuts();
        shortcuts.load(saved);
        assertThat(shortcuts.save()).isEmpty();
    }

    @Test
    void testLoad_swappedShortcuts() {
        // Regression test: the saved entries used to be applied one at a time against the
        // defaults, so the first half of a swap conflicted and was lost
        final Shortcuts shortcuts = new Shortcuts();
        assertThat(shortcuts.set(ShortcutAction.FULL_SCREEN, KeyCombo.parse("alt+Z"))).isNull();
        assertThat(shortcuts.set(ShortcutAction.TOGGLE_PANEL, KeyCombo.parse("alt+F"))).isNull();
        assertThat(shortcuts.set(ShortcutAction.FULL_SCREEN, KeyCombo.parse("alt+A"))).isNull();
        final String saved = shortcuts.save();

        final Shortcuts loaded = new Shortcuts();
        loaded.load(saved);
        assertThat(loaded.get(ShortcutAction.FULL_SCREEN)).isEqualTo(KeyCombo.parse("alt+A"));
        assertThat(loaded.get(ShortcutAction.TOGGLE_PANEL)).isEqualTo(KeyCombo.parse("alt+F"));
        assertThat(loaded.save()).isEqualTo(saved);
    }

    @Test
    void testLoad_swapInEitherOrder() {
        final Shortcuts shortcuts = new Shortcuts();
        shortcuts.load("togglePanel=alt+F\nfullScreen=alt+A");
        assertThat(shortcuts.get(ShortcutAction.FULL_SCREEN)).isEqualTo(KeyCombo.parse("alt+A"));
        assertThat(shortcuts.get(ShortcutAction.TOGGLE_PANEL)).isEqualTo(KeyCombo.parse("alt+F"));
    }

    @Test
    void testLoad_duplicateSavedEntriesRevert() {
        // Two changed shortcuts with the same keys both go back to their defaults
        final Shortcuts shortcuts = new Shortcuts();
        shortcuts.load("fullScreen=alt+Z\ntogglePanel=alt+Z\ntoolbar=alt+Y");
        assertThat(shortcuts.get(ShortcutAction.FULL_SCREEN)).isEqualTo(KeyCombo.parse("alt+F"));
        assertThat(shortcuts.get(ShortcutAction.TOGGLE_PANEL)).isEqualTo(KeyCombo.parse("alt+A"));
        // Unaffected entries are kept
        assertThat(shortcuts.get(ShortcutAction.TOOLBAR)).isEqualTo(KeyCombo.parse("alt+Y"));
    }

    @Test
    void testLoad_chainedConflictsRevert() {
        // Reverting full screen to alt+F conflicts with the toolbar's saved alt+F, which reverts too
        final Shortcuts shortcuts = new Shortcuts();
        shortcuts.load("fullScreen=alt+A\ntoolbar=alt+F");
        assertThat(shortcuts.get(ShortcutAction.FULL_SCREEN)).isEqualTo(KeyCombo.parse("alt+F"));
        assertThat(shortcuts.get(ShortcutAction.TOGGLE_PANEL)).isEqualTo(KeyCombo.parse("alt+A"));
        assertThat(shortcuts.get(ShortcutAction.TOOLBAR)).isEqualTo(KeyCombo.parse("alt+T"));
        assertThat(shortcuts.save()).isEmpty();
    }

    @Test
    void testClear() {
        final Shortcuts shortcuts = new Shortcuts();
        shortcuts.clear(ShortcutAction.FULL_SCREEN);
        assertThat(shortcuts.get(ShortcutAction.FULL_SCREEN)).isNull();
        assertThat(shortcuts.find(KeyCombo.parse("alt+F"))).isNull();
        assertThat(shortcuts.find(null)).isNull();
        assertThat(shortcuts.save()).isEqualTo("fullScreen=");

        // The cleared keys can be used by another shortcut
        assertThat(shortcuts.set(ShortcutAction.TOOLBAR, KeyCombo.parse("alt+F"))).isNull();

        final Shortcuts loaded = new Shortcuts();
        loaded.load(shortcuts.save());
        assertThat(loaded.get(ShortcutAction.FULL_SCREEN)).isNull();
        assertThat(loaded.get(ShortcutAction.TOOLBAR)).isEqualTo(KeyCombo.parse("alt+F"));

        loaded.restoreDefaults();
        assertThat(loaded.get(ShortcutAction.FULL_SCREEN)).isEqualTo(KeyCombo.parse("alt+F"));
    }

    @Test
    void testReserve() {
        final Shortcuts shortcuts = new Shortcuts();
        shortcuts.reserve(KeyCombo.parse("alt+="), "Zoom in");
        assertThat(shortcuts.isReserved(KeyCombo.parse("alt+="))).isTrue();
        assertThat(shortcuts.isReserved(KeyCombo.parse("alt+-"))).isFalse();

        // A reserved combination can't be used and nothing is changed
        assertThat(shortcuts.set(ShortcutAction.REMOUNT, KeyCombo.parse("alt+="))).isEqualTo("Zoom in");
        assertThat(shortcuts.get(ShortcutAction.REMOUNT)).isEqualTo(KeyCombo.parse("alt+R"));
        assertThat(shortcuts.find(KeyCombo.parse("alt+="))).isNull();
    }

    @Test
    void testReserve_revertsExistingShortcut() {
        // A shortcut saved before the keys were reserved goes back to its default
        final Shortcuts shortcuts = new Shortcuts();
        shortcuts.load("remount=alt+0");
        assertThat(shortcuts.get(ShortcutAction.REMOUNT)).isEqualTo(KeyCombo.parse("alt+0"));

        shortcuts.reserve(KeyCombo.parse("alt+0"), "Reset zoom");
        assertThat(shortcuts.get(ShortcutAction.REMOUNT)).isEqualTo(KeyCombo.parse("alt+R"));

        // And is ignored when loaded again
        shortcuts.load("remount=alt+0");
        assertThat(shortcuts.get(ShortcutAction.REMOUNT)).isEqualTo(KeyCombo.parse("alt+R"));
    }

    @Test
    void testReserve_defaultIsCleared() {
        // If a default is reserved, the action is left without a shortcut rather than sharing it
        final Shortcuts shortcuts = new Shortcuts();
        shortcuts.reserve(KeyCombo.parse("alt+F"), "Something fixed");
        assertThat(shortcuts.get(ShortcutAction.FULL_SCREEN)).isNull();
    }
}
