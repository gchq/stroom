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

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/// How typing or deleting changes the value of a text field, as `userEvent.type`/`keyboard`
/// work it out (user-event 14's `editInputElement`), kept free of the DOM so it can be tested on
/// the JVM. [Dom] reads the field's value and selection, asks [#edit(String, int, int, String,
/// String, String, int)] and applies the result.
///
/// Like user-event, the value being typed (the "UI value") can differ from the field's `value`
/// property, e.g. typing `1.5` into a number field passes through `1.`, which the browser
/// sanitises to an empty value; the UI value is kept so the next character gives `1.5`.
final class TextEdits {

    /// The input types user-event can type into, besides text areas.
    private static final List<String> EDITABLE_INPUT_TYPES = Arrays.asList(
            "text", "date", "datetime-local", "email", "month", "number", "password", "search", "tel", "time",
            "url", "week");
    /// The input types whose `maxlength` limits typing, exactly as user-event 14 lists them,
    /// including its quirk: it lists `telephone` (which is never an input's type) rather than
    /// `tel`, so `maxlength` doesn't limit typing into a `tel` input.
    private static final List<String> MAX_LENGTH_INPUT_TYPES = Arrays.asList(
            "email", "password", "search", "telephone", "text", "url");

    private TextEdits() {
        // Static utility
    }

    /// @param localName    The element's local name, e.g. `input`.
    /// @param typeProperty For an `<input>`, its `type` property; otherwise ignored.
    /// @return True if user-event treats the element as a text field it can type into, i.e. a
    /// text area or an input of an editable type (whether or not it is read only or disabled).
    static boolean isTextField(final String localName, final String typeProperty) {
        return "textarea".equals(localName)
               || ("input".equals(localName) && typeProperty != null && EDITABLE_INPUT_TYPES.contains(typeProperty));
    }

    /// @param localName    The element's local name.
    /// @param typeProperty For an `<input>`, its `type` property.
    /// @param maxLength    The `maxlength` attribute, or null.
    /// @return The maximum length typing can give the field, or -1 for none.
    static int maxLength(final String localName, final String typeProperty, final String maxLength) {
        final boolean supported = "textarea".equals(localName)
                                  || ("input".equals(localName) && MAX_LENGTH_INPUT_TYPES.contains(typeProperty));
        if (!supported || maxLength == null || maxLength.isEmpty()) {
            return -1;
        }
        int value = 0;
        for (int i = 0; i < maxLength.length(); i++) {
            final char chr = maxLength.charAt(i);
            if (chr < '0' || chr > '9') {
                return -1;
            }
            value = value * 10 + (chr - '0');
        }
        return value;
    }

    /// Works out the result of typing (or deleting) in a field, as user-event does.
    ///
    /// @param value        The field's current UI value.
    /// @param start        The start of the selection (or the caret).
    /// @param end          The end of the selection (or the caret).
    /// @param data         The text typed, or an empty string to delete.
    /// @param inputType    The `inputType` of the `input` event, e.g. `insertText`,
    ///                     `deleteContentBackward` or `deleteContentForward`.
    /// @param typeProperty For an `<input>`, its `type` property, e.g. `number`; otherwise null.
    /// @param maxLength    The maximum length, or -1 for none.
    /// @return The edit, or null if nothing changes, e.g. the field is full or the result isn't
    /// a number a number field accepts (as typing a letter into it does nothing).
    static Edit edit(final String value,
                     final int start,
                     final int end,
                     final String data,
                     final String inputType,
                     final String typeProperty,
                     final int maxLength) {
        return edit(value, start, end, data, inputType, typeProperty, maxLength, time -> false);
    }

    /// Works out the result of typing (or deleting) in a field, as user-event does, including
    /// building a time field's `HH:MM` value from the digits typed.
    ///
    /// @param value            The field's current UI value.
    /// @param start            The start of the selection (or the caret).
    /// @param end              The end of the selection (or the caret).
    /// @param data             The text typed, or an empty string to delete.
    /// @param inputType        The `inputType` of the `input` event.
    /// @param typeProperty     For an `<input>`, its `type` property; otherwise null.
    /// @param maxLength        The maximum length, or -1 for none.
    /// @param isValidDateOrTime For a time field, whether the browser takes a value, e.g.
    ///                          `09:30`.
    /// @return The edit, or null if nothing changes.
    static Edit edit(final String value,
                     final int start,
                     final int end,
                     final String data,
                     final String inputType,
                     final String typeProperty,
                     final int maxLength,
                     final Predicate<String> isValidDateOrTime) {
        String toInsert = data;
        if (maxLength >= 0 && !data.isEmpty()) {
            final int space = maxLength - value.length();
            if (space <= 0) {
                return null;
            }
            toInsert = data.substring(0, Math.min(data.length(), space));
        }
        final int safeStart = clamp(Math.min(start, end), value.length());
        final int safeEnd = clamp(Math.max(start, end), value.length());
        final boolean collapsed = safeStart == safeEnd;
        final int prologEnd = Math.max(0, collapsed && "deleteContentBackward".equals(inputType)
                ? safeStart - 1
                : safeStart);
        final int epilogStart = Math.min(value.length(), collapsed && "deleteContentForward".equals(inputType)
                ? safeStart + 1
                : safeEnd);
        String newValue = value.substring(0, prologEnd) + toInsert + value.substring(epilogStart);
        int newOffset = prologEnd + toInsert.length();
        if ("time".equals(typeProperty)) {
            final String built = buildTimeValue(newValue);
            if (!built.isEmpty() && isValidDateOrTime.test(built)) {
                newValue = built;
                newOffset = built.length();
            }
        }
        if (newValue.equals(value) && newOffset == safeStart && newOffset == safeEnd) {
            return null;
        }
        if ("number".equals(typeProperty) && !isValidNumberInput(newValue)) {
            return null;
        }
        return new Edit(newValue, newOffset);
    }

