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
import stroom.ai.impl.tools.validation.XmlTrees;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/// The XSLT functions reading a record's source type through a reading into held values ([SourceTypeReading]), as
/// the tools read it ([HeldText]): each held value's format told per record by the same rules in the same order
/// (JSON, encoded once or twice; XML; a syslog header; key=value pairs; else text, never read), with the same
/// regular expressions, and stepped into the same way (JSON keys; XML elements and attributes by local name, the
/// first in document order; a key's first value; a syslog field). Each reading's value is stripped and a nil value
/// is none, then the first reading with a value gives it. So processing counts the same records as each source type
/// as the survey, the scope and the registry do.
///
/// Every function is named by the caller (a source type group prefixes its names with its mode), so the functions of
/// two groups never meet.
final class SourceTypeReadingXslt {

    private static final String XSL = XmlTrees.XSL;
    private static final String S = "'s'";

    private SourceTypeReadingXslt() {
        // Static only.
    }

    /// Writes the function reading a record's source type value through a reading, and the functions it calls.
    ///
    /// @param sheet   The stylesheet.
    /// @param namer   A function's name from its base name (`source-type-value`, `held-read`...).
    /// @param reading The reading.
    /// @param input   The mapping's input (`json`, `data_splitter`, ...), how a record's field is read.
    static void write(final XmlElement sheet,
                      final UnaryOperator<String> namer,
                      final SourceTypeReading reading,
                      final String input) {
        writeValue(sheet, namer, "source-type-value", reading, input);
        writeHelpers(sheet, namer, true);
    }

    /// Writes the function reading a record's value through one reading (not a combination), by the helper
    /// functions [#writeHelpers] writes.
    ///
    /// @param sheet   The stylesheet.
    /// @param namer   A function's name from its base name.
    /// @param name    The function's base name, e.g. `source-type-value` or `source-type-value-2`.
    /// @param reading The reading.
    /// @param input   The mapping's input, how a record's field is read.
    static void writeValue(final XmlElement sheet,
                           final UnaryOperator<String> namer,
                           final String name,
                           final SourceTypeReading reading,
                           final String input) {
        final XmlElement value = sheet.addElement(XSL, "function", "name", namer.apply(name), "as", "xs:string");
        value.append(new XmlComment(XsltComments.comment("A record's source type value, read as the tools read "
                                                         + reading.spec() + ": " + reading.said() + "; stripped, "
                                                         + "'' for none or a nil value")));
        value.addElement(XSL, "param", "name", "record", "as", "element()");
        final List<String> readings = new ArrayList<>();
        for (final SourceTypeReading.Path path : reading.paths()) {
            // A field's value, as the tools take it: a string, number or boolean, not an object or a list.
            String read = "string(($record/" + XPathText.fieldText(input, path.field()) + ")[1][not(*)])";
            for (final SourceTypeReading.Step step : path.held()) {
                read = call(namer, "reading-held", read, literal(step.format()), literal(step.special()),
                        literal(step.raw()), literal(HeldText.keyRegex(step.raw())), sequence(step.names()),
                        sequence(step.localNames()), literal(step.attribute()));
            }
            readings.add(call(namer, "reading-value", read));
        }
        value.addElement(XSL, "sequence", "select", "string((" + String.join(", ", readings) + ")[. ne ''][1])");
    }

    /// Writes the functions the value functions call: the normaliser, and, where a reading steps into held values,
    /// the functions telling and reading a held value's format.
    ///
    /// @param sheet The stylesheet.
    /// @param namer A function's name from its base name.
    /// @param held  True if a reading steps into held values.
    static void writeHelpers(final XmlElement sheet, final UnaryOperator<String> namer, final boolean held) {
        normaliser(sheet, namer);
        if (!held) {
            return;
        }
        heldRead(sheet, namer);
        heldJson(sheet, namer);
        heldXml(sheet, namer);
        jsonAt(sheet, namer);
        xmlAt(sheet, namer);
    }

    // A value as the tools read it: stripped of control characters, then of XML's white space, '' for a nil value.
    private static void normaliser(final XmlElement sheet, final UnaryOperator<String> namer) {
        final XmlElement function = function(sheet, namer, "reading-value", "xs:string",
                "A value read, stripped of control characters, then of XML's white space; '' for a nil value");
        function.addElement(XSL, "param", "name", "value", "as", "xs:string?");
        function.addElement(XSL, "variable", "name", "stripped", "select", "replace(replace(string($value), "
                                                                           + literal(HeldText.CONTROL) + ", ''), "
                                                                           + literal(HeldText.SPACE_AROUND)
                                                                           + ", '')");
        function.addElement(XSL, "sequence", "select", "if (lower-case($stripped) = (" + HeldText.NIL_VALUES.stream()
                .map(XPathText::literal).collect(Collectors.joining(", ")) + ")) then '' else $stripped");
    }

