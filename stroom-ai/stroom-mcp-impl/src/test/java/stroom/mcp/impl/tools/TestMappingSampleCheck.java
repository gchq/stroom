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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.draft.LocalCheck;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.sample.PyJson;
import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.ai.impl.tools.validation.XPathChecks;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/// The mapping checked against the sample before anything is generated or stepped (the Stroom MCP server's
/// `tools/generation.py` `_sample_check`, the mapping-level checks of `utils/xpathcheck.py`, their cases in
/// `tests/test_sample_check_speed.py` and `test_generation.py`, and `dev/eval/offline.py`'s check of each evaluation
/// case's reference mapping).
class TestMappingSampleCheck {

    private static final List<String> KEYS = List.of("logid", "type", "action", "srcip", "dstip", "user", "msg");
    private static final List<String> QUOTED = List.of("logid", "type", "action", "user", "msg");
    private static final String SAMPLE = IntStream.range(0, 400)
            .mapToObj(i -> PyJson.dumps(line(i, "")))
            .collect(Collectors.joining("\n"));

    @Test
    void eachEvaluationCasesReferenceMappingPassesTheChecksOffline() {
        // dev/eval/offline.py: what build_translation_xslt checks before it saves, for each case's reference (the
        // cases whose samples are under 60 KB; the Python server passed every one).
        for (final Map<String, Object> c : cases()) {
            final TranslationMapping mapping = TranslationMapping.parse(c.get("mapping"));
            @SuppressWarnings("unchecked") final List<String> texts = (List<String>) c.get("texts");
            @SuppressWarnings("unchecked") final Map<String, Object> spec = (Map<String, Object>) c.get("splitter");
            final SplitterSpec splitter = spec == null
                    ? null
                    : SplitterSpec.fromMap(spec);
            final List<String> problems = new ArrayList<>(XsltGenerator.generate(mapping, TranslationWorld.SCHEMA,
                    TranslationWorld.VERSION).problems());
            problems.addAll(strings(MappingSampleCheck.check(mapping, texts, splitter, TranslationWorld.SCHEMA,
                    false, Clock.systemUTC()).get("problems")));
            assertThat(problems).as(String.valueOf(c.get("id"))).isEqualTo(c.get("problems"));
        }
    }

    @Test
    void theCheckSaysWhichExtractionsMatchNoneAndWhichSome() {
        // The user key is missing from every seventh record: 342 of the 400 match.
        final TranslationMapping m = kvMapping(List.of(extraction("(?:^|[\\s,;])absent=(\\S*)", "absent"),
                extraction("\\w+=(\\w+)", "first")));

        final XPathChecks.Findings found = checked(m, Instant.now().plusSeconds(60));

        assertThat(found.problems()).singleElement().asString()
                .startsWith("extract[7] (body): the regex matches none of the 400");
        assertThat(found.warnings()).singleElement().asString()
                .startsWith("extract[5] (body): the regex matches 342 of the 400");
    }

    @Test
    void entriesPastTheDeadlineAreNamedNotChecked() {
        final TranslationMapping m = kvMapping(List.of(extraction("(?:^|[\\s,;])absent=(\\S*)", "absent")));

        final XPathChecks.Findings found = checked(m, Instant.EPOCH);

        assertThat(found.problems()).isEmpty();
        assertThat(found.warnings()).containsExactly(IntStream.range(0, 8)
                .mapToObj(n -> "extract[" + n + "] (body)")
                .collect(Collectors.joining(", "))
                                                     + " not checked against the sample: the check's time ran out. "
                                                     + "step_sample shows what they give.");
    }

    @Test
    void aLargeKeyValueSampleIsCheckedInSeconds() {
        final String extra = IntStream.range(0, 50).mapToObj(j -> "k" + j + "=\"v" + j + "\"")
                .collect(Collectors.joining(" "));
        final String sample = IntStream.range(0, 3000)
                .mapToObj(i -> PyJson.dumps(line(i, " " + extra)))
                .collect(Collectors.joining("\n"));
        final List<Map<String, Object>> more = IntStream.range(0, 50)
                .mapToObj(j -> extraction("(?:^|[\\s,;])k" + j + "=\"([^\"]*)\"", "k" + j))
                .toList();
        final TranslationMapping m = kvMapping(more);
        final long started = System.nanoTime();

        final XPathChecks.Findings found = MappingSampleCheck.checkExtractions(m, List.of(sample), null,
                LocalCheck.sampleRecords(m, List.of(sample), null).records(), null, Clock.systemUTC());

        assertThat(found.problems()).isEmpty();
        // Within the check's own time (the MCP server's test allowed 20 s, with its Python twin of each regex
        // counting most texts): Saxon reads every text here, some 13 s on its own, longer on a loaded machine.
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(MappingSampleCheck.CHECK_TIME);
    }

