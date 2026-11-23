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

import stroom.ai.impl.AiConfig;
import stroom.ai.impl.AiSamplingConfig;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.sample.PartSampler;
import stroom.ai.impl.tools.sample.SampleProfiler;
import stroom.ai.impl.tools.sample.SourceTypeRows;
import stroom.ai.impl.tools.sample.SourceTypeScope;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.ai.impl.tools.streams.StreamReader;
import stroom.meta.shared.Meta;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Sample streams' texts read through a build's scope over an aggregated feed's source types.
class TestSampleStreamTexts {

    // The characters a part was read to in a stream of 20 parts or more, before parts were sampled as events.
    private static final int CUT = 100_000;
    private static final String FIRST_PART = openWebUiPart(150_000, CUT - 5);
    private static final String OTHER_PART = openWebUiPart(2_000, -1);
    private static final long STREAM = 9L;

    private static final SourceTypeScope DB = new SourceTypeScope("b", "source", List.of(
            new SourceTypeRows.Row("db", SourceTypeRows.VALUE, "db"),
            new SourceTypeRows.Row("web", SourceTypeRows.VALUE, "web")), List.of("db"));

    @Test
    void eachTextKeepsOnlyTheScopesRecordsAndATextOfNoneIsLeftOut() {
        final Map<String, String> texts = new LinkedHashMap<>();
        texts.put("stream 1", "{\"source\": \"db\", \"n\": 1}\n{\"source\": \"web\", \"n\": 2}\n");
        texts.put("stream 2", "{\"source\": \"web\", \"n\": 3}\n");
        texts.put("stream 3", "<13>Nov 12 10:00:00 host app: syslog, not records of a field\n");

        final List<SampleStreamTexts.Cut> cut = List.of(new SampleStreamTexts.Cut("stream 1", 1, 0),
                new SampleStreamTexts.Cut("stream 2", 2, 0));
        final SampleStreamTexts.Scoped scoped = SampleStreamTexts.inScope(new SampleStreamTexts.Texts(texts,
                List.of("a note"), cut), DB);

        assertThat(scoped.texts().texts()).containsOnlyKeys("stream 1", "stream 3");
        assertThat(scoped.texts().texts().get("stream 1")).isEqualTo("{\"source\": \"db\", \"n\": 1}\n");
        assertThat(scoped.texts().cut()).extracting(SampleStreamTexts.Cut::name).containsExactly("stream 1");
        assertThat(scoped.texts().notes()).contains("a note").anySatisfy(note -> assertThat(note)
                .startsWith("stream 3 can't be read record by record"));
        assertThat(scoped.said()).containsEntry("records_in_scope", 1)
                .containsEntry("records_left_out", Map.of("web", 2));
    }

    @Test
    void aSampleWithNoneOfTheScopesRecordsIsRefused() {
        final SampleStreamTexts.Texts texts = new SampleStreamTexts.Texts(Map.of("stream 2",
                "{\"source\": \"web\", \"n\": 3}\n"), List.of());

        assertThatThrownBy(() -> SampleStreamTexts.inScope(texts, DB)).isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("None of the sample's records is in build 'b''s scope (db)");
    }