    // One step into a held value: its format told (the first of JSON, XML, syslog, key=value that applies, else
    // text), then the step's names read in it; '' where the format isn't the step's or nothing is found.
    private static void heldRead(final XmlElement sheet, final UnaryOperator<String> namer) {
        final XmlElement function = function(sheet, namer, "reading-held", "xs:string",
                "One step into a held value, as the tools take it: its format told per record (JSON, XML, a syslog "
                + "header, key=value pairs, else text, never read), then the step read in it");
        for (final String[] param : new String[][]{{"text", "xs:string?"}, {"format", "xs:string"},
                {"special", "xs:string"}, {"key", "xs:string"}, {"pair", "xs:string"}, {"names", "xs:string*"},
                {"local", "xs:string*"}, {"attribute", "xs:string"}}) {
            function.addElement(XSL, "param", "name", param[0], "as", param[1]);
        }
        function.addElement(XSL, "variable", "name", "t", "select", "replace(string($text), "
                                                                    + literal(HeldText.AROUND) + ", '')");
        function.addElement(XSL, "variable", "name", "json", "as", "element()?", "select",
                call(namer, "reading-json", "$t"));
        function.addElement(XSL, "variable", "name", "xml", "as", "element()?", "select",
                "if (exists($json)) then () else " + call(namer, "reading-xml", "$t"));
        function.addElement(XSL, "variable", "name", "found", "as", "xs:string", "select",
                "if (exists($json)) then '" + HeldText.JSON + "' else if (exists($xml)) then '" + HeldText.XML
                + "' else if ($t ne '' and (matches($t, " + literal(HeldText.SYSLOG_5424) + ", " + S
                + ") or matches($t, " + literal(HeldText.SYSLOG_3164) + ", " + S + "))) then '" + HeldText.SYSLOG
                + "' else if ($t ne '' and count(tokenize($t, " + literal(HeldText.PAIR) + ")) - 1 ge "
                + HeldText.MIN_PAIRS + ") then '" + HeldText.KEY_VALUE + "' else '" + HeldText.TEXT + "'");
        final String plain = "$special ne '' or $attribute ne ''";
        function.addElement(XSL, "sequence", "select",
                "if ($t eq '' or ($format ne '' and $format ne $found)) then ''"
                + " else if ($special eq " + literal(SourceTypeReading.FORMAT_STEP) + ") then $found"
                + " else if ($found eq '" + HeldText.JSON + "') then (if (" + plain + ") then '' else "
                + call(namer, "reading-json-at", "$json", "$names") + ")"
                + " else if ($found eq '" + HeldText.XML + "') then (if ($special eq "
                + literal(SourceTypeReading.ROOT_STEP) + ") then local-name($xml) else if ($special ne '') then '' "
                + "else " + call(namer, "reading-xml-at", "$xml", "$local", "$attribute") + ")"
                + " else if ($found eq '" + HeldText.SYSLOG + "') then (if (" + plain + ") then '' else "
                + syslog() + ")"
                + " else if ($found eq '" + HeldText.KEY_VALUE + "') then (if (" + plain + " or $pair eq '') then '' "
                + "else if (matches($t, $pair, " + S + ")) then replace($t, $pair, '$3$4', " + S + ") else '')"
                + " else ''");
    }

    // A syslog header's field: the RFC 5424 header's group, else the RFC 3164 header's.
    private static String syslog() {
        final StringBuilder out = new StringBuilder();
        for (final String field : HeldText.SYSLOG_FIELDS) {
            final List<Integer> groups = HeldText.SYSLOG_GROUPS.get(field);
            out.append("if ($key eq ").append(XPathText.literal(field)).append(") then (if (matches($t, ")
                    .append(literal(HeldText.SYSLOG_5424)).append(", ").append(S).append(")) then replace($t, ")
                    .append(literal(HeldText.SYSLOG_5424)).append(", '$").append(groups.get(0)).append("', ")
                    .append(S).append(") else replace($t, ").append(literal(HeldText.SYSLOG_3164)).append(", '$")
                    .append(groups.get(1)).append("', ").append(S).append(")) else ");
        }
        return out.append("''").toString();
    }

