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

package stroom.ai.impl.tools.generation;

import stroom.ai.impl.tools.sample.HeldText;
import stroom.ai.impl.tools.sample.SourceTypeReading;
import stroom.ai.impl.tools.sample.SourceTypeRows;
import stroom.ai.impl.tools.validation.XmlTrees;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/// The XSLT reading and matching a source type read by a combination of readings
/// ([SourceTypeReading#isCombination]), as the tools read and match it: a function a part (`source-type-value-1`,
/// `source-type-value-2`...), each reading its part as one reading is read ([SourceTypeReadingXslt]), and the key
/// function (`source-type-value`) joining their values by [SourceTypeReading#KEY_SEPARATOR], '' where no part has a
/// value. A row's test splits the key as the tools split it (`tokenize()` on `‖`, each part stripped) and matches it
/// part by part ([SourceTypeRows.TuplePart]): `=` exactly, `~` by an anchored pattern on a part with a value, `∈` an
/// item of a list, `*` any value.
final class SourceTypeTupleXslt {

    /// The variable holding a key's parts, where a function reads a combination's key.
    static final String PARTS = "parts";

    private static final String XSL = XmlTrees.XSL;

    private SourceTypeTupleXslt() {
        // Static only.
    }

    /// Writes the functions reading a combination: a value function a part, the key function, and the functions
    /// they call.
    ///
    /// @param sheet   The stylesheet.
    /// @param namer   A function's name from its base name.
    /// @param reading The combination.
    /// @param input   The mapping's input, how a record's field is read.
    static void write(final XmlElement sheet,
                      final UnaryOperator<String> namer,
                      final SourceTypeReading reading,
                      final String input) {
        final List<String> calls = new ArrayList<>();
        final List<SourceTypeReading> parts = reading.parts();
        for (int i = 0; i < parts.size(); i++) {
            final String name = "source-type-value-" + (i + 1);
            SourceTypeReadingXslt.writeValue(sheet, namer, name, parts.get(i), input);
            calls.add(namer.apply(name) + "($record)");
        }
        final XmlElement key = sheet.addElement(XSL, "function", "name", namer.apply("source-type-value"), "as",
                "xs:string");
        key.append(new XmlComment(XsltComments.comment("A record's source type key, read as the tools read "
                                                       + reading.spec() + ": its parts' values joined by '"
                                                       + SourceTypeReading.KEY_SEPARATOR.strip() + "' ('' for a "
                                                       + "part with none), '' where no part has a value")));
        key.addElement(XSL, "param", "name", "record", "as", "element()");
        key.addElement(XSL, "variable", "name", "values", "as", "xs:string*", "select",
                "(" + String.join(", ", calls) + ")");
        key.addElement(XSL, "sequence", "select", "if (every $v in $values satisfies $v eq '') then '' else "
                                                  + "string-join($values, "
                                                  + XPathText.literal(SourceTypeReading.KEY_SEPARATOR) + ")");
        SourceTypeReadingXslt.writeHelpers(sheet, namer, reading.held());
    }

    /// Declares a key's parts in a function holding the key in `$value`, as the tools split it.
    ///
    /// @param function The function.
    static void declareParts(final XmlElement function) {
        function.addElement(XSL, "variable", "name", PARTS, "as", "xs:string*", "select",
                "for $part in tokenize($value, " + XPathText.literal(SourceTypeReading.KEY_SEPARATOR.strip())
                + ") return replace($part, " + XPathText.literal(HeldText.SPACE_AROUND) + ", '')");
    }

    /// @param parts     A combination row's parts, as the registry keeps them.
    /// @param separator Its list separator, or null.
    /// @return The test of `$parts` matching the row, as [SourceTypeRows.Row#matches] matches it.
    static String rowTest(final String parts, final String separator) {
        final List<SourceTypeRows.TuplePart> each = new SourceTypeRows.Row("", SourceTypeRows.TUPLE, parts,
                separator).parts();
        final List<String> tests = new ArrayList<>();
        tests.add("count($" + PARTS + ") eq " + each.size());
        for (int i = 0; i < each.size(); i++) {
            final SourceTypeRows.TuplePart part = each.get(i);
            final String at = "$" + PARTS + "[" + (i + 1) + "]";
            final String literal = XPathText.literal(part.value());
            if (SourceTypeRows.ANY.equals(part.match())) {
                continue;
            }
            if (SourceTypeRows.PATTERN.equals(part.match())) {
                tests.add("(" + at + " ne '' and matches(" + at + ", " + literal + "))");
            } else if (SourceTypeRows.MEMBER.equals(part.match())) {
                tests.add("(" + at + " ne '' and (some $item in " + XsltGenerator.itemsOf(at, separator)
                          + " satisfies $item = " + literal + "))");
            } else {
                tests.add(at + " = " + literal);
            }
        }
        return String.join(" and ", tests);
    }

    /// @return The `(no value)` row's test of a combination's key: no value at all, or a part with none.
    static String noValueTest() {
        return "$value = '' or (contains($value, " + XPathText.literal(SourceTypeReading.KEY_SEPARATOR.strip())
               + ") and $" + PARTS + " = '')";
    }
}
