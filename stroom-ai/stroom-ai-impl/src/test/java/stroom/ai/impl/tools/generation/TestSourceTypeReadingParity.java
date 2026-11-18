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

import stroom.ai.impl.tools.AwkwardValues;
import stroom.ai.impl.tools.sample.HeldText;
import stroom.ai.impl.tools.sample.SampleProfiler;
import stroom.ai.impl.tools.sample.SourceTypeFields;
import stroom.ai.impl.tools.sample.SourceTypeReading;
import stroom.ai.impl.tools.sample.SourceTypeRows;
import stroom.ai.impl.tools.sample.ToolJson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/// Parity of a source type's reading into held values between the tools and a source type group's generated XSLT:
/// the same sample records, classified by the tools ([SourceTypeReading], the registry's rows in order) and by the
/// group's XSLT run through Saxon, land in the same source type each, and read the same value: held JSON (encoded
/// once and twice), held XML (namespaces, attributes, the root's name), held key=value pairs, a syslog header,
/// held values inside held values two and three deep, plain text and a missing or malformed held field (no value:
/// the `(no value)` row), and the readings tried in turn.
class TestSourceTypeReadingParity extends XsltRuns {

    private static final String COMMON_HREF = "uuid='c0000000-0000-0000-0000-000000000001', name='Held common'";
    private static final String HELPERS_HREF = "uuid='c0000000-0000-0000-0000-000000000002', name='Held helpers'";
    private static final Pattern FUNCTION = Pattern.compile("name=\"(mcp:[\\w-]*source[_-]type)\"");
    private static final Pattern VALUE_FUNCTION = Pattern.compile("name=\"(mcp:[\\w-]*source[_-]type[_-]value)\"");

    // The sample: a wrapper whose `event` holds each record's data in a format of its own.
    private static final List<String> LINES = List.of(
            line("json", "{\"logger_name\": \"com.x.Auth\", \"level\": \"INFO\"}"),
            line("kv", "logger_name=com.x.Db level=DEBUG msg=\"select 1\""),
            line("xml", "<Log logger=\"com.x.Xml\"><logger_name>com.x.Auth</logger_name><When>now</When></Log>"),
            line("xml-ns", "<?xml version=\"1.0\"?><ns:Log xmlns:ns=\"urn:x\" ns:logger=\"com.x.Ns\">"
                           + "<ns:logger_name> com.x.Ns </ns:logger_name></ns:Log>"),
            line("twice", "\"{\\\"logger_name\\\": \\\"com.x.Twice\\\"}\""),
            line("text", "User bob logged in from somewhere"),
            line("one-pair", "failed logger_name=com.x.Text"),
            line("nested-2", "{\"payload\": \"logger_name=com.x.Nested level=WARN\"}"),
            line("nested-3", "{\"payload\": \"{\\\"inner\\\": \\\"<L n=\\\\\\\"com.x.Deep\\\\\\\"/>\\\"}\"}"),
            "{\"id\": \"missing\", \"timestamp\": \"2026-11-18T10:00:00.000Z\", \"hostname\": \"app01\"}",
            line("broken", "{\"logger_name\": \"com.x.Broken\""),
            line("syslog", "<34>Oct 11 22:14:15 host1 sshd[123]: Accepted logger_name=x level=y"),
            line("syslog-5424", "<165>1 2026-11-18T10:00:00Z host2 auditd 77 ID47 - logger_name=com.x.Sys"),
            line("nil", "{\"logger_name\": \"N/A\"}"),
            line("padded", "{\"logger_name\": \"  com.x.Pad \\t\"}"),
            line("number", "{\"logger_name\": 42}"),
            line("quoted", "logger_name=\"com.x.Quoted\" level=1"),
            line("twice-key", "logger_name=first level=1 logger_name=second"),
            line("quoted-pair", "msg=\"a logger_name=fake\" logger_name=real"),
            line("array", "[1, 2]"),
            line("object", "{\"logger_name\": {\"nested\": \"x\"}}"),
            line("deep", "{\"a\": {\"b\": {\"c\": {\"d\": {\"e\": \"com.x.Five\"}}}}}"),
            line("empty", ""),
            line("duplicate", "{\"logger_name\": \"com.x.First\", \"logger_name\": \"com.x.Last\"}"),
            // Found in review (2026-11-18): numbers as written, NaN and Infinity no JSON, a DOCTYPE no XML.
            line("decimal", "{\"logger_name\": 2.10}"),
            line("exponent", "{\"logger_name\": 1e10}"),
            line("exponent-signed", "{\"logger_name\": -1.5E+3}"),
            line("negative", "{\"logger_name\": -7}"),
            line("big", "{\"logger_name\": 12345678901234567890}"),
            line("point-zero", "{\"logger_name\": 1.0}"),
            line("nan", "{\"logger_name\": NaN, \"level\": \"x\"}"),
            line("infinity", "{\"logger_name\": -Infinity}"),
            line("doctype", "<!DOCTYPE Log><Log logger=\"com.x.Dtd\"><logger_name>com.x.Dtd</logger_name></Log>"),
            line("doctype-entity", "<!DOCTYPE Log [<!ENTITY e \"com.x.Entity\">]><Log logger=\"&e;\">"
                                   + "<logger_name>&e;</logger_name></Log>"),
            line("doctype-system", "<!DOCTYPE Log SYSTEM \"log.dtd\"><Log logger=\"com.x.Sys\"/>"),
            "{\"id\": \"not-string\", \"timestamp\": \"2026-11-18T10:00:00.000Z\", \"hostname\": \"app01\", "
            + "\"event\": {\"logger_name\": \"com.x.Object\"}}");