    @Test
    void aScopeByAPartsMetaKeepsOrLeavesOutEachPartWholeByItsOwnValue() {
        final StreamReader reader = Mockito.mock(StreamReader.class);
        Mockito.when(reader.partAttributes(9L, 0)).thenReturn(Map.of("Roles", "DNS Server, Directory Services"));
        Mockito.when(reader.partAttributes(9L, 1)).thenReturn(Map.of("Roles", "File Server"));
        Mockito.when(reader.partAttributes(9L, 2)).thenReturn(Map.of());
        final SampleStreamTexts sampleTexts = new SampleStreamTexts(reader, Mockito.mock(DocumentAccess.class),
                AiConfig::new);
        final SourceTypeScope directory = new SourceTypeScope("b", "meta:Roles", List.of(new SourceTypeRows.Row(
                "Directory", SourceTypeRows.MEMBER, "Directory Services", ",")), List.of("Directory"));
        final Map<String, String> texts = new LinkedHashMap<>();
        // XML, which a record field's scope reads whole: kept or left out by its part's value.
        texts.put("stream 9 part 0", "<Events><Event><Id>1</Id></Event><Event><Id>2</Id></Event></Events>\n");
        texts.put("stream 9 part 1", "<Events><Event><Id>3</Id></Event></Events>\n");
        texts.put("stream 9 part 2", "<Events><Event><Id>4</Id></Event></Events>\n");

        final SampleStreamTexts.Scoped scoped = sampleTexts.scoped(new SampleStreamTexts.Texts(texts, List.of()),
                directory);

        assertThat(scoped.texts().texts()).containsOnlyKeys("stream 9 part 0");
        assertThat(scoped.said()).containsEntry("records_in_scope", 2).containsEntry("records_left_out",
                Map.of(SourceTypeScope.UNMATCHED, 2));
        assertThat(String.valueOf(scoped.said().get("note"))).contains("left out whole");
        assertThat(SampleStreamTexts.partOf("stream 9 part 2")).hasValueSatisfying(part -> {
            assertThat(part.stream()).isEqualTo(9L);
            assertThat(part.part()).isEqualTo(2);
        });
        assertThat(SampleStreamTexts.partOf("stream 7")).hasValueSatisfying(part -> assertThat(part.part())
                .isZero());
    }

    // --------------------------------------------------------------------------------
    // A stream of newline-free JSON arrays (OpenWebUI audit log, user testing 2026-11-19).
    // --------------------------------------------------------------------------------

    @Test
    void anEventTooLongToSampleIsLeftOutWholeAndTheEventsAfterItAreKeptWhole() {
        // Seen in user testing: an OpenWebUI audit stream of 787 parts, each one JSON array of filebeat events on one
        // line. With 20 parts read, each was read to 100,000 characters; a part longer than that has no line end to
        // cut back to, so it was cut inside a \\uXXXX escape, and profile_sample, classify_kinds and the other
        // sampling tools refused it: "This starts as JSON but isn't valid JSON: Invalid \\uXXXX escape at
        // character 99996". Read as a stream of events, the second and seventh of 30 (each over 100,000
        // characters) are left out, and the other 28 kept whole.
        final StreamReader reader = stream(25, part -> part == 0
                ? events(30, 1, 6)
                : OTHER_PART);

        final SampleStreamTexts.Texts texts = sampleTexts(reader, new AiConfig()).read(List.of(STREAM));

        final String read = texts.texts().get("stream 9 part 0");
        assertThat((List<?>) ToolJson.loads(read)).hasSize(28);
        assertThat(read).doesNotContain("\"n\":1,").doesNotContain("\"n\":6,").contains("\"n\":29,");
        assertThat(texts.notes()).contains("stream 9 part 0: 2 events over 100,000 characters left out of the "
                                           + "sample (largest 150,020 characters; longest string value 150,000, in "
                                           + "'message'); 28 events sampled");
        assertThat(texts.skipped()).singleElement().satisfies(left -> {
            assertThat(left.name()).isEqualTo("stream 9 part 0");
            assertThat(left.events().longestText()).isEqualTo(150_000);
        });
        assertThat(texts.texts().values()).allSatisfy(text -> assertThat(ToolJson.loads(text))
                .isInstanceOf(List.class));
    }

