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

package stroom.widget.form.client;

import java.util.function.Function;

/// Checks of a text box's text that many boxes share, for [TextBox#setInvalidReason(Function)]. Each
/// gives why the text can't be kept, or null if it can.
public final class InputChecks {

    private InputChecks() {
        // Static utility
    }

    /// @param min The least the number may be.
    /// @param max The most the number may be.
    /// @return A check that the text is a number from `min` to `max`, e.g. `0.8` for 0 to 1.
    public static Function<String, String> numberFrom(final double min, final double max) {
        final String reason = "Must be a number from " + format(min) + " to " + format(max);
        return text -> {
            final Double number = parseNumber(text);
            return number == null || number < min || number > max
                    ? reason
                    : null;
        };
    }

    /// @param example An example list, e.g. `1000000,100,10`, for the reason.
    /// @return A check that the text is blank, or whole numbers of 1 or more separated by commas.
    public static Function<String, String> wholeNumberList(final String example) {
        final String reason = "Must be whole numbers separated by commas, e.g. " + example;
        return text -> {
            if (text == null || text.trim().isEmpty()) {
                return null;
            }
            for (final String part : text.split(",", -1)) {
                final Double number = parseNumber(part);
                if (number == null || number < 1 || number != Math.rint(number)) {
                    return reason;
                }
            }
            return null;
        };
    }

    /// @param text Text that may be a number, with spaces around it.
    /// @return The number, or null if the text isn't one.
    public static Double parseNumber(final String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            final double number = Double.parseDouble(text.trim());
            return Double.isNaN(number) || Double.isInfinite(number)
                    ? null
                    : number;
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    // 1 rather than 1.0
    private static String format(final double number) {
        return number == Math.rint(number)
                ? String.valueOf((long) number)
                : String.valueOf(number);
    }
}