    @ParameterizedTest
    @ValueSource(strings = {"event>logger_name", "event>logger_name | event>xml:@logger", "event>xml:#root",
            "event>#format", "event>payload>logger_name", "event>payload>inner>@n", "event>syslog:app",
            "event>app | event>host", "event>kv:logger_name", "event>json:logger_name", "event>a.b.c.d.e",
            "event>message>logger_name", "event>xml:logger_name", "event>Log.@logger"})
    void aGroupReadsEachRecordsSourceTypeAsTheToolsDo(final String spec) {
        final SourceTypeReading reading = SourceTypeReading.of(spec);
        final List<Map<String, Object>> parsed = LINES.stream().map(TestSourceTypeReadingParity::parsed).toList();
        final List<String> tools = parsed.stream().map(reading::valueOf).toList();
        // A row a value seen, and the (no value) row last, as record_source_types proposes them.
        final List<SourceTypeModule.Row> rows = new ArrayList<>();
        new LinkedHashSet<>(tools).stream().filter(value -> value != null)
                .forEach(value -> rows.add(new SourceTypeModule.Row("type " + value, SourceTypeRows.VALUE, value)));
        rows.add(SourceTypeModule.Row.of(SourceTypeRows.noValue()));
        final List<SourceTypeRows.Row> toolRows = rows.stream().map(SourceTypeModule.Row::toolRow).toList();

        final List<String[]> xslt = classify(spec, rows, LINES);

        assertThat(xslt).hasSize(LINES.size());
        for (int i = 0; i < LINES.size(); i++) {
            final String expectedValue = tools.get(i) == null
                    ? ""
                    : tools.get(i);
            final String expectedType = SourceTypeRows.first(toolRows, tools.get(i)).map(SourceTypeRows.Row::name)
                    .orElse("");
            assertThat(xslt.get(i)).as("record %s by %s", parsed.get(i).get("id"), spec)
                    .containsExactly(expectedValue, expectedType);
        }
        // Every record lands somewhere: a value's row, or (no value).
        assertThat(xslt).allSatisfy(each -> assertThat(each[1]).isNotEmpty());
    }

