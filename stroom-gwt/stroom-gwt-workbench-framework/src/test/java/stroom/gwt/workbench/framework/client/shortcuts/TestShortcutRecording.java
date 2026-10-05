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

import stroom.gwt.workbench.framework.client.shortcuts.ShortcutRecording.Outcome;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestShortcutRecording {

    @ParameterizedTest
    @CsvSource(value = {
            // Tab must never be trapped, with or without modifiers
            "Tab,PASS_THROUGH",
            "shift+Tab,PASS_THROUGH",
            "alt+Tab,PASS_THROUGH",
            "Escape,CANCEL",
            "Backspace,CLEAR",
            "Delete,CLEAR",
            // Plain navigation keys can't be shortcuts
            "Enter,IGNORE",
            "Space,IGNORE",
            "ArrowUp,IGNORE",
            "ArrowLeft,IGNORE",
            "Home,IGNORE",
            "PageDown,IGNORE",
            "shift+ArrowUp,IGNORE",
            // With a modifier they can
            "alt+ArrowUp,RECORD",
            "control+Enter,RECORD",
            "alt+Escape,RECORD",
            "control+Backspace,RECORD",
            // Plain keys like Storybook's '1' and 'F6' defaults
            "1,RECORD",
            "F6,RECORD",
            "shift+F6,RECORD",
            "alt+F,RECORD",
    })
    void testClassify(final String combo, final Outcome expected) {
        assertThat(ShortcutRecording.classify(KeyCombo.parse(combo))).isEqualTo(expected);
    }
}
