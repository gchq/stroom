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

package stroom.widget.menu.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// A menu item's shortcut text, and the form screen readers are given it in.
class TestMenuItemShortcut {

    @Test
    void fixedShortcut_isShown() {
        final IconMenuItem item = new IconMenuItem.Builder()
                .text("Copy")
                .shortcut("Ctrl+C")
                .build();

        assertThat(item.getShortcut()).isEqualTo("Ctrl+C");
    }

    @Test
    void noShortcutOrAction_showsNone() {
        final IconMenuItem item = new IconMenuItem.Builder()
                .text("Open")
                .build();

        assertThat(item.getShortcut()).isNull();
    }

    @Test
    void ariaKeyShortcuts_namesModifiersAsTheAttributeDoes() {
        assertThat(MenuItem.toAriaKeyShortcuts("Ctrl+C")).isEqualTo("Control+C");
        assertThat(MenuItem.toAriaKeyShortcuts("Cmd+Shift+Z")).isEqualTo("Meta+Shift+Z");
        assertThat(MenuItem.toAriaKeyShortcuts("Del")).isEqualTo("Delete");
        assertThat(MenuItem.toAriaKeyShortcuts("F2")).isEqualTo("F2");
    }

    @Test
    void ariaKeyShortcuts_leavesOutSequencesAndNothing() {
        // A space separates alternatives in the attribute, so a sequence can't be given
        assertThat(MenuItem.toAriaKeyShortcuts("G H")).isNull();
        assertThat(MenuItem.toAriaKeyShortcuts(null)).isNull();
        assertThat(MenuItem.toAriaKeyShortcuts("")).isNull();
    }
}