    @Test
    void aFieldAnInferredSplitterLacksIsAWarningNotARefusal() {
        // Seen: a hand-written syslog splitter names a field 'pwd'; the sample, read with the spec inferred from
        // it, has 'pid' instead, and the mapping was refused though the build's converter does write 'pwd'.
        final Map<String, Object> json = TranslationWorld.mapping();
        @SuppressWarnings("unchecked") final List<Object> common = new ArrayList<>((List<Object>) json.get("common"));
        common.add(Map.of("path", "EventSource/Device/Name", "field", "hosts"));
        json.put("common", common);
        final TranslationMapping m = TranslationMapping.parse(json);
        final List<String> sample = List.of("time,action,user,result,sid,host,port\n"
                                            + "2026-09-28T10:00:00.000Z,login,alice,ok,s1,ws01,22\n"
                                            + "2026-09-28T10:03:00.000Z,login,bob,fail,s2,ws03,22\n");
        final SplitterSpec spec = SplitterSpec.fromMap(Map.of("kind", "delimited", "header", true));

        final Map<String, Object> guessed = MappingSampleCheck.check(m, sample, spec, TranslationWorld.SCHEMA, true,
                Clock.systemUTC());
        assertThat(strings(guessed.get("problems"))).noneMatch(p -> p.contains("'hosts'"));
        assertThat(strings(guessed.get("warnings"))).anyMatch(w -> w.contains("field 'hosts'")
                                                                    && w.contains("inferred Data Splitter"));

        final Map<String, Object> given = MappingSampleCheck.check(m, sample, spec, TranslationWorld.SCHEMA, false,
                Clock.systemUTC());
        assertThat(strings(given.get("problems"))).anyMatch(p -> p.contains("field 'hosts'"));
    }

    @Test
    void theXpathsCheckedAreThoseOnlyTheRecordAnswers() {
        // Not those calling a shared XSLT's functions (only Stroom, which imports it, can run them), nor those
        // reading a variable, which only the XSLT binds; a regex replacement's $1 is not one.
        final TranslationMapping m = TranslationMapping.parse(PyJson.loads("""
                {"input": "xml", "root": "logs", "record": "log",
                 "functions": [{"href": "GeoIP-V1", "prefix": "geo", "namespace": "http://example.com/geo"}],
                 "common": [{"path": "EventTime/TimeCreated", "xpath": "@time"},
                            {"path": "EventSource/Device/HostName", "xpath": "replace(host, '(.*)', '$1')"},
                            {"path": "EventSource/Device/IPAddress", "xpath": "geo:lookup-ip(ip)"},
                            {"path": "EventSource/User/Id", "xpath": "stroom:meta('User')"}],
                 "events": [{"name": "a", "when": [{"xpath": "kind", "equals": "x"}],
                             "fields": [{"path": "EventDetail/TypeId", "xpath": "$kind"}]}]}"""));

        assertThat(MappingSampleCheck.xpathInputs(m)).isEqualTo(Map.of("@time", List.of("EventTime/TimeCreated"),
                "replace(host, '(.*)', '$1')", List.of("EventSource/Device/HostName"),
                "kind", List.of("[a] when")));
    }

