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

package stroom.ai.impl.tools.validation;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// What Stroom's XSLT engine can't run. Stroom runs Saxon-HE 9.9, which has XPath 3.1 and XSLT 3.0 (let, `!`, `=>`,
/// maps and arrays, xsl:iterate, xsl:for-each-group) but no higher-order functions: inline functions, function
/// references (`upper-case#1`), the functions that take a function (fold-left, for-each, filter, sort with a key...)
/// and string constructors all fail to compile, Saxon saying they need Saxon-PE, which Stroom doesn't have. Checked
/// against Stroom's own Saxon, case by case.
public final class StroomXPath {

    /// The engine Stroom runs, as said to the agent.
    public static final String ENGINE = "Stroom's XSLT engine (Saxon-HE 9.9)";
    /// What to use instead, as said to the agent.
    public static final String INSTEAD = "write it with for, let, if, the ! and => operators, or in XSLT with "
                                         + "xsl:iterate or xsl:for-each-group (Saxon-PE, which has them, is not an "
                                         + "option in Stroom)";

    // String literals, whose text isn't XPath.
    private static final Pattern LITERAL = Pattern.compile("'(?:[^']|'')*'|\"(?:[^\"]|\"\")*\"");
    private static final Pattern INLINE_FUNCTION = Pattern.compile("(?<![\\w:.-])function\\s*\\(");
    private static final Pattern FUNCTION_REFERENCE = Pattern.compile("(?<![\\w.-])[\\p{L}_][\\w.:-]*#\\d+");
    private static final Pattern STRING_CONSTRUCTOR = Pattern.compile("``\\[");
    // The functions that take a function, in the standard, array and map namespaces.
    private static final Pattern HIGHER_ORDER_CALL = Pattern.compile(
            "(?<![\\w.-])(?:(?:fn|array):)?(fold-left|fold-right|for-each|for-each-pair|filter|apply|function-name"
            + "|function-arity)\\s*\\(|(?<![\\w.-])map:(for-each)\\s*\\(");
    private static final List<Pattern> FORMS = List.of(INLINE_FUNCTION, FUNCTION_REFERENCE, STRING_CONSTRUCTOR);
    private static final List<String> FORM_NAMES = List.of("an inline function", "a function reference",
            "a string constructor");

    private StroomXPath() {
        // Static only.
    }

    /// @param xpath An XPath expression.
    /// @return Why Stroom can't run it, or empty if nothing in it is known to fail.
    public static Optional<String> unsupported(final String xpath) {
        if (xpath == null || xpath.isBlank()) {
            return Optional.empty();
        }
        final String code = LITERAL.matcher(xpath).replaceAll("''");
        for (int n = 0; n < FORMS.size(); n++) {
            final Matcher found = FORMS.get(n).matcher(code);
            if (found.find()) {
                return Optional.of(said(FORM_NAMES.get(n) + " (" + found.group().strip() + ")"));
            }
        }
        final Matcher call = HIGHER_ORDER_CALL.matcher(code);
        if (call.find()) {
            return Optional.of(said(call.group(1) != null
                    ? call.group(1) + "()"
                    : "map:" + call.group(2) + "()"));
        }
        return Optional.empty();
    }

    /// @param message A compile error, as Saxon gives it.
    /// @return It, with what it means in Stroom when Saxon says the feature needs a higher edition.
    public static String explained(final String message) {
        if (message == null || !message.contains("Saxon-PE")) {
            return message;
        }
        return message + " (" + ENGINE + " has no higher-order functions: " + INSTEAD + ")";
    }

    private static String said(final String what) {
        return ENGINE + " has no higher-order functions, so it can't run " + what + ": " + INSTEAD;
    }
}