    /// Builds a time from the digits typed into a time field, as user-event's `buildTimeValue`
    /// does, e.g. `9` stays `9`, `930` becomes `09:30` and `2359` becomes `23:59`.
    ///
    /// @param value The text typed so far.
    /// @return The time as `HH:MM`, or the text unchanged if there are fewer than two digits (or
    /// exactly two characters that could still be an hour).
    static String buildTimeValue(final String value) {
        final StringBuilder digits = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            final char chr = value.charAt(i);
            if (chr >= '0' && chr <= '9') {
                digits.append(chr);
            }
        }
        if (digits.length() < 2) {
            return value;
        }
        final int firstDigit = digits.charAt(0) - '0';
        final int secondDigit = digits.charAt(1) - '0';
        if (firstDigit >= 3 || (firstDigit == 2 && secondDigit >= 4)) {
            return buildTime(digits.toString(), firstDigit >= 3
                    ? 1
                    : 2);
        }
        if (value.length() == 2) {
            return value;
        }
        return buildTime(digits.toString(), 2);
    }

    // As user-event's build(): hours from the digits before the index (at most 23), minutes from
    // the rest (at most 59), each padded to two digits. With no minute digits JavaScript's
    // parseInt gives NaN, which shows as "NaN", so the result is never a valid time.
    private static String buildTime(final String digits, final int index) {
        final int hours = Math.min(Integer.parseInt(digits.substring(0, index)), 23);
        final String minuteDigits = digits.substring(index);
        final String minutes = minuteDigits.isEmpty()
                ? "NaN"
                : pad(Math.min(parseLeadingInt(minuteDigits), 59));
        return pad(hours) + ":" + minutes;
    }

    // As JavaScript's parseInt for a string of digits, which may be too long for an int
    private static int parseLeadingInt(final String digits) {
        return digits.length() > 9
                ? Integer.MAX_VALUE
                : Integer.parseInt(digits);
    }

    private static String pad(final int number) {
        return number < 10
                ? "0" + number
                : String.valueOf(number);
    }

    /// Whether a browser lets the text be typed into a number field, as user-event checks: only
    /// digits, `.`, `-` and `e`, with at most two `-`, one `.` and, after an `e`, an optional `-`
    /// then digits.
    ///
    /// @param value The text.
    /// @return True if it can be typed.
    static boolean isValidNumberInput(final String value) {
        int minuses = 0;
        int dots = 0;
        for (int i = 0; i < value.length(); i++) {
            final char chr = value.charAt(i);
            if (chr == '-') {
                minuses++;
            } else if (chr == '.') {
                dots++;
            } else if (chr != 'e' && (chr < '0' || chr > '9')) {
                return false;
            }
        }
        if (minuses > 2 || dots > 1) {
            return false;
        }
        final int e = value.indexOf('e');
        if (e >= 0) {
            // As JavaScript's value.split('e', 2)[1]: the text up to any second 'e'
            final int nextE = value.indexOf('e', e + 1);
            final String exponent = value.substring(e + 1, nextE >= 0
                    ? nextE
                    : value.length());
            if (!exponent.isEmpty() && !isExponent(exponent)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isExponent(final String text) {
        final int digitsStart = text.startsWith("-")
                ? 1
                : 0;
        for (int i = digitsStart; i < text.length(); i++) {
            final char chr = text.charAt(i);
            if (chr < '0' || chr > '9') {
                return false;
            }
        }
        return true;
    }

    private static int clamp(final int position, final int length) {
        return Math.max(0, Math.min(position, length));
    }


    // --------------------------------------------------------------------------------


    /// The result of typing or deleting in a field.
    static final class Edit {

        private final String value;
        private final int offset;

        /// @param value  The new UI value.
        /// @param offset Where the caret ends up.
        Edit(final String value, final int offset) {
            this.value = value;
            this.offset = offset;
        }

        /// @return The new UI value.
        String getValue() {
            return value;
        }

        /// @return Where the caret ends up.
        int getOffset() {
            return offset;
        }

        @Override
        public String toString() {
            return value + "|" + offset;
        }
    }
}
