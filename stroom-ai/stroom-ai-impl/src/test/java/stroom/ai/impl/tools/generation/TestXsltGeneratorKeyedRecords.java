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

import stroom.ai.impl.tools.draft.DraftMapping;
import stroom.ai.impl.tools.sample.SplitterDryRun;
import stroom.ai.impl.tools.sample.SplitterSpec;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/// An auditd log drafted, generated and run with Saxon: each record of lines sharing an envelope one Event, timed by
/// the envelope's epoch, its lines' fields read under their names (`data[@name='SYSCALL']/data[@name='exe']`).
class TestXsltGeneratorKeyedRecords extends XsltRuns {

    private static final String AUDITD = sampleResource("/stroom/ai/impl/tools/sample/auditd-lines.log");

    @Test
    void eachRecordOfAnEnvelopesLinesIsOneValidEventAtItsTime() {
        final SplitterSpec spec = SplitterDryRun.inferSpec(AUDITD).spec();
        final Map<String, Object> draft = DraftMapping.draft(List.of(AUDITD), "Auditd", "Auditd", "Test", null, null,
                spec);
        @SuppressWarnings("unchecked") final Map<String, Object> mapping = (Map<String, Object>) draft.get("mapping");
        final XsltGenerator.Generated generated = XsltGenerator.generate(TranslationMapping.fromJson(
                decided(mapping)), GenerationFixtures.SCHEMA, "4.1.0");
        assertThat(generated.problems()).isEmpty();

        final XmlElement events = transform(generated.xslt(), recordsOf(SplitterDryRun.dryRun(spec, AUDITD, 1_000)));

        assertThat(invalid(events, GenerationFixtures.XSD)).isEmpty();
        final List<XmlElement> written = children(events, "Event");
        assertThat(written).hasSize(152);
        // The first event's envelope: msg=audit(1791709202.136:4501).
        assertThat(text(written.getFirst(), "EventTime/TimeCreated")).isEqualTo("2026-10-11T09:00:02.136Z");
        // The interleaved event's two records (its lines split by another's) each carry its envelope's time.
        assertThat(written.stream().map(event -> text(event, "EventTime/TimeCreated")))
                .doesNotContainNull()
                .filteredOn("2026-10-11T09:06:55.665Z"::equals)
                .hasSize(2);
    }

    // What an agent decides beyond the draft: a host for the Events with none, and each rule kept as Unknown on
    // purpose.
    @SuppressWarnings("unchecked")
    private static Map<String, Object> decided(final Map<String, Object> mapping) {
        final List<Object> common = new ArrayList<>((List<Object>) mapping.get("common"));
        common.removeIf(entry -> String.valueOf(entry).contains("EventSource/Device/HostName"));
        common.add(Map.of("path", "EventSource/Device/HostName", "value", "audit-host"));
        mapping.put("common", common);
        for (final Object rule : (List<Object>) mapping.get("events")) {
            final Map<String, Object> map = (Map<String, Object>) rule;
            if (String.valueOf(map.get("fields")).contains("/Unknown/")) {
                map.put("allow_unknown", "kept Unknown for the test");
            }
        }
        return mapping;
    }

    // The records a Data Splitter writes, as records:2: a field named by its path (SYSCALL/exe) nested under its
    // line's data element, as the converter writes it.
    private static String recordsOf(final SplitterDryRun.DryRun dryRun) {
        final StringBuilder out = new StringBuilder("<records xmlns=\"records:2\">");
        for (final Map<String, String> record : dryRun.records()) {
            final Map<String, Map<String, String>> nested = new LinkedHashMap<>();
            out.append("<record>");
            record.forEach((name, value) -> {
                final int slash = name.indexOf('/');
                if (slash < 0) {
                    out.append(data(name, value));
                } else {
                    nested.computeIfAbsent(name.substring(0, slash), line -> new LinkedHashMap<>())
                            .put(name.substring(slash + 1), value);
                }
            });
            nested.forEach((line, fields) -> {
                out.append("<data name=\"").append(escaped(line)).append("\">");
                fields.forEach((name, value) -> out.append(data(name, value)));
                out.append("</data>");
            });
            out.append("</record>");
        }
        return out.append("</records>").toString();
    }

    private static String data(final String name, final String value) {
        return "<data name=\"" + escaped(name) + "\" value=\"" + escaped(value) + "\"/>";
    }

    private static String escaped(final String text) {
        return text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\n", "&#10;").replace("\t", "&#9;");
    }

    private static String sampleResource(final String name) {
        try (InputStream in = Objects.requireNonNull(TestXsltGeneratorKeyedRecords.class.getResourceAsStream(name),
                name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