    @Test
    void theToolsReadTheSampleAsDecided() {
        final List<Map<String, Object>> parsed = LINES.stream().map(TestSourceTypeReadingParity::parsed).toList();
        final Map<String, String> byId = new LinkedHashMap<>();
        final SourceTypeReading reading = SourceTypeReading.of("event>logger_name | event>xml:@logger");
        parsed.forEach(record -> byId.put(String.valueOf(record.get("id")), reading.valueOf(record)));

        assertThat(byId).containsEntry("json", "com.x.Auth").containsEntry("kv", "com.x.Db")
                .containsEntry("xml", "com.x.Auth").containsEntry("xml-ns", "com.x.Ns")
                .containsEntry("twice", "com.x.Twice").containsEntry("text", null).containsEntry("one-pair", null)
                .containsEntry("missing", null).containsEntry("broken", null).containsEntry("nil", null)
                .containsEntry("padded", "com.x.Pad").containsEntry("number", "42")
                .containsEntry("quoted", "com.x.Quoted").containsEntry("twice-key", "first")
                .containsEntry("array", null).containsEntry("object", null).containsEntry("empty", null)
                .containsEntry("not-string", null).containsEntry("duplicate", "com.x.Last")
                .containsEntry("decimal", "2.10").containsEntry("exponent", "1e10")
                .containsEntry("exponent-signed", "-1.5E+3").containsEntry("negative", "-7")
                .containsEntry("big", "12345678901234567890").containsEntry("point-zero", "1.0")
                .containsEntry("nan", null).containsEntry("infinity", null).containsEntry("doctype", null)
                .containsEntry("doctype-entity", null).containsEntry("doctype-system", null);
        // A key after white space inside an earlier quoted value is found first, by both (a known limit, the same
        // in processing); one right after the quote isn't.
        assertThat(byId).containsEntry("quoted-pair", "fake\"");
        assertThat(SourceTypeReading.of("event>payload>inner>@n").valueOf(byIdRecord(parsed, "nested-3")))
                .isEqualTo("com.x.Deep");
        assertThat(SourceTypeReading.of("event>#format").valueOf(byIdRecord(parsed, "broken")))
                .isEqualTo(HeldText.TEXT);
        assertThat(SourceTypeFields.valueOf(byIdRecord(parsed, "syslog"), "event>app")).isEqualTo("sshd");
    }

    // Awkward values (2026-11-18, after chat 181's form feed): held JSON, XML and key=value whose source type and
    // other values hold control characters, line ends, astral and right-to-left text, XML's specials and quotes; and
    // a held field longer than the 10,000 characters Stroom's jsonParser keeps of a string. The records are given to
    // the group's XSLT as Stroom's jsonParser gives them: XML 1.1, the characters only it holds as references.
    @ParameterizedTest
    @ValueSource(strings = {"event>logger_name", "event>logger_name | event>xml:@logger", "event>kv:logger_name",
            "event>#format", "event>xml:#root", "event>msg>logger_name"})
    void aGroupReadsAwkwardHeldValuesAsTheToolsDo(final String spec) {
        final List<String> lines = awkwardLines();
        final SourceTypeReading reading = SourceTypeReading.of(spec);
        // The tools read each record as Stroom's jsonParser leaves it: a string longer than it keeps, cut. A Stroom
        // limit, not the tools': they read the sample whole, so a held field past the cut reads differently in the
        // group's pipeline (asserted below), whose processing warns of the cut ('String value truncated', which
        // step_sample and wait_for_processing give for review, with stringTruncateLength to raise).
        final List<String> tools = lines.stream().map(TestSourceTypeReadingParity::cutAsStroomParses)
                .map(reading::valueOf).toList();
        final int longAt = lines.size() - 1;
        if (List.of("event>logger_name", "event>logger_name | event>xml:@logger", "event>#format").contains(spec)) {
            assertThat(reading.valueOf(parsed(lines.get(longAt)))).as("the long record, read whole")
                    .isNotEqualTo(tools.get(longAt));
        }
        final List<SourceTypeModule.Row> rows = new ArrayList<>();
        new LinkedHashSet<>(tools).stream().filter(value -> value != null)
                .forEach(value -> rows.add(new SourceTypeModule.Row("type " + rows.size(), SourceTypeRows.VALUE,
                        value)));
        rows.add(SourceTypeModule.Row.of(SourceTypeRows.noValue()));
        final List<SourceTypeRows.Row> toolRows = rows.stream().map(SourceTypeModule.Row::toolRow).toList();

        final List<String[]> xslt = classify(spec, rows, stroomRecords(lines), true);

        assertThat(xslt).hasSize(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            final String expectedValue = tools.get(i) == null
                    ? ""
                    : tools.get(i);
            final String expectedType = SourceTypeRows.first(toolRows, tools.get(i)).map(SourceTypeRows.Row::name)
                    .orElse("");
            assertThat(xslt.get(i)).as("record %s by %s", parsed(lines.get(i)).get("id"), spec)
                    .containsExactly(expectedValue, expectedType);
        }
    }

