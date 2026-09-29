/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.floormap.client.value;

import stroom.util.shared.NullSafe;

/// The text-level rules shared by every XML [ValueAccessor] implementation:
/// how a numeric array is spelled in element text, and how a value is escaped on
/// the way out.
///
/// **Why this exists rather than living in the accessor.** The
/// production XML accessor is a GWT client class built on GWT's DOM, so it cannot
/// run on the JVM, and the JVM-testable accessor used by the unit tests is a
/// separate implementation over `org.w3c.dom`. When these rules were written
/// out twice — once in each — the two copies drifted, and the drift was invisible:
/// the tests exercised the copy in the double, so they passed while production
/// behaved differently. Two real examples, both found in review:
///
/// - The double zero-filled an unparseable token where production returned an
///   array, or vice versa — a disagreement on the single branch that decides
///   whether corrupt data is rejected or silently accepted.
/// - One copy escaped `'` as `&amp;apos;` and the other did not.
///
/// Neither rule needs a DOM, so neither has any reason to be duplicated.
/// Holding them here makes the two accessors share one implementation, which turns
/// "we must remember to keep these in step, and test that we did" into something
/// that cannot drift in the first place. What genuinely needs a DOM — walking and
/// building nodes — stays in each accessor, and is what the shared contract test
/// covers.
///
/// Holds no GWT or DOM types, so it compiles to JavaScript and runs on the JVM.
public final class XmlValueText {

    private XmlValueText() {
        // Utility class.
    }

    /// Parses element text holding comma-separated numbers.
    ///
    /// **All or nothing.** If any token is not a number the whole
    /// array is malformed and `null` is returned, per
    /// [ValueAccessor#getArray]. Substituting a default for the offending
    /// token fabricates data the caller cannot distinguish from real data — a
    /// matrix of six unparseable values became six zeroes, which is a
    /// structurally valid but degenerate transform.
    ///
    /// @param text the element text; `null` or empty yields `null`
    /// @return the parsed numbers, or `null` if the text is absent, empty or
    ///         contains any non-numeric token
    public static double[] parseCommaSeparatedNumbers(final String text) {
        if (NullSafe.isEmptyString(text)) {
            return null;
        }
        final String[] parts = text.split(",");
        final double[] result = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Double.parseDouble(parts[i].trim());
            } catch (final NumberFormatException e) {
                return null;
            }
        }
        return result;
    }

    /// Escapes text for inclusion in element content or an attribute value.
    ///
    /// Escapes all five XML predefined entities. `>` does not strictly
    /// require escaping in content, and `'` does not in a double-quoted
    /// attribute, but escaping both is always valid and means one routine serves
    /// content and attributes alike — the alternative being two nearly-identical
    /// routines and a chance to use the wrong one.
    ///
    /// @param text the text to escape; `null` yields an empty string so
    ///         callers can append the result unconditionally
    /// @return the escaped text; never `null`
    public static String escapeXml(final String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
