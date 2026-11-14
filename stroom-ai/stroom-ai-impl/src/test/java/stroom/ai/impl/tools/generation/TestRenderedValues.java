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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigInteger;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The generated functions of `fill_template` and `mask_names` ([RenderXslt]) run through Saxon, and compared with
/// the previews of the same in Java ([FillTemplate#fill], [MaskNames#named]); then whole mappings using them.
class TestRenderedValues extends XsltRuns {

    private static final String PLACEHOLDER = "%(\\d+)";
    private static final String ESCAPED = "%%|%(\\d+)";
    // A table: single bits (0, 1, 4, 52, 53, 63) and one of two bits (0 and 1).
    private static final List<Map.Entry<String, String>> TABLE = List.of(
            entry("0x1", "One"),
            entry("0x2", "Two"),
            entry("16", "Sixteen"),
            entry("0x0010000000000000", "Failure"),
            entry("0x0020000000000000", "Success"),
            entry("0x8000000000000000", "Top"),
            entry("0x3", "Both"),
            entry("0x40", ""),
            entry("0x2", "Two"));

    // --------------------------------------------------------------------------------
    // mask_names

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "0x8000000000000001|One,Top",
            "9223372036854775809|One,Top",
            "0X10|Sixteen",
            "'  0x2 '|Two",
            "3|One,Two,Both",
            "0x0030000000000000|Failure,Success",
            "0|''",
            "18446744073709551615|One,Two,Sixteen,Failure,Success,Top,Both"})
    void aMaskIsNamedByEveryMaskItHolds(final String value, final String expected) {
        final List<String> want = expected.isEmpty()
                ? List.of()
                : List.of(expected.split(","));
        assertThat(maskNames(value)).as("the XSLT").isEqualTo(want);
        assertThat(MaskNames.named(value, TABLE).names()).as("the preview").isEqualTo(want);
    }

    @Test
    void bit63IsExactAndItsHexIsWritten() {
        assertThat(MaskNames.parse("0x8000000000000000")).isEqualTo(BigInteger.ONE.shiftLeft(63));
        assertThat(MaskNames.parse("18446744073709551615")).isEqualTo(MaskNames.MAX);
        assertThat(MaskNames.hex(63)).isEqualTo("0x8000000000000000");
        assertThat(MaskNames.hex(52)).isEqualTo("0x10000000000000");
        final List<Map.Entry<String, String>> low = List.of(entry("0x1", "One"));
        assertThat(maskProblem("0x8000000000000001", low)).isEqualTo("no name for bit 63 (0x8000000000000000) of "
                                                                      + "0x8000000000000001");
        assertThat(MaskNames.named("0x8000000000000001", low).unnamed()).containsExactly(63);
    }

    @Test
    void theBitsNoNameCoversAreReported() {
        assertThat(maskProblem("0x8000000000000045", TABLE))
                .as("bit 2, and bit 6, whose mask's name is blank")
                .isEqualTo("no name for bits 2 (0x4), 6 (0x40) of 0x8000000000000045");
        assertThat(MaskNames.named("0x8000000000000045", TABLE).unnamed()).containsExactly(2, 6);
        assertThat(maskProblem("0x8000000000000003", TABLE)).as("every bit named").isNull();
        assertThat(maskProblem("0", TABLE)).as("no bits").isNull();
        assertThat(maskProblem("", TABLE)).as("no value").isNull();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"abc", "18446744073709551616", "0x10000000000000000", "-1", "1.5", "0x"})
    void aValueThatIsNoMaskIsReportedAndNamedNothing(final String value) {
        assertThat(maskNames(value)).isEmpty();
        assertThat(maskProblem(value, TABLE)).contains("is not an unsigned number of up to 64 bits");
        assertThat(MaskNames.named(value, TABLE).notANumber()).isTrue();
        assertThat(MaskNames.parse(value)).isNull();
    }

    @Test
    void everySingleBitIsNamedAlike() {
        final List<Map.Entry<String, String>> table = IntStream.range(0, 64)
                .mapToObj(bit -> entry(MaskNames.hex(bit), "b" + bit))
                .toList();
        for (final int bit : List.of(0, 1, 31, 32, 62, 63)) {
            final String decimal = BigInteger.ONE.shiftLeft(bit).toString();
            assertThat(maskNames(decimal, table)).as("bit %s", bit).containsExactly("b" + bit);
        }
        assertThat(maskNames("0xFFFFFFFFFFFFFFFF", table)).hasSize(64);
    }

    @Test
    void aMaskTableIsChecked() {
        assertThatThrownBy(() -> entryOf(Map.of("path", "EventDetail/Authenticate/Data", "data_name", "k",
                "field", "mask", "mask_names", Map.of("names", Map.of("0", "None"))))).hasMessageContaining(
                "isn't a mask");
        assertThatThrownBy(() -> entryOf(Map.of("path", "EventDetail/Authenticate/Data", "data_name", "k",
                "field", "mask", "mask_names", Map.of("names", Map.of("0x10000000000000000", "Big")))))
                .hasMessageContaining("isn't a mask");
        assertThatThrownBy(() -> entryOf(Map.of("path", "EventDetail/Authenticate/Data", "data_name", "k",
                "field", "mask", "mask_names", Map.of("lookup", Map.of("map", "M", "field", "k",
                        "path", "t/e")))))
                .hasMessageContaining("needs mask and name");
        assertThatThrownBy(() -> entryOf(Map.of("path", "EventDetail/Authenticate/Data", "data_name", "k",
                "field", "mask", "transform", "trim", "mask_names", Map.of("names", Map.of("1", "A")))))
                .hasMessageContaining("no value, map, time_format, dictionary, transform or join");
    }

    // --------------------------------------------------------------------------------
    // fill_template

    @Test
    void aTemplateIsFilledInOrderAndRepeatsAPlaceholder() {
        checkFill("User %1 logged on from %2", PLACEHOLDER, List.of("alice", "ws01"), "User alice logged on from ws01",
                List.of());
        checkFill("%1 and %1 again", PLACEHOLDER, List.of("a"), "a and a again", List.of());
        checkFill("%2 before %1", PLACEHOLDER, List.of("a", "b"), "b before a", List.of());
    }

    @Test
    void aPlaceholderWithNoValueIsLeftAsWrittenAndReported() {
        checkFill("%1 then %3", PLACEHOLDER, List.of("a"), "a then %3", List.of("%3"));
        checkFill("%0 is not a value", PLACEHOLDER, List.of("a"), "%0 is not a value", List.of("%0"));
        checkFill("nothing %1", PLACEHOLDER, List.of(), "nothing %1", List.of("%1"));
    }

    @Test
    void aMultiDigitPlaceholderIsOneNumber() {
        final List<String> ten = IntStream.rangeClosed(1, 10).mapToObj(n -> "v" + n).toList();
        checkFill("%10/%1", PLACEHOLDER, ten, "v10/v1", List.of());
        checkFill("%10/%1", PLACEHOLDER, List.of("v1"), "%10/v1", List.of("%10"));
        checkFill("%1%2", PLACEHOLDER, List.of("a", "b"), "ab", List.of());
    }

    @Test
    void anEscapeThePatternMatchesWithoutANumberIsKept() {
        checkFill("100%% of %1", ESCAPED, List.of("disk"), "100%% of disk", List.of());
        checkFill("%%1 stays", ESCAPED, List.of("x"), "%%1 stays", List.of());
    }

    @Test
    void aValueIsWrittenAsItIsNeverFilledAgain() {
        checkFill("%1 %2", PLACEHOLDER, List.of("%2", "b"), "%2 b", List.of());
        checkFill("cost %1", PLACEHOLDER, List.of("$1\\"), "cost $1\\", List.of());
        checkFill("", PLACEHOLDER, List.of("a"), "", List.of());
    }

    @Test
    void aTemplatePatternIsChecked() {
        assertThatThrownBy(() -> entryOf(fillEntry("%\\d+"))).hasMessageContaining("needs a capturing group");
        assertThatThrownBy(() -> entryOf(fillEntry("(\\d*)"))).hasMessageContaining("matches empty text");
        assertThatThrownBy(() -> entryOf(fillEntry("%(\\d+"))).hasMessageContaining("fill_template pattern");
        assertThatThrownBy(() -> entryOf(Map.of("path", "EventDetail/Description", "field", "template",
                "repeat", true, "fill_template", Map.of("pattern", PLACEHOLDER, "field", "args"))))
                .hasMessageContaining("no repeat");
    }

    // --------------------------------------------------------------------------------
    // Whole mappings

    @Test
    void aMappingFillsATemplateAndNamesAMaskRepeatedAndJoined() {
        final Map<String, String> names = new LinkedHashMap<>();
        names.put("0x1", "Read");
        names.put("0x8000000000000000", "Audit");
        final List<Object> fields = new ArrayList<>(LOGON);
        fields.add(Map.of("path", "EventDetail/Description", "field", "template",
                "fill_template", Map.of("pattern", PLACEHOLDER, "xpath", "tokenize(data[@name='args']/@value, ';')")));
        fields.add(Map.of("path", "EventDetail/Authenticate/Data", "data_name", "keyword", "field", "mask",
                "repeat", true, "mask_names", Map.of("names", names)));
        fields.add(Map.of("path", "EventDetail/Authenticate/Data", "data_name", "keywords", "field", "mask",
                "separator", " | ", "mask_names", Map.of("names", names)));
        final XsltGenerator.Generated result = XsltGenerator.generate(mapping(with("events",
                List.of(rule("logon", List.of(equalsCondition("action", "login")), fields), otherRule()))),
                SCHEMA, "4.1.0");
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();
        final List<String> logged = new ArrayList<>();
        final XmlElement events = transform(result.xslt(), record("User %1 on %2 (%3)", "alice;ws01",
                "0x8000000000000005"), Map.of(), logged);
        assertThat(invalid(events, XSD)).as(result.xslt()).isEmpty();
        final XmlElement event = children(events, "Event").getFirst();
        assertThat(text(event, "EventDetail/Description")).isEqualTo("User alice on ws01 (%3)");
        assertThat(data(event, "keyword")).containsExactly("Read", "Audit");
        assertThat(data(event, "keywords")).containsExactly("Read | Audit");
        assertThat(logged).as("the placeholder left unfilled, and the bit no name covers, once each")
                .containsExactly("WARN EventDetail/Description: no value for placeholder(s) %3, record 0",
                        "WARN EventDetail/Authenticate/Data: no name for bit 2 (0x4) of 0x8000000000000005, record 0",
                        "WARN EventDetail/Authenticate/Data: no name for bit 2 (0x4) of 0x8000000000000005, record 0");
        // Read back from its JSON: the entries keep their keys.
        final TranslationMapping again = TranslationMapping.fromJson(mapping(with("events",
                List.of(rule("logon", List.of(equalsCondition("action", "login")), fields), otherRule())))
                .toJson(DumpMode.COMPACT));
        assertThat(XsltGenerator.generate(again, SCHEMA, "4.1.0").xslt()).isEqualTo(result.xslt());
    }

    @Test
    void aCompositeKeyIsMadeAlikeByTheLoaderAndTheLookup() {
        final String context = """
                <Meta>
                  <Entry Source="P1" Id="7" Version="0"><Text>Hello %1</Text><Step>Start</Step>
                    <Flags><Flag Mask="0x1" Name="One"/><Flag Mask="0x8000000000000000" Name="Top"/></Flags>
                  </Entry>
                  <Entry Source="P1" Id="7" Version="1"><Text>Hi %1, version 1</Text><Step>Stop</Step>
                    <Flags><Flag Mask="0x2" Name="Two"/></Flags>
                  </Entry>
                  <Entry Source=" P2 " Id="7" Version="0"><Text>Other</Text><Step>Info</Step><Flags/></Entry>
                </Meta>""";
        final ReferenceMapping reference = ReferenceMapping.fromJson(json("""
                {"input": "xml", "maps": [{"name": "ENTRY_INFO", "key_fields": ["@Source", "@Id", "@Version"],
                  "values": [{"element": "text", "field": "Text"}, {"element": "step", "field": "Step"},
                             {"element": "flags", "xpath": "Flags/Flag", "copy": true}]}]}"""));
        final ReferenceGenerator.Generated loader = ReferenceGenerator.generate(reference);
        assertThat(loader.ok()).as(String.valueOf(loader.problems())).isTrue();
        final XmlElement data = transform(loader.xslt(), context);
        final Map<String, String> values = new LinkedHashMap<>();
        for (final XmlElement found : data.iterElements(ReferenceGenerator.REF, "reference")) {
            final String map = found.childElements(ReferenceGenerator.REF, "map").getFirst().text();
            final String key = found.childElements(ReferenceGenerator.REF, "key").getFirst().text();
            final XmlElement value = found.childElements(ReferenceGenerator.REF, "value").getFirst();
            values.put(map + "\n" + key, XmlSerialiser.toXml(value.childElements().getFirst()));
        }
        assertThat(values.keySet()).as("the loader's keys, as the Java key is made")
                .containsExactly("ENTRY_INFO\n" + LookupKeys.key(List.of("P1", "7", "0")),
                        "ENTRY_INFO\n" + LookupKeys.key(List.of("P1", "7", "1")),
                        "ENTRY_INFO\n" + LookupKeys.key(List.of("P2", "7", "0")));
        final String keySelect = XmlParser.parse(loader.xslt()).iterElements(ReferenceGenerator.REF, "key")
                .getFirst().childElements().getFirst().get("select");
        assertThat(LookupKeys.partCount(keySelect)).isEqualTo(3);

        final Map<String, Object> key = Map.of("map", "ENTRY_INFO", "key_fields", List.of("src", "id", "ver"));
        final List<Object> fields = new ArrayList<>(LOGON);
        fields.add(Map.of("path", "EventDetail/Description", "lookup", keyed(key, "path", "text"),
                "fill_template", Map.of("pattern", PLACEHOLDER, "xpath", "tokenize(data[@name='args']/@value, ';')")));
        fields.add(Map.of("path", "EventDetail/Authenticate/Data", "data_name", "step",
                "lookup", keyed(key, "path", "step")));
        fields.add(Map.of("path", "EventDetail/Authenticate/Data", "data_name", "flag", "field", "mask",
                "repeat", true, "mask_names", Map.of("lookup", keyed(key, "path", "flags/Flag"), "mask", "@Mask",
                        "name", "@Name")));
        final XsltGenerator.Generated events = XsltGenerator.generate(mapping(with("events",
                List.of(rule("logon", List.of(equalsCondition("action", "login")), fields), otherRule()))),
                SCHEMA, "4.1.0");
        assertThat(events.ok()).as(String.valueOf(events.problems())).isTrue();
        assertThat(events.xslt()).contains("codepoints-to-string(9247)");

        final XmlElement v0 = lookedUp(events.xslt(), values, "P1", "7", "0", "bob", "0x8000000000000001");
        assertThat(text(v0, "EventDetail/Description")).isEqualTo("Hello bob");
        assertThat(data(v0, "step")).containsExactly("Start");
        assertThat(data(v0, "flag")).containsExactly("One", "Top");
        final XmlElement v1 = lookedUp(events.xslt(), values, "P1", "7", "1", "bob", "0x2");
        assertThat(text(v1, "EventDetail/Description")).as("the version tells the entries apart")
                .isEqualTo("Hi bob, version 1");
        assertThat(data(v1, "flag")).containsExactly("Two");
        final XmlElement spaced = lookedUp(events.xslt(), values, " P2", "7 ", "0", "bob", "0");
        assertThat(data(spaced, "step")).as("each value stripped, on both sides").containsExactly("Info");
        final XmlElement missing = lookedUp(events.xslt(), values, "P9", "7", "0", "bob", "0x1");
        assertThat(text(missing, "EventDetail/Description")).isNull();
        assertThat(data(missing, "flag")).isEmpty();
    }

    @Test
    void aLookupKeyIsOneOfFieldXpathOrKeyFields() {
        assertThatThrownBy(() -> entryOf(Map.of("path", "EventSource/User/Name", "lookup", Map.of("map", "M",
                "field", "a", "key_fields", List.of("a", "b"))))).hasMessageContaining("exactly one of field, xpath "
                                                                                       + "or key_fields");
        assertThatThrownBy(() -> entryOf(Map.of("path", "EventSource/User/Name", "lookup", Map.of("map", "M",
                "key_fields", List.of("a", "b", "c", "d"))))).hasMessageContaining("1 to 3");
        assertThatThrownBy(() -> ReferenceMapping.fromJson(json("""
                {"input": "xml", "maps": [{"name": "M", "key": "a", "key_fields": ["a"],
                  "values": [{"field": "b"}]}]}"""))).hasMessageContaining("exactly one of key, key_xpath or "
                                                                          + "key_fields");
        assertThat(LookupKeys.partCount("data[@name='a']/@value")).isEqualTo(1);
        assertThat(LookupKeys.keyText(List.of("a"))).isEqualTo(XPathText.keyText("a"));
        assertThat(LookupKeys.key(List.of(" a "))).as("one field is a plain key, as it is").isEqualTo(" a ");
        assertThat(LookupKeys.key(List.of(" a  b ", "c"))).isEqualTo("a b" + LookupKeys.SEPARATOR + "c");
    }

    // --------------------------------------------------------------------------------

    private static Map<String, Object> fillEntry(final String pattern) {
        return Map.of("path", "EventDetail/Description", "field", "template",
                "fill_template", Map.of("pattern", pattern, "field", "args"));
    }

    private static Map<String, Object> keyed(final Map<String, Object> lookup, final String key, final Object value) {
        final Map<String, Object> out = new LinkedHashMap<>(lookup);
        out.put(key, value);
        return out;
    }

    private static FieldMapping entryOf(final Map<String, Object> json) {
        return FieldMapping.fromJson(json);
    }

    private static Map.Entry<String, String> entry(final String mask, final String name) {
        return new AbstractMap.SimpleImmutableEntry<>(mask, name);
    }

    private static List<String> maskNames(final String value) {
        return maskNames(value, TABLE);
    }

    private static List<String> maskNames(final String value, final List<Map.Entry<String, String>> table) {
        final RenderXslt render = new RenderXslt(name -> name);
        return evaluate(render.namesText(XPathText.literal(value), masks(table), labels(table)));
    }

    private static String maskProblem(final String value, final List<Map.Entry<String, String>> table) {
        final RenderXslt render = new RenderXslt(name -> name);
        final List<String> found = evaluate(render.problemText(XPathText.literal(value), masks(table),
                labels(table)));
        return found.isEmpty()
                ? null
                : found.getFirst();
    }

    private static String masks(final List<Map.Entry<String, String>> table) {
        return "(" + table.stream().map(e -> XPathText.literal(e.getKey())).collect(Collectors.joining(", ")) + ")";
    }

    private static String labels(final List<Map.Entry<String, String>> table) {
        return "(" + table.stream().map(e -> XPathText.literal(e.getValue())).collect(Collectors.joining(", "))
               + ")";
    }

    // The template filled by the XSLT and by the preview alike, with the same placeholders left unfilled.
    private static void checkFill(final String template,
                                  final String pattern,
                                  final List<String> values,
                                  final String filled,
                                  final List<String> unfilled) {
        final RenderXslt render = new RenderXslt(name -> name);
        final String given = "(" + values.stream().map(XPathText::literal).collect(Collectors.joining(", ")) + ")";
        assertThat(evaluate(render.fillText(XPathText.literal(template), pattern, given)))
                .as("the XSLT fills %s", template).containsExactly(filled);
        assertThat(evaluate(render.unfilledText(XPathText.literal(template), pattern, given)))
                .as("the XSLT's unfilled placeholders").isEqualTo(unfilled);
        final FillTemplate.Filled preview = FillTemplate.fill(template, pattern, values);
        assertThat(preview.text()).as("the preview fills %s", template).isEqualTo(filled);
        assertThat(preview.unfilled()).isEqualTo(unfilled);
    }

    // The items an expression gives, each as text, with the render functions in scope.
    private static List<String> evaluate(final String expression) {
        final Map<String, String> namespaces = new LinkedHashMap<>();
        namespaces.put("xsl", XSL);
        namespaces.put("xs", MappingFacts.XS);
        namespaces.put("mcp", MappingFacts.MCP_NS);
        final XmlElement sheet = XmlElement.create(XSL, "stylesheet", namespaces);
        sheet.set("version", "3.0");
        final XmlElement template = sheet.addElement(XSL, "template", "match", "/");
        template.addElement(null, "out").addElement(XSL, "for-each", "select", expression)
                .addElement(null, "v").addElement(XSL, "value-of", "select", ".");
        new RenderXslt(name -> name).functions(true, true).forEach(sheet::append);
        final XmlElement out = transform(XmlSerialiser.toPrettyDocument(sheet), "<in/>");
        return out.childElements().stream().map(v -> v.text() == null
                ? ""
                : v.text()).toList();
    }

    private static XmlElement lookedUp(final String xslt,
                                       final Map<String, String> values,
                                       final String src,
                                       final String id,
                                       final String ver,
                                       final String args,
                                       final String mask) {
        final String record = "<records xmlns=\"records:2\"><record><data name=\"time\" value=\"2026-09-28T10:00:00"
                              + ".000Z\"/><data name=\"action\" value=\"login\"/><data name=\"user\" value=\"dave\"/>"
                              + "<data name=\"src\" value=\"" + src + "\"/><data name=\"id\" value=\"" + id + "\"/>"
                              + "<data name=\"ver\" value=\"" + ver + "\"/><data name=\"args\" value=\"" + args
                              + "\"/><data name=\"mask\" value=\"" + mask + "\"/></record></records>";
        final XmlElement events = transform(xslt, record, Map.of(), null, Map.of(),
                List.of(new LookupStub(values)));
        assertThat(invalid(events, XSD)).as(xslt).isEmpty();
        return children(events, "Event").getFirst();
    }

    private static String record(final String template, final String args, final String mask) {
        return "<records xmlns=\"records:2\"><record><data name=\"time\" value=\"2026-09-28T10:00:00.000Z\"/>"
               + "<data name=\"action\" value=\"login\"/><data name=\"user\" value=\"dave\"/>"
               + "<data name=\"template\" value=\"" + template + "\"/><data name=\"args\" value=\"" + args + "\"/>"
               + "<data name=\"mask\" value=\"" + mask + "\"/></record></records>";
    }

    private static List<String> data(final XmlElement event, final String name) {
        return having(event, EVT, "Data", "Name", name).stream().map(found -> found.get("Value")).toList();
    }
}