    @Test
    void theToolsReadAwkwardHeldValuesWhole() {
        final SourceTypeReading reading = SourceTypeReading.of("event>logger_name | event>xml:@logger");
        final Map<String, String> byId = new LinkedHashMap<>();
        awkwardLines().stream().map(TestSourceTypeReadingParity::parsed)
                .forEach(record -> byId.put(String.valueOf(record.get("id")), reading.valueOf(record)));
        for (final AwkwardValues.Value value : AwkwardValues.ALL) {
            // Read whole, but for the control characters (which no source type group's XSLT can hold) and the white
            // space around it.
            final String read = value.text().replaceAll("[\\p{Cc}&&[^\\t\\n\\r]]", "").strip();
            assertThat(byId).as(value.why()).containsEntry("json-" + value.name(), read);
            // Held XML holding a C0 control isn't XML (XML 1.0 can't write one): no value, as in processing.
            final boolean c0 = value.text().chars().anyMatch(c -> c < 0x20 && c != '\t' && c != '\n' && c != '\r');
            assertThat(byId).as(value.why()).containsEntry("xml-" + value.name(), c0
                    ? null
                    : read);
        }
        assertThat(byId).containsEntry("long", "com.x.Long");
    }

    @Test
    void aGroupWhoseReadingIsNoneIsRefusedSayingWhy() {
        final TranslationMapping common = commonMapping();
        final List<SourceTypeModule.Row> rows = List.of(new SourceTypeModule.Row("a", SourceTypeRows.VALUE, "a"));

        final XsltGenerator.Generated group = XsltGenerator.generate(groupOf("event>a>b>c>d", List.of("a"), rows,
                common), SCHEMA, "4.1.0");

        assertThat(group.problems()).anySatisfy(problem -> assertThat(problem).contains("isn't one",
                "at most 3"));
        assertThat(group.xslt()).isNull();
    }

    // The group's XSLT run over the sample: each record's value and source type, as its functions read them.
    static List<String[]> classify(final String spec,
                                   final List<SourceTypeModule.Row> rows,
                                   final List<String> lines) {
        return classify(spec, rows, records(lines), false);
    }

    // As classify, over records given as XML; by code points, each value written as its characters' numbers (so
    // a control character, which the XML Saxon writes can't hold, comes back as it was).
    static List<String[]> classify(final String spec,
                                   final List<SourceTypeModule.Row> rows,
                                   final String records,
                                   final boolean byCodePoints) {
        final TranslationMapping common = commonMapping();
        final XsltGenerator.Generated commonXslt = XsltGenerator.generate(common, SCHEMA, "4.1.0");
        assertThat(commonXslt.problems()).isEmpty();
        final List<String> types = rows.stream().map(SourceTypeModule.Row::name).toList();
        final XsltGenerator.Generated group = XsltGenerator.generate(groupOf(spec, types, rows, common), SCHEMA,
                "4.1.0");
        assertThat(group.problems()).as("problems: %s", group.problems()).isEmpty();
        final String type = found(FUNCTION, group.xslt());
        final String value = found(VALUE_FUNCTION, group.xslt());
        final String valueOf = byCodePoints
                ? "string-join(for $c in string-to-codepoints(" + value + "(.)) return string($c), ' ')"
                : value + "(.)";
        final String code = group.xslt().replace("</xsl:stylesheet>",
                "<xsl:template match=\"/\" priority=\"100\"><types><xsl:for-each select=\"*/*\"><t value=\"{"
                + valueOf + "}\" type=\"{" + type + "(.)}\"/></xsl:for-each></types></xsl:template>"
                + "</xsl:stylesheet>");
        final Map<String, String> imports = new LinkedHashMap<>();
        imports.put(COMMON_HREF, commonXslt.xslt());
        imports.put(HELPERS_HREF, HelpersXslt.generate());
        final XmlElement out = transform(code, records, imports, new ArrayList<>());
        return children(out, "t").stream().map(t -> new String[]{byCodePoints
                ? fromCodePoints(t.get("value"))
                : t.get("value"), t.get("type")}).toList();
    }