    @Test
    void aNewlineFreePartsTextIsNeverRefusedAsInvalidJson() {
        final StreamReader reader = stream(25, part -> part == 0
                ? FIRST_PART
                : OTHER_PART);

        final SampleStreamTexts.Texts texts = sampleTexts(reader, new AiConfig()).read(List.of(STREAM));

        assertThat(texts.texts()).doesNotContainKey("stream 9 part 0");
        assertThat(texts.notes()).anySatisfy(note -> assertThat(note).startsWith("stream 9 part 0: 1 event over "
                                                                                  + "100,000 characters left out"));
        assertThatCode(() -> SampleProfiler.profileMany(texts.texts(), SampleProfiler.MAX_RECORDS))
                .doesNotThrowAnyException();
    }

    @Test
    void aStreamOfManyPartsHasThemReadSpreadThroughItFromItsFirstToItsLast() {
        final StreamReader reader = stream(787, part -> "[{\"part\":" + part + "}]");

        final SampleStreamTexts.Texts texts = sampleTexts(reader, new AiConfig()).read(List.of(STREAM));

        assertThat(texts.texts()).hasSize(20).containsKeys("stream 9 part 0", "stream 9 part 786")
                .containsEntry("stream 9 part 41", "[{\"part\":41}]");
        assertThat(texts.notes()).contains("stream 9 has 787 parts: 20 of them are read, spread through it (parts "
                                           + PartSampler.said(PartSampler.spread(787, 20)) + ")");
        assertThat(texts.cut()).isEmpty();
    }

    @Test
    void aStreamsSampleHoldsNoMoreThanTheMostCharactersOfAStream() {
        // 20 parts of 20 events of about 20,000 characters: 8,000,000 if each were read whole.
        final StreamReader reader = stream(20, part -> eventsOf(20, 20_000));

        final SampleStreamTexts.Texts texts = sampleTexts(reader, new AiConfig()).read(List.of(STREAM));

        final long chars = texts.texts().values().stream().mapToLong(String::length).sum();
        // At most the stream's characters, but for the last part's floor of one event of the most length.
        assertThat(chars).isLessThanOrEqualTo(2_000_000 + 100_000).isGreaterThan(1_500_000);
        assertThat(texts.texts()).hasSize(20);
        assertThat(texts.texts().values()).allSatisfy(text -> assertThat((List<?>) ToolJson.loads(text))
                .isNotEmpty());
        assertThat(texts.cut()).hasSize(20);
    }

    @Test
    void theConfiguredLimitsAreKept() {
        final AiConfig config = Mockito.mock(AiConfig.class);
        Mockito.when(config.getSampling()).thenReturn(new AiSamplingConfig(null, 50_000));
        // Events of 60,000 characters are too long; those of 20 are kept, however many.
        final StreamReader reader = stream(2, part -> "[" + String.join(",", List.of(eventOf(60_000),
                eventOf(20), eventOf(20), eventOf(60_000), eventOf(20), eventOf(20))) + "]");

        final SampleStreamTexts.Texts texts = sampleTexts(reader, config).read(List.of(STREAM));

        assertThat(texts.texts()).hasSize(2).allSatisfy((name, text) ->
                assertThat((List<?>) ToolJson.loads(text)).hasSize(4));
        assertThat(texts.notes()).anySatisfy(note -> assertThat(note).startsWith("stream 9 part 0: 2 events over "
                                                                                  + "50,000 characters"));
    }

    @Test
    void everyChosenPartIsReadHoweverFewTheStreamsCharacters() {
        // 20 parts of 30,000 characters, a stream's sample at most 40,000: each part still gets an event of the
        // most length (20,000), so every one is read.
        final AiConfig config = Mockito.mock(AiConfig.class);
        Mockito.when(config.getSampling()).thenReturn(new AiSamplingConfig(40_000, 20_000));
        final StreamReader reader = stream(20, part -> eventsOf(3, 10_000));

        final SampleStreamTexts.Texts texts = sampleTexts(reader, config).read(List.of(STREAM));

        assertThat(texts.texts()).hasSize(20);
        assertThat(texts.texts().values()).allSatisfy(text -> assertThat((List<?>) ToolJson.loads(text))
                .hasSizeBetween(1, 2));
        assertThat(texts.notes()).noneSatisfy(note -> assertThat(note).contains("not read"));
    }