    @Test
    void fragmentsAreCheckedInTheirWrappersNamespace() {
        // As the XMLFragmentParser's wrapper gives them: a fragment declaring no namespace takes its default. Read
        // without it, the checks passed xpaths that select nothing in Stroom.
        final TranslationMapping m = TranslationMapping.parse(PyJson.loads("""
                {"input": "xml_fragments", "record": "Event", "xml_namespace": "records:2",
                 "common": [{"path": "EventTime/TimeCreated", "xpath": "System/TimeCreated/@SystemTime"}],
                 "events": [{"name": "a", "fields": [{"path": "EventDetail/TypeId", "xpath": "System/EventID"}]}]}"""));

        final List<String> documents = MappingSampleCheck.recordDocuments(m, List.of(TestSampleProfiling.FRAGMENTS),
                null);

        assertThat(documents).hasSize(3).allSatisfy(d -> assertThat(d).startsWith("<Event xmlns=\"records:2\">"));
        assertThat(MappingSampleCheck.checkXPaths(m, List.of(TestSampleProfiling.FRAGMENTS), null)).isEmpty();
        // An xpath no record answers is said.
        final TranslationMapping missing = TranslationMapping.parse(PyJson.loads(PyJson.dumps(
                m.toJson(DumpMode.COMPACT)).replace("System/EventID",
                "System/Nope")));
        assertThat(MappingSampleCheck.checkXPaths(missing, List.of(TestSampleProfiling.FRAGMENTS), null))
                .singleElement().asString().contains("System/Nope").contains("selects nothing in any of the 3");
    }

    // A record: a time, a host and a key=value body, every seventh without the user key.
    private static Map<String, Object> line(final int i, final String more) {
        final Map<String, String> pairs = new LinkedHashMap<>();
        pairs.put("logid", "\"" + String.format("%010d", i) + "\"");
        pairs.put("type", i % 3 != 0
                ? "\"traffic\""
                : "\"event\"");
        pairs.put("action", "\"accept\"");
        pairs.put("srcip", "10.0.0." + (i % 250));
        pairs.put("dstip", "8.8.8.8");
        pairs.put("user", "\"u" + i + "\"");
        pairs.put("msg", "\"message " + i + "\"");
        if (i % 7 == 0) {
            pairs.remove("user");
        }
        final Map<String, Object> record = new LinkedHashMap<>();
        record.put("time", "2026-10-01T10:00:00.000Z");
        record.put("host", "fw1");
        record.put("body", pairs.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                                   .collect(Collectors.joining(" ")) + more);
        return record;
    }

    private static TranslationMapping kvMapping(final List<Map<String, Object>> extra) {
        final List<Map<String, Object>> extract = new ArrayList<>();
        for (final String k : KEYS) {
            extract.add(extraction(QUOTED.contains(k)
                    ? "(?:^|[\\s,;])" + k + "=\"([^\"]*)\""
                    : "(?:^|[\\s,;])" + k + "=([^\\s,;\"]*)", k));
        }
        extract.addAll(extra);
        final Map<String, Object> json = new LinkedHashMap<>(TranslationWorld.mapping());
        json.put("input", "json");
        json.put("json_layout", "lines");
        json.put("extract", extract);
        json.put("common", PyJson.loads("""
                [{"path": "EventTime/TimeCreated", "field": "time"},
                 {"path": "EventSource/System/Name", "value": "FortiOS"},
                 {"path": "EventSource/System/Environment", "value": "Test"},
                 {"path": "EventSource/Generator", "value": "fortigate"},
                 {"path": "EventSource/Device/HostName", "field": "host"},
                 {"path": "EventSource/User/Id", "field": "user"}]"""));
        @SuppressWarnings("unchecked") final List<Object> events = (List<Object>) json.get("events");
        json.put("events", List.of(events.getFirst()));
        json.remove("unmatched");
        return TranslationMapping.parse(json);
    }

    private static Map<String, Object> extraction(final String regex, final String name) {
        return Map.of("field", "body", "regex", regex, "names", List.of(name));
    }

    private static XPathChecks.Findings checked(final TranslationMapping m, final Instant deadline) {
        return MappingSampleCheck.checkExtractions(m, List.of(SAMPLE), null,
                LocalCheck.sampleRecords(m, List.of(SAMPLE), null).records(), deadline, Clock.systemUTC());
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(final Object value) {
        return value instanceof final List<?> list
                ? (List<String>) list
                : List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> cases() {
        try (InputStream in = TestMappingSampleCheck.class.getResourceAsStream("offline_cases.json")) {
            return JsonUtil.readValue(new String(in.readAllBytes(), StandardCharsets.UTF_8), List.class);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