    // A JSON object held in a text: the text an object, or a JSON string whose text is one (JSON encoded twice);
    // nothing for a text that isn't one or doesn't parse.
    private static void heldJson(final XmlElement sheet, final UnaryOperator<String> namer) {
        final XmlElement function = function(sheet, namer, "reading-json", "element()?",
                "The JSON object a held text is (or a JSON string holding one), or nothing");
        function.addElement(XSL, "param", "name", "t", "as", "xs:string");
        final XmlElement attempt = function.addElement(XSL, "try");
        attempt.addElement(XSL, "sequence", "select",
                "if (starts-with($t, '{')) then json-to-xml($t)/*[local-name() eq 'map'] else if (starts-with($t, "
                + "'\"')) then (let $inner := replace(string(json-to-xml($t)/*[local-name() eq 'string']), "
                + literal(HeldText.AROUND) + ", '') return if (starts-with($inner, '{')) then json-to-xml($inner)"
                + "/*[local-name() eq 'map'] else ()) else ()");
        attempt.addElement(XSL, "catch").addElement(XSL, "sequence", "select", "()");
    }

    // An XML document held in a text, its root element; nothing for a text that isn't one, doesn't parse or holds a
    // document type declaration.
    private static void heldXml(final XmlElement sheet, final UnaryOperator<String> namer) {
        final XmlElement function = function(sheet, namer, "reading-xml", "element()?",
                "The root element of the XML document a held text is, or nothing");
        function.addElement(XSL, "param", "name", "t", "as", "xs:string");
        // A document type declaration makes it no XML, as the tools take it (they read no DTD).
        final XmlElement attempt = function.addElement(XSL, "if", "test", "starts-with($t, '<') and not(contains($t, "
                                                                          + literal(HeldText.DOCTYPE) + "))")
                .addElement(XSL, "try");
        attempt.addElement(XSL, "sequence", "select", "parse-xml($t)/*");
        attempt.addElement(XSL, "catch").addElement(XSL, "sequence", "select", "()");
    }

    // A JSON object's value at the keys given, a scalar only.
    private static void jsonAt(final XmlElement sheet, final UnaryOperator<String> namer) {
        final XmlElement function = function(sheet, namer, "reading-json-at", "xs:string",
                "A held JSON object's value at the keys given: a string, number or boolean, else ''");
        function.addElement(XSL, "param", "name", "node", "as", "element()?");
        function.addElement(XSL, "param", "name", "names", "as", "xs:string*");
        // The last of a key given twice, as the tools' JSON reading keeps it.
        final String next = call(namer, "reading-json-at", "($node/*[@key eq $names[1]])[last()]",
                "subsequence($names, 2)");
        function.addElement(XSL, "sequence", "select", "if (empty($node)) then '' else if (empty($names)) then (if "
                                                       + "(local-name($node) = ('string', 'number', 'boolean')) "
                                                       + "then string($node) else '') else " + next);
    }

    // An XML document's value at the elements given by local name, then an attribute: the first in document order.
    private static void xmlAt(final XmlElement sheet, final UnaryOperator<String> namer) {
        final XmlElement function = function(sheet, namer, "reading-xml-at", "xs:string",
                "A held XML document's value at the elements given (by local name, below the root), then an "
                + "attribute: the first's in document order");
        function.addElement(XSL, "param", "name", "nodes", "as", "element()*");
        function.addElement(XSL, "param", "name", "names", "as", "xs:string*");
        function.addElement(XSL, "param", "name", "attribute", "as", "xs:string");
        final String next = call(namer, "reading-xml-at", "$nodes/*[local-name() eq $names[1]]",
                "subsequence($names, 2)", "$attribute");
        function.addElement(XSL, "sequence", "select", "if (exists($names)) then " + next + " else if ($attribute "
                                                       + "ne '') then string(($nodes/@*[local-name() eq "
                                                       + "$attribute])[1]) else string($nodes[1])");
    }

    private static XmlElement function(final XmlElement sheet,
                                       final UnaryOperator<String> namer,
                                       final String name,
                                       final String type,
                                       final String comment) {
        final XmlElement function = sheet.addElement(XSL, "function", "name", namer.apply(name), "as", type);
        function.append(new XmlComment(XsltComments.comment(comment)));
        return function;
    }

    private static String call(final UnaryOperator<String> namer, final String name, final String... args) {
        return namer.apply(name) + "(" + String.join(", ", args) + ")";
    }

    private static String literal(final String text) {
        return XPathText.literal(text == null
                ? ""
                : text);
    }

    private static String sequence(final List<String> items) {
        return "(" + items.stream().map(XPathText::literal).collect(Collectors.joining(", ")) + ")";
    }
}