    @Test
    void aSampleWhoseEveryEventIsTooLongToSampleIsRefusedSayingWhy() {
        final StreamReader reader = stream(1, part -> FIRST_PART);

        assertThatThrownBy(() -> sampleTexts(reader, new AiConfig()).read(List.of(STREAM)))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("No event of the sample could be sampled")
                .hasMessageContaining("stream 9: 1 event over 100,000 characters left out of the sample");
    }

    @Test
    void aPartOfLinesIsReadToItsShareAndSaidToBeSampledInPart() {
        final String lines = "a=1 b=2\n".repeat(400_000);
        final StreamReader reader = stream(1, part -> lines);

        final SampleStreamTexts.Texts texts = sampleTexts(reader, new AiConfig()).read(List.of(STREAM));

        assertThat(texts.texts().get("stream 9")).hasSize(2_000_000).endsWith("\n");
        assertThat(texts.cut()).extracting(SampleStreamTexts.Cut::name).containsExactly("stream 9");
        assertThat(texts.notes()).anySatisfy(note -> assertThat(note).startsWith("stream 9 is sampled in part"));
    }

    // A stream of `parts` parts sampled from their texts.
    private static StreamReader stream(final int parts, final IntFunction<String> partText) {
        final StreamReader reader = Mockito.mock(StreamReader.class);
        Mockito.when(reader.meta(STREAM)).thenReturn(Meta.builder().id(STREAM).feedName("OPENWEBUI-AUDIT").build());
        Mockito.when(reader.partCount(STREAM)).thenReturn(parts);
        // Read as UTF-8 throughout: nothing to say of its charset.
        Mockito.when(reader.charsetReport(STREAM)).thenReturn(null);
        SampledParts.stub(reader, STREAM, partText);
        return reader;
    }

    private static SampleStreamTexts sampleTexts(final StreamReader reader, final AiConfig config) {
        final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
        Mockito.when(documentAccess.canViewData()).thenReturn(true);
        return new SampleStreamTexts(reader, documentAccess, () -> config);
    }

    // A JSON array of `count` events, those at the indexes given 150,000 characters long.
    private static String events(final int count, final int... big) {
        return IntStream.range(0, count)
                .mapToObj(i -> IntStream.of(big).anyMatch(b -> b == i)
                        ? "{\"n\":" + i + ",\"message\":\"" + "m".repeat(150_000) + "\"}"
                        : "{\"n\":" + i + ",\"message\":\"short\"}")
                .collect(Collectors.joining(",", "[", "]"));
    }

    // A JSON array of `count` events of about `length` characters each.
    private static String eventsOf(final int count, final int length) {
        return IntStream.range(0, count).mapToObj(i -> eventOf(length)).collect(Collectors.joining(",", "[", "]"));
    }

    private static String eventOf(final int length) {
        return "{\"m\":\"" + "x".repeat(Math.max(0, length - 8)) + "\"}";
    }

    // A part of an OpenWebUI audit stream as filebeat wrote it: one JSON array of one event on one line, its message
    // a JSON string, exactly `length` characters, with a \\u00e9 escape starting at `escapeAt` (none if -1).
    private static String openWebUiPart(final int length, final int escapeAt) {
        final String prefix = "[{\"@timestamp\":\"2026-11-18T09:00:00.000Z\",\"event\":{\"kind\":\"event\"},"
                              + "\"message\":\"{\\\"user\\\":\\\"u1\\\",\\\"request_object\\\":\\\"";
        final String suffix = "\\\"}\"}]";
        final StringBuilder text = new StringBuilder(prefix);
        if (escapeAt >= 0) {
            text.append("x".repeat(escapeAt - text.length())).append("\\u00e9");
        }
        text.append("x".repeat(length - suffix.length() - text.length())).append(suffix);
        return text.toString();
    }
}
