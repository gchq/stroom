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

package stroom.gwt.workbench.framework.client.play;

import stroom.gwt.workbench.framework.client.play.TextEdits.Edit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestTextEdits {

    private static final String INSERT = "insertText";
    private static final String BACKWARD = "deleteContentBackward";
    private static final String FORWARD = "deleteContentForward";

    @Test
    void testInsertAndDelete() {
        assertThat(edit("abc", 3, 3, "d", INSERT, "text", -1)).hasToString("abcd|4");
        assertThat(edit("abc", 1, 1, "x", INSERT, "text", -1)).hasToString("axbc|2");
        assertThat(edit("abc", 0, 3, "z", INSERT, "text", -1)).hasToString("z|1");
        assertThat(edit("abc", 3, 3, "", BACKWARD, "text", -1)).hasToString("ab|2");
        assertThat(edit("abc", 0, 0, "", FORWARD, "text", -1)).hasToString("bc|0");
        assertThat(edit("abc", 1, 3, "", BACKWARD, "text", -1)).hasToString("a|1");
        // Nothing to delete
        assertThat(edit("abc", 0, 0, "", BACKWARD, "text", -1)).isNull();
        assertThat(edit("", 0, 0, "", BACKWARD, "text", -1)).isNull();
        assertThat(edit("abc", 3, 3, "", FORWARD, "text", -1)).isNull();
        // A selection out of range is clamped
        assertThat(edit("abc", 10, 10, "d", INSERT, "text", -1)).hasToString("abcd|4");
    }

    @Test
    void testNumberFieldKeepsTheTypedValue() {
        // Regression: typing '1.5' into a number field gave 15, as '1.' was sanitised to ''
        String value = "";
        for (final char chr : "1.5".toCharArray()) {
            final Edit edit = edit(value, value.length(), value.length(), String.valueOf(chr), INSERT, "number", -1);
            assertThat(edit).isNotNull();
            value = edit.getValue();
        }
        assertThat(value).isEqualTo("1.5");

        assertThat(edit("", 0, 0, "-", INSERT, "number", -1)).hasToString("-|1");
        assertThat(edit("-", 1, 1, "2", INSERT, "number", -1)).hasToString("-2|2");
        // Letters, a second '.' and a third '-' are rejected, as a browser does
        assertThat(edit("1", 1, 1, "a", INSERT, "number", -1)).isNull();
        assertThat(edit("1.5", 3, 3, ".", INSERT, "number", -1)).isNull();
        assertThat(edit("--", 2, 2, "-", INSERT, "number", -1)).isNull();
        // Any text goes in other fields
        assertThat(edit("1", 1, 1, "a", INSERT, "text", -1)).hasToString("1a|2");
    }

    @Test
    void testIsValidNumberInput() {
        assertThat(TextEdits.isValidNumberInput("")).isTrue();
        assertThat(TextEdits.isValidNumberInput("1.5")).isTrue();
        assertThat(TextEdits.isValidNumberInput("-2")).isTrue();
        assertThat(TextEdits.isValidNumberInput("1e5")).isTrue();
        assertThat(TextEdits.isValidNumberInput("1e-5")).isTrue();
        assertThat(TextEdits.isValidNumberInput("1e")).isTrue();
        assertThat(TextEdits.isValidNumberInput("1.")).isTrue();
        assertThat(TextEdits.isValidNumberInput("1e5.")).isFalse();
        assertThat(TextEdits.isValidNumberInput("1e--5")).isFalse();
        assertThat(TextEdits.isValidNumberInput("1..5")).isFalse();
        assertThat(TextEdits.isValidNumberInput("---1")).isFalse();
        assertThat(TextEdits.isValidNumberInput("1,5")).isFalse();
        assertThat(TextEdits.isValidNumberInput("1E5")).isFalse();
    }

    @Test
    void testMaxLength() {
        // Regression: maxlength was ignored
        assertThat(edit("abc", 3, 3, "d", INSERT, "text", 3)).isNull();
        assertThat(edit("ab", 2, 2, "cd", INSERT, "text", 3)).hasToString("abc|3");
        assertThat(edit("abc", 3, 3, "", BACKWARD, "text", 3)).hasToString("ab|2");
        assertThat(TextEdits.maxLength("input", "text", "5")).isEqualTo(5);
        assertThat(TextEdits.maxLength("textarea", null, "10")).isEqualTo(10);
        assertThat(TextEdits.maxLength("input", "number", "5")).isEqualTo(-1);
        assertThat(TextEdits.maxLength("input", "text", null)).isEqualTo(-1);
        assertThat(TextEdits.maxLength("input", "text", "x")).isEqualTo(-1);
        assertThat(TextEdits.maxLength("input", "text", "-1")).isEqualTo(-1);
        // As user-event 14, which lists "telephone" rather than "tel", so tel isn't limited
        assertThat(TextEdits.maxLength("input", "tel", "3")).isEqualTo(-1);
        assertThat(TextEdits.maxLength("input", "email", "3")).isEqualTo(3);
    }

    @Test
    void testIsTextField() {
        assertThat(TextEdits.isTextField("textarea", null)).isTrue();
        assertThat(TextEdits.isTextField("input", "text")).isTrue();
        assertThat(TextEdits.isTextField("input", "number")).isTrue();
        assertThat(TextEdits.isTextField("input", "date")).isTrue();
        assertThat(TextEdits.isTextField("input", "checkbox")).isFalse();
        assertThat(TextEdits.isTextField("div", null)).isFalse();
    }

    @Test
    void testBuildTimeValue() {
        // As user-event's buildTimeValue
        assertThat(TextEdits.buildTimeValue("")).isEmpty();
        assertThat(TextEdits.buildTimeValue("1")).isEqualTo("1");
        assertThat(TextEdits.buildTimeValue("12")).isEqualTo("12");
        assertThat(TextEdits.buildTimeValue("123")).isEqualTo("12:03");
        assertThat(TextEdits.buildTimeValue("1234")).isEqualTo("12:34");
        assertThat(TextEdits.buildTimeValue("9")).isEqualTo("9");
        assertThat(TextEdits.buildTimeValue("93")).isEqualTo("09:03");
        assertThat(TextEdits.buildTimeValue("930")).isEqualTo("09:30");
        assertThat(TextEdits.buildTimeValue("25")).isEqualTo("23:NaN");
        assertThat(TextEdits.buildTimeValue("35")).isEqualTo("03:05");
        assertThat(TextEdits.buildTimeValue("2359")).isEqualTo("23:59");
        assertThat(TextEdits.buildTimeValue("1299")).isEqualTo("12:59");
        assertThat(TextEdits.buildTimeValue("12:3")).isEqualTo("12:03");
        // Two digits that could still be an hour, then nothing after them, aren't a time
        assertThat(TextEdits.buildTimeValue("12:")).isEqualTo("12:NaN");
    }

    @Test
    void testTypingIntoATimeField() {
        // Regression: typing digits into a time field never gave a time, as user-event builds one
        String value = "";
        int offset = 0;
        for (final char chr : "0930".toCharArray()) {
            final Edit edit = TextEdits.edit(value, offset, offset, String.valueOf(chr), "insertText", "time",
                    -1, time -> time.matches("\\d\\d:\\d\\d"));
            value = edit.getValue();
            offset = edit.getOffset();
        }
        assertThat(value).isEqualTo("09:30");
        assertThat(offset).isEqualTo(5);
    }

    private static Edit edit(final String value,
                             final int start,
                             final int end,
                             final String data,
                             final String inputType,
                             final String type,
                             final int maxLength) {
        return TextEdits.edit(value, start, end, data, inputType, type, maxLength);
    }
}
