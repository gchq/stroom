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

        final List<String[]> xslt = classify(spec, rows);

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
                .containsEntry("not-string", null).containsEntry("duplicate", "com.x.Last");
        // A key after white space inside an earlier quoted value is found first, by both (a known limit, the same
        // in processing); one right after the quote isn't.
        assertThat(byId).containsEntry("quoted-pair", "fake\"");
        assertThat(SourceTypeReading.of("event>payload>inner>@n").valueOf(byIdRecord(parsed, "nested-3")))
                .isEqualTo("com.x.Deep");
        assertThat(SourceTypeReading.of("event>#format").valueOf(byIdRecord(parsed, "broken")))
                .isEqualTo(HeldText.TEXT);
        assertThat(SourceTypeFields.valueOf(byIdRecord(parsed, "syslog"), "event>app")).isEqualTo("sshd");
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
    private static List<String[]> classify(final String spec, final List<SourceTypeModule.Row> rows) {
        final TranslationMapping common = commonMapping();
        final XsltGenerator.Generated commonXslt = XsltGenerator.generate(common, SCHEMA, "4.1.0");
        assertThat(commonXslt.problems()).isEmpty();
        final List<String> types = rows.stream().map(SourceTypeModule.Row::name).toList();
        final XsltGenerator.Generated group = XsltGenerator.generate(groupOf(spec, types, rows, common), SCHEMA,
                "4.1.0");
        assertThat(group.problems()).as("problems: %s", group.problems()).isEmpty();
        final String type = found(FUNCTION, group.xslt());
        final String value = found(VALUE_FUNCTION, group.xslt());
        final String code = group.xslt().replace("</xsl:stylesheet>",
                "<xsl:template match=\"/\" priority=\"100\"><types><xsl:for-each select=\"*/*\"><t value=\"{"
                + value + "(.)}\" type=\"{" + type + "(.)}\"/></xsl:for-each></types></xsl:template>"
                + "</xsl:stylesheet>");
        final Map<String, String> imports = new LinkedHashMap<>();
        imports.put(COMMON_HREF, commonXslt.xslt());
        imports.put(HELPERS_HREF, HelpersXslt.generate());
        final XmlElement out = transform(code, records(), imports, new ArrayList<>());
        return children(out, "t").stream().map(t -> new String[]{t.get("value"), t.get("type")}).toList();
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
    private static String records() {
        final StringBuilder out = new StringBuilder("<map xmlns=\"http://www.w3.org/2013/XSL/json\">");
        LINES.forEach(line -> out.append(asParsed(null, ToolJson.loads(line))));
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
    private static Map<String, Object> parsed(final String line) {
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