    private static String fromCodePoints(final String numbers) {
        final StringBuilder out = new StringBuilder();
        for (final String number : numbers.split(" ")) {
            if (!number.isEmpty()) {
                out.appendCodePoint(Integer.parseInt(number));
            }
        }
        return out.toString();
    }

    // Wrapper records whose `event` holds each awkward value: as held JSON (its source type and a message), as an
    // attribute of held XML, and as a quoted key=value pair where the value holds no quote; then a held JSON
    // message longer than Stroom's jsonParser keeps of a string.
    static List<String> awkwardLines() {
        final List<String> lines = new ArrayList<>();
        for (final AwkwardValues.Value value : AwkwardValues.ALL) {
            lines.add(line("json-" + value.name(), ToolJson.dumps(Map.of("logger_name", value.text(),
                    "msg", ToolJson.dumps(Map.of("logger_name", value.text()))))));
            lines.add(line("xml-" + value.name(), "<Log logger=\"" + heldXml(value.text())
                                                   + "\"><When>now</When></Log>"));
            if (value.text().indexOf('"') < 0 && value.text().indexOf('\\') < 0) {
                lines.add(line("kv-" + value.name(), "logger_name=\"" + value.text() + "\" level=1"));
            }
        }
        lines.add(line("long", "{\"logger_name\": \"com.x.Long\", \"msg\": \""
                               + "L".repeat(AwkwardValues.JSON_PARSER_CUT + 50) + "\"}"));
        return lines;
    }

    // A record as Stroom's jsonParser leaves it, flattened: each string longer than it keeps, cut.
    private static Map<String, Object> cutAsStroomParses(final String line) {
        return SampleProfiler.flatten(cut(ToolJson.loads(line)));
    }

    @SuppressWarnings("unchecked")
    private static Object cut(final Object value) {
        if (value instanceof final Map<?, ?> map) {
            final Map<String, Object> out = new LinkedHashMap<>();
            ((Map<String, Object>) map).forEach((key, inner) -> out.put(key, cut(inner)));
            return out;
        }
        if (value instanceof final List<?> list) {
            return list.stream().map(TestSourceTypeReadingParity::cut).toList();
        }
        return value instanceof final String text && text.length() > AwkwardValues.JSON_PARSER_CUT
                ? text.substring(0, AwkwardValues.JSON_PARSER_CUT)
                : value;
    }

    // An attribute's value as a source writes held XML (no declaration, so XML 1.0): the specials and the white
    // space an attribute would normalise as references; a C0 control raw, which makes it not XML (no XML 1.0
    // reference can write one).
    private static String heldXml(final String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replace("\t", "&#x9;").replace("\n", "&#xa;").replace("\r", "&#xd;");
    }

    // The sample as Stroom's jsonParser gives it: XML 1.1, the characters only it holds as character references,
    // and each string cut to the jsonParser's default length.
    static String stroomRecords(final List<String> lines) {
        final StringBuilder out = new StringBuilder("<?xml version=\"1.1\"?><map xmlns=\"http://www.w3.org/2013/XSL/json\">");
        lines.forEach(line -> out.append(asStroomParses(null, ToolJson.loads(line))));
        return out.append("</map>").toString();
    }

    @SuppressWarnings("unchecked")
    private static String asStroomParses(final String key, final Object value) {
        final String keyed = key == null
                ? ""
                : " key=\"" + AwkwardValues.xml11Text(key) + "\"";
        if (value instanceof final Map<?, ?> map) {
            final StringBuilder out = new StringBuilder("<map" + keyed + ">");
            ((Map<String, Object>) map).forEach((name, inner) -> out.append(asStroomParses(name, inner)));
            return out.append("</map>").toString();
        }
        if (value instanceof final String text) {
            final String cut = text.length() > AwkwardValues.JSON_PARSER_CUT
                    ? text.substring(0, AwkwardValues.JSON_PARSER_CUT)
                    : text;
            return "<string" + keyed + ">" + AwkwardValues.xml11Text(cut) + "</string>";
        }
        return asParsed(key, value);
    }

