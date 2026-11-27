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

import stroom.ai.impl.tools.validation.XsltVersionHistory;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Reading a `max_length` cap back (`rebuild_mapping`): an entry cut to its length (`substring(x, 1, N)`) is read
/// back as the entry with its `max_length`, not kept as an xpath; plain, transformed (`clean` too), with a default,
/// repeated, and a group's item.
class TestXsltReaderMaxLength extends XsltRuns {

    private static final String RECORDS = """
            <records xmlns="records:2">
            <record><data name="time" value="2026-09-28T10:00:00.000Z"/><data name="sender" value="jane.doe"/>\
            <data name="host" value="mailhost01"/><data name="body" value="A long message body"/>\
            <data name="tag" value="urgent"/><data name="tag" value="external"/>\
            <data name="recipients" value="john@mail.com,jack@gmail.com"/></record>
            <record><data name="time" value="2026-09-28T10:01:00.000Z"/><data name="sender" value="jill"/></record>
            </records>""";

    @Test
    void aPlainEntryCutToItsLengthIsReadBackWithItsMaxLength() {
        assertRoundTrip(sendMapping(entry("""
                {"path": "EventDetail/Send/Source/User/Id", "field": "sender", "max_length": 4}"""), entry("""
                {"path": "EventDetail/Send/Data", "data_name": "body", "field": "body", "max_length": 6}""")));
    }

    @Test
    void aTransformedEntryCutToItsLengthIsReadBackWithItsTransformAndMaxLength() {
        assertRoundTrip(sendMapping(entry("""
                {"path": "EventDetail/Send/Source/User/Id", "field": "sender", "transform": "upper",
                 "max_length": 4}"""), entry("""
                {"path": "EventDetail/Send/Source/Device/HostName", "field": "host", "transform": "lower",
                 "max_length": 4, "default": "none"}"""), entry("""
                {"path": "EventDetail/Send/Data", "data_name": "host", "field": "host", "transform": "clean",
                 "max_length": 5}"""), entry("""
                {"path": "EventDetail/Send/Data", "data_name": "sender", "field": "sender", "transform": "clean"}""")));
    }

    @Test
    void aRepeatedEntryCutToItsLengthIsReadBackRepeatedWithItsTransformAndMaxLength() {
        assertRoundTrip(sendMapping(entry("""
                {"path": "EventDetail/Send/Source/User/Id", "field": "sender"}"""), entry("""
                {"path": "EventDetail/Send/Data", "data_parent": "tags", "data_name": "tag", "field": "tag",
                 "repeat": true, "max_length": 3}"""), entry("""
                {"path": "EventDetail/Send/Data", "data_parent": "tags", "data_name": "shout", "field": "tag",
                 "repeat": true, "transform": "upper", "max_length": 4}""")));
    }

    @Test
    void aGroupsItemCutToItsLengthIsReadBackWithItsMaxLength() {
        assertRoundTrip(sendMapping(entry("""
                {"path": "EventDetail/Send/Source/User/Id", "field": "sender"}"""), entry("""
                {"path": "EventDetail/Send/Destination/User", "for_each": {"split": "recipients", "on": ","},
                 "as": "recipient",
                 "fields": [{"path": "Id", "item": true, "max_length": 3},
                            {"path": "Domain", "item": true, "transform": "domain", "max_length": 4},
                            {"path": "Data", "data_name": "sender", "field": "sender", "scope": "record",
                             "max_length": 2}]}""")));
    }

    // --- helpers ----------------------------------------------------------------------------------------------------

    // Generated, read back lost and kept: lost, every entry is read as an idiom, is the mapping's own and writes the
    // same events; kept, the XSLT regenerates as it was.
    private static void assertRoundTrip(final TranslationMapping mapping) {
        final String code = codeOf(mapping);
        assertThat(code).contains("substring(");

        final RebuiltMapping lost = XsltReader.rebuild(code, null, null);
        assertThat(lost.problems()).as("%s", lost.summary()).isEmpty();
        assertThat(lost.raw()).as("%s", lost.summary()).isEmpty();
        // One rule alone: the common entries are read back as its own, in the XSLT's order.
        assertThat(rule(lost.mapping())).containsAll(rule(mapping));
        assertThat(events(codeOf(lost.mapping()))).isEqualTo(events(code));

        final RebuiltMapping kept = XsltReader.rebuild(code, mapping, code);
        assertThat(kept.problems()).as("%s", kept.summary()).isEmpty();
        assertThat(kept.added()).isEmpty();
        assertThat(kept.removed()).isEmpty();
        assertThat(normalise(codeOf(kept.mapping()))).isEqualTo(normalise(code));
    }

    // The send rule's own entries, as JSON.
    private static List<Map<String, Object>> rule(final TranslationMapping mapping) {
        return mapping.events().getFirst().fields().stream()
                .map(entry -> entry.toJson(DumpMode.COMPACT))
                .toList();
    }

    // A send rule with its TypeId, these fields and a destination device.
    private static TranslationMapping sendMapping(final Map<?, ?>... fields) {
        final List<Object> all = new ArrayList<>(List.of(entry("""
                {"path": "EventDetail/TypeId", "value": "Send"}""")));
        all.addAll(List.of(fields));
        all.add(entry("""
                {"path": "EventDetail/Send/Destination/Device/HostName", "value": "mx1"}"""));
        return mapping(with("input", "data_splitter", "common", BASE.subList(0, 5), "events",
                List.of(rule("send", List.of(), all))));
    }

    private static Map<String, Object> entry(final String text) {
        return json(text);
    }

    private static String codeOf(final TranslationMapping mapping) {
        final XsltGenerator.Generated result = XsltGenerator.generate(mapping, SCHEMA, "4.1.0");
        assertThat(result.ok()).as("%s", result.problems()).isTrue();
        return result.xslt();
    }

    private static String events(final String code) {
        return XmlSerialiser.toXml(transform(code, RECORDS));
    }

    // The XSLT without what isn't an edit (the declaration, space between tags).
    private static String normalise(final String text) {
        final String stripped = XsltVersionHistory.strip(text).replaceFirst("^\\s*<\\?xml[^>]*\\?>", "");
        return stripped.replaceAll(">\\s+<", "><").strip();
    }
}
