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

package stroom.gwt.workbench.framework.client.manager;

import java.util.Locale;
import java.util.Set;

/// Checks values before they are put into markup that `SafeHtmlBuilder` doesn't escape, e.g. CSS
/// class names and inline styles, so that values from the URL, the preview or the workbench
/// server can't inject markup or CSS. Plain Java so it can be unit tested.
public final class HtmlTokens {

    /// The class modifier for an interaction step that hasn't run yet.
    public static final String STEP_WAITING = "waiting";
    /// The class modifier for the interaction step that is running.
    public static final String STEP_ACTIVE = "active";
    /// The class modifier for an interaction step that has passed.
    public static final String STEP_DONE = "done";
    /// The class modifier for an interaction step that has failed.
    public static final String STEP_ERROR = "error";

    private static final Set<String> IMPACTS = Set.of("minor", "moderate", "serious", "critical");
    private static final Set<String> COLOUR_FUNCTIONS = Set.of("rgb", "rgba", "hsl", "hsla");

    private HtmlTokens() {
        // Static utility
    }

    /// @param value A value to use as (part of) a CSS class name or an SVG symbol id.
    /// @return True if the value is non-empty and only has lower case letters, digits, `-` and
    /// `_`, so is safe to put in an attribute without escaping.
    public static boolean isSafeToken(final String value) {
        return value != null && !value.isEmpty() && hasOnlyTokenChars(value, false);
    }

    /// @param value A space separated list of CSS class names.
    /// @return True if the value is non-empty and only has lower case letters, digits, `-`, `_`
    /// and spaces, so is safe to put in a `class` attribute without escaping.
    public static boolean isSafeClassList(final String value) {
        return value != null && !value.trim().isEmpty() && hasOnlyTokenChars(value, true);
    }

    /// Checks a value that must be a constant from the code, e.g. a class name or icon id.
    ///
    /// @param value The value.
    /// @return The value.
    /// @throws IllegalArgumentException If the value isn't a [#isSafeToken(String)] token.
    public static String requireSafeToken(final String value) {
        if (!isSafeToken(value)) {
            throw new IllegalArgumentException("Unsafe token: " + value);
        }
        return value;
    }

    /// Checks a value that must be a constant list of class names from the code.
    ///
    /// @param value The value.
    /// @return The value.
    /// @throws IllegalArgumentException If the value isn't a [#isSafeClassList(String)] list.
    public static String requireSafeClassList(final String value) {
        if (!isSafeClassList(value)) {
            throw new IllegalArgumentException("Unsafe class list: " + value);
        }
        return value;
    }

    /// @param status The status of an interaction step as reported by the preview, e.g. `DONE`.
    /// @return The class modifier for the step, one of [#STEP_WAITING], [#STEP_ACTIVE],
    /// [#STEP_DONE] or [#STEP_ERROR]. Unknown or missing statuses are treated as waiting.
    public static String interactionStepStatus(final String status) {
        if (status == null) {
            return STEP_WAITING;
        }
        switch (status) {
            case "ACTIVE":
                return STEP_ACTIVE;
            case "DONE":
                return STEP_DONE;
            case "ERROR":
                return STEP_ERROR;
            default:
                return STEP_WAITING;
        }
    }

    /// @param impact The impact of an accessibility rule as reported by axe-core.
    /// @return The impact in lower case if it is one of axe-core's (`minor`, `moderate`,
    /// `serious` or `critical`), otherwise null.
    public static String accessibilityImpact(final String impact) {
        if (impact == null) {
            return null;
        }
        final String lowerCase = impact.toLowerCase(Locale.ROOT);
        return IMPACTS.contains(lowerCase)
                ? lowerCase
                : null;
    }

    /// Checks a CSS colour, e.g. one from a story's args in a shared URL, before it is used in an
    /// inline style. Hex colours, `rgb[a]()`/`hsl[a]()` with plain numeric arguments and named
    /// colours are allowed. Anything that could load a resource or break out of the property,
    /// e.g. `url(...)` or `;`, is not.
    ///
    /// @param colour The colour.
    /// @return True if the colour is safe to use as a CSS `background-color`.
    public static boolean isSafeColour(final String colour) {
        if (colour == null) {
            return false;
        }
        final String value = colour.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.length() > 100) {
            return false;
        }
        if (value.charAt(0) == '#') {
            return isHexColour(value.substring(1));
        }
        final int open = value.indexOf('(');
        if (open < 0) {
            // A named colour, e.g. 'rebeccapurple' or 'transparent'
            return isLetters(value);
        }
        if (!COLOUR_FUNCTIONS.contains(value.substring(0, open).trim())
            || value.charAt(value.length() - 1) != ')') {
            return false;
        }
        final String arguments = value.substring(open + 1, value.length() - 1);
        for (int i = 0; i < arguments.length(); i++) {
            if (!isColourArgumentChar(arguments.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasOnlyTokenChars(final String value, final boolean allowSpaces) {
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            final boolean ok = (c >= 'a' && c <= 'z')
                               || (c >= '0' && c <= '9')
                               || c == '-'
                               || c == '_'
                               || (allowSpaces && c == ' ');
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHexColour(final String digits) {
        final int length = digits.length();
        if (length != 3 && length != 4 && length != 6 && length != 8) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            final char c = digits.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLetters(final String value) {
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (c < 'a' || c > 'z') {
                return false;
            }
        }
        return true;
    }

    private static boolean isColourArgumentChar(final char c) {
        // Numbers, separators and units such as 'deg', 'turn' or 'none', but no brackets, quotes
        // or semicolons, so nothing can be nested or escape the property
        return (c >= '0' && c <= '9')
               || (c >= 'a' && c <= 'z')
               || c == ' '
               || c == '.'
               || c == ','
               || c == '%'
               || c == '/'
               || c == '-'
               || c == '+';
    }
}