    private static String found(final Pattern pattern, final String xslt) {
        final Matcher matcher = pattern.matcher(xslt);
        assertThat(matcher.find()).as("%s in the group's XSLT", pattern).isTrue();
        return matcher.group(1);
    }

    private static TranslationMapping commonMapping() {
        return TranslationMapping.fromJson(json("""
                {"input": "json", "json_layout": "lines",
                 "common": [{"path": "EventTime/TimeCreated", "field": "timestamp"},
                            {"path": "EventSource/System/Name", "value": "Held"},
                            {"path": "EventSource/Device/HostName", "field": "hostname"},
                            {"path": "EventSource/System/Environment", "value": "Prod"}],
                 "events": [{"name": "common"}],
                 "module": {"kind": "common", "name": "Held", "mode": "held_common",
                            "helpers": "%s"}}""".formatted(HELPERS_HREF)));
    }

    private static TranslationMapping groupOf(final String field,
                                              final List<String> sourceTypes,
                                              final List<SourceTypeModule.Row> rows,
                                              final TranslationMapping common) {
        final TranslationMapping mapping = TranslationMapping.fromJson(json("""
                {"input": "json", "json_layout": "lines",
                 "common": [{"path": "EventSource/Generator", "value": "Held"}],
                 "events": [{"name": "line", "allow_unknown": "a test",
                             "fields": [{"path": "EventDetail/TypeId", "field": "id"},
                                        {"path": "EventDetail/Unknown/Data", "data_name": "event",
                                         "field": "event"}]}]}"""));
        return mapping.withModule(new SourceTypeModule(SourceTypeModule.GROUP, "Held all", "held_all", sourceTypes,
                COMMON_HREF, null, SourceTypeModule.commonParts(common), null, field, rows));
    }

    // The sample as Stroom's JSON parser gives it: its root map around the records.
    static String records(final List<String> lines) {
        final StringBuilder out = new StringBuilder("<map xmlns=\"http://www.w3.org/2013/XSL/json\">");
        lines.forEach(line -> out.append(asParsed(null, ToolJson.loads(line))));
        return out.append("</map>").toString();
    }

    // A JSON value as Stroom's JSON parser writes it.
    @SuppressWarnings("unchecked")
    private static String asParsed(final String key, final Object value) {
        final String keyed = key == null
                ? ""
                : " key=\"" + escape(key).replace("\"", "&quot;") + "\"";
        if (value instanceof final Map<?, ?> map) {
            final StringBuilder out = new StringBuilder("<map" + keyed + ">");
            ((Map<String, Object>) map).forEach((name, inner) -> out.append(asParsed(name, inner)));
            return out.append("</map>").toString();
        }
        if (value instanceof final List<?> list) {
            final StringBuilder out = new StringBuilder("<array" + keyed + ">");
            list.forEach(inner -> out.append(asParsed(null, inner)));
            return out.append("</array>").toString();
        }
        if (value == null) {
            return "<null" + keyed + "/>";
        }
        final String element = value instanceof String
                ? "string"
                : value instanceof Boolean
                        ? "boolean"
                        : "number";
        return "<" + element + keyed + ">" + escape(String.valueOf(value)) + "</" + element + ">";
    }

    private static String escape(final String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // A record as the survey parses a JSON line: flattened to dotted names.
    static Map<String, Object> parsed(final String line) {
        return SampleProfiler.flatten(ToolJson.loads(line));
    }

    private static Map<String, Object> byIdRecord(final List<Map<String, Object>> parsed, final String id) {
        return parsed.stream().filter(record -> id.equals(record.get("id"))).findFirst().orElseThrow();
    }

    // A wrapper record whose `event` holds the text given.
    private static String line(final String id, final String event) {
        return "{\"id\": \"" + id + "\", \"timestamp\": \"2026-11-18T10:00:00.000Z\", \"hostname\": \"app01\", "
               + "\"system\": \"Teams\", \"event\": " + ToolJson.dumps(event) + "}";
    }
}
