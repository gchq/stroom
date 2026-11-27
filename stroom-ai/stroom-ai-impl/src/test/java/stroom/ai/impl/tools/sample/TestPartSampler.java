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

package stroom.ai.impl.tools.sample;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/// A stream part's sample, read as a stream of whole events, and the parts of a stream chosen for it.
class TestPartSampler {

    private static final PartSampler.Limits LIMITS = new PartSampler.Limits(100_000, 2_000_000);

    @Test
    void manyPartsAreChosenSpreadFromTheFirstToTheLast() {
        // Seen in user testing: an OpenWebUI audit stream of 787 parts read through its first 20 only.
        final List<Integer> chosen = PartSampler.spread(787, 20);

        assertThat(chosen).hasSize(20).doesNotHaveDuplicates().isSorted().startsWith(0).endsWith(786);
        assertThat(PartSampler.spread(787, 20)).as("the same each time").isEqualTo(chosen);
        // Evenly: no gap much bigger than another.
        final List<Integer> gaps = IntStream.range(1, chosen.size()).mapToObj(i -> chosen.get(i) - chosen.get(i - 1))
                .toList();
        assertThat(gaps).allSatisfy(gap -> assertThat(gap).isBetween(41, 42));
    }

    @Test
    void aStreamOfNoMorePartsThanAreReadHasThemAllRead() {
        assertThat(PartSampler.spread(5, 20)).containsExactly(0, 1, 2, 3, 4);
        assertThat(PartSampler.spread(1, 20)).containsExactly(0);
        assertThat(PartSampler.spread(0, 20)).containsExactly(0);
        assertThat(PartSampler.spread(21, 20)).hasSize(20).startsWith(0, 1).endsWith(20);
        assertThat(PartSampler.spread(2, 1)).containsExactly(0);
    }

    @Test
    void aPartsShareIsWhatIsLeftSpreadOverThePartsLeftButNeverLessThanTheLeast() {
        assertThat(PartSampler.share(2_000_000, 20, 100_000)).isEqualTo(100_000);
        assertThat(PartSampler.share(2_000_000, 4, 100_000)).isEqualTo(500_000);
        assertThat(PartSampler.share(2_000_000, 40, 100_000)).isEqualTo(100_000);
        // Every part is read, room for an event of the most length, however little is left.
        assertThat(PartSampler.share(30_000, 3, 100_000)).isEqualTo(100_000);
        assertThat(PartSampler.share(0, 3, 100_000)).isEqualTo(100_000);
        assertThat(PartSampler.share(-50_000, 3, 100_000)).isEqualTo(100_000);
    }

    @Test
    void partsAreSaidShortenedWhenMany() {
        assertThat(PartSampler.said(List.of(3))).isEqualTo("3");
        assertThat(PartSampler.said(List.of(0, 5, 9))).isEqualTo("0, 5 and 9");
        assertThat(PartSampler.said(PartSampler.spread(787, 20))).startsWith("0, 41, 82").endsWith("and 786")
                .contains("…");
    }

    @Test
    void aJsonArrayPartIsSampledAsItsWholeEvents() {
        final String part = events(30, 1, 6);

        final PartSampler.Sample sample = sample(part, LIMITS);

        assertThat(sample.mode()).isEqualTo(PartSampler.Mode.JSON_ARRAY);
        assertThat(sample.events()).isEqualTo(28);
        assertThat(sample.whole()).isFalse();
        assertThat(sample.skipped().count()).isEqualTo(2);
        assertThat(ToolJson.loads(sample.text())).asList().hasSize(28);
        assertThat(sample.said(LIMITS.maxEventChars())).singleElement().asString()
                .startsWith("2 events over 100,000 characters left out of the sample (largest ")
                .contains("longest string value 150,000, in 'message'")
                .endsWith("; 28 events sampled");
    }

    @Test
    void aJsonArrayPartThatEndsFirstGivesWhatItHas() {
        final PartSampler.Sample sample = sample(events(5), LIMITS);

        assertThat(sample.events()).isEqualTo(5);
        assertThat(sample.whole()).isTrue();
        assertThat(sample.said(LIMITS.maxEventChars())).isEmpty();
    }

    @Test
    void charactersNotACountDecideHowManyEventsAreKept() {
        // Each short event is 25 characters: four fit in 100.
        final PartSampler.Limits limits = new PartSampler.Limits(50_000, 100);

        final PartSampler.Sample sample = sample(events(10), limits);

        assertThat(sample.events()).isEqualTo(4);
        assertThat(ToolJson.loads(sample.text())).asList().hasSize(4);
    }

    @Test
    void aJsonArrayPartEndingInsideAnEventSaysItsLastEventIsLeftOut() {
        final String part = events(3);

        final PartSampler.Sample sample = sample(part.substring(0, part.length() - 5), LIMITS);

        assertThat(sample.events()).isEqualTo(2);
        assertThat(sample.unterminated()).isTrue();
        assertThat(String.join(" ", sample.said(LIMITS.maxEventChars()))).contains("its last event is cut off");
    }

    @Test
    void theOpenWebUiPartsAreNeverCut() {
        // As seen in user testing: one event of 150,000 characters, an escape where a read of 100,000 would cut.
        final PartSampler.Sample sample = sample(OpenWebUiParts.part(150_000, OpenWebUiParts.ESCAPE_AT), LIMITS);

        assertThat(sample.text()).isEqualTo("[]");
        assertThat(sample.skipped().count()).isEqualTo(1);
        // The event, the array's brackets not counted.
        assertThat(sample.skipped().largest()).isEqualTo(149_998);
        assertThat(sample.skipped().path()).isEqualTo("message");
        assertThat(sample.skipped().longestText()).isGreaterThan(140_000);
    }

    @Test
    void linesAreSampledUpToTheShareLeavingOutLinesTooLong() {
        final String text = "a=1\n" + "b=" + "x".repeat(200) + "\na=2\na=3\n";

        final PartSampler.Sample sample = sample(text, new PartSampler.Limits(100, 1000));

        assertThat(sample.mode()).isEqualTo(PartSampler.Mode.LINES);
        assertThat(sample.text()).isEqualTo("a=1\na=2\na=3\n");
        assertThat(sample.skipped().count()).isEqualTo(1);
        assertThat(sample.said(100)).singleElement().asString().endsWith("; 3 lines sampled");
    }

    @Test
    void linesAreReadToThePartsShare() {
        final String text = IntStream.range(0, 100).mapToObj(i -> "line " + i + "\n").collect(Collectors.joining());

        final PartSampler.Sample sample = sample(text, LIMITS);

        assertThat(sample.text()).isEqualTo(text);
        assertThat(sample.whole()).isTrue();
    }

    @Test
    void jsonLinesOfArraysAreReadALineAtATime() {
        final String text = "[{\"a\":1}]\n[{\"a\":2}]\n";

        final PartSampler.Sample sample = sample(text, LIMITS);

        assertThat(sample.mode()).isEqualTo(PartSampler.Mode.LINES);
        assertThat(sample.text()).isEqualTo(text);
    }

    @Test
    void anXmlDocumentIsSampledAsItsWholeRecordsInItsOwnRoot() {
        final String text = "<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<!-- a comment -->\n"
                            + "<Events xmlns=\"event-logging:3\" Version=\"4.1.0\">\n"
                            + IntStream.range(0, 6).mapToObj(i -> i == 1 || i == 4
                                    ? "  <Event><Id>" + i + "</Id><Data>" + "d".repeat(300) + "</Data></Event>\n"
                                    : "  <Event><Id>" + i + "</Id><Data><![CDATA[a < b]]></Data></Event>\n")
                                    .collect(Collectors.joining())
                            + "</Events>\n";

        final PartSampler.Sample sample = sample(text, new PartSampler.Limits(200, 100_000));

        assertThat(sample.mode()).isEqualTo(PartSampler.Mode.XML);
        assertThat(sample.events()).isEqualTo(4);
        assertThat(sample.skipped().count()).isEqualTo(2);
        assertThat(sample.skipped().largest()).isGreaterThan(300);
        assertThat(sample.skipped().longestText()).isEqualTo(300);
        assertThat(sample.whole()).isFalse();
        final XmlTree.Element root = XmlTree.parse(sample.text());
        assertThat(root.localName).isEqualTo("Events");
        assertThat(root.namespace).isEqualTo("event-logging:3");
        assertThat(root.elements()).hasSize(4);
        assertThat(sample.text()).startsWith("<?xml version=\"1.1\" encoding=\"UTF-8\"?>")
                .contains("<Events xmlns=\"event-logging:3\" Version=\"4.1.0\">")
                .contains("<Event><Id>5</Id>").doesNotContain("<Id>1</Id>").doesNotContain("<Id>4</Id>");
        assertThat(sample.said(200)).singleElement().asString()
                .startsWith("2 events over 200 characters left out of the sample").endsWith("; 4 events sampled");
    }

    @Test
    void aNamespacedRootIsKeptWithItsPrefixAndClosedByItsOwnName() {
        final String text = "<evt:Events xmlns:evt=\"event-logging:3\" xmlns:x=\"urn:x\">"
                            + "<evt:Event><evt:Id x:a='1&gt;2'>1</evt:Id></evt:Event>"
                            + "<evt:Event><evt:Id>2</evt:Id><evt:Big>" + "b".repeat(500) + "</evt:Big></evt:Event>"
                            + "<evt:Event/></evt:Events>";

        final PartSampler.Sample sample = sample(text, new PartSampler.Limits(200, 100_000));

        assertThat(sample.events()).isEqualTo(2);
        assertThat(sample.text()).endsWith("</evt:Events>\n");
        final XmlTree.Element root = XmlTree.parse(sample.text());
        assertThat(root.localName).isEqualTo("Events");
        assertThat(root.namespace).isEqualTo("event-logging:3");
        assertThat(root.elements()).hasSize(2);
    }

    @Test
    void xmlFragmentsAreSampledAsTheirTopLevelElements() {
        final String text = "<Event><Id>1</Id></Event>\n<Event><Id>2</Id><Big>" + "b".repeat(500) + "</Big></Event>\n"
                            + "<Event><Id>3</Id></Event>\n";

        final PartSampler.Sample sample = sample(text, new PartSampler.Limits(200, 100_000));

        assertThat(sample.mode()).isEqualTo(PartSampler.Mode.XML);
        assertThat(sample.text()).isEqualTo("<Event><Id>1</Id></Event>\n<Event><Id>3</Id></Event>\n");
        assertThat(sample.skipped().count()).isEqualTo(1);
        assertThat(SampleProfiler.profile(sample.text())).containsEntry("format", SampleProfiler.XML_FRAGMENTS);
    }

    @Test
    void anXmlDocumentWhoseFirstRecordIsTooLongIsNeverRefusedAsNotWellFormed() {
        // The XML equivalent of the OpenWebUI parts: a read to a limit cut the first record, which wasn't
        // well-formed.
        final String text = "<Events>\n<Event><Data>" + "d".repeat(150_000) + "</Data></Event>\n"
                            + "<Event><Id>2</Id></Event>\n</Events>\n";

        final PartSampler.Sample sample = sample(text, LIMITS);

        assertThat(sample.text()).isEqualTo("<Events>\n<Event><Id>2</Id></Event>\n</Events>\n");
        assertThatCode(() -> SampleProfiler.profile(sample.text())).doesNotThrowAnyException();
        assertThat(SampleProfiler.profile(sample.text())).containsEntry("format", SampleProfiler.XML)
                .containsEntry("records", 1);
    }

    @Test
    void xmlRecordsAreKeptUpToTheShareNeverCut() {
        final String text = "<Events>" + "<Event><Id>1</Id></Event>".repeat(10) + "</Events>";

        final PartSampler.Sample sample = sample(text, new PartSampler.Limits(100, 60));

        assertThat(sample.events()).isEqualTo(2);
        assertThat(sample.whole()).isFalse();
        assertThat(XmlTree.parse(sample.text()).elements()).hasSize(2);
    }

    @Test
    void xmlThatCantBeReadRecordByRecordIsReadFromItsStart() {
        // A root holding only text: no record in it.
        final String text = "<Doc>\n" + "words\n".repeat(20) + "</Doc>\n";

        final PartSampler.Sample sample = sample(text, new PartSampler.Limits(100, 40));

        assertThat(sample.mode()).isEqualTo(PartSampler.Mode.HEAD);
        assertThat(sample.cut()).isTrue();
        assertThat(sample.text()).isEqualTo("<Doc>\nwords\nwords\nwords\nwords\nwords\n");
        assertThat(String.join(" ", sample.said(100))).contains("can't be read record by record")
                .contains("its root holds no record element");
        assertThat(sample(text, LIMITS).text()).isEqualTo(text);
    }

    @Test
    void syslogIsReadALineAtATimeNotAsXml() {
        final String text = "<34>1 2026-11-18T10:00:00Z host app - - - hello\n<34>1 2026-11-18T10:00:01Z host app "
                            + "- - - again\n";

        assertThat(sample(text, LIMITS).mode()).isEqualTo(PartSampler.Mode.LINES);
    }

    @Test
    void aPartWhoseEveryLineIsTooLongIsReadFromItsStart() {
        // A one-line JSON object wrapping its records, say.
        final String text = "{\"Records\":[" + "{\"a\":\"" + "x".repeat(300) + "\"}]}";

        final PartSampler.Sample sample = sample(text, new PartSampler.Limits(100, 150));

        assertThat(sample.mode()).isEqualTo(PartSampler.Mode.HEAD);
        assertThat(sample.fellBack()).isNotNull();
        assertThat(sample.text()).isEqualTo(text.substring(0, 150));
        assertThat(String.join(" ", sample.said(100))).contains("every line read is that long");
    }

    @Test
    void anEmptyPartIsAnEmptySample() {
        final PartSampler.Sample sample = sample("", LIMITS);

        assertThat(sample.text()).isEmpty();
        assertThat(sample.whole()).isTrue();
    }

    @Test
    void aByteOrderMarkIsPassedOver() {
        assertThat(sample("﻿[{\"a\":1}]", LIMITS).text()).isEqualTo("[{\"a\":1}]");
        assertThat(sample("﻿a=1\n", LIMITS).text()).isEqualTo("a=1\n");
    }

    // A JSON array of `count` events, those at the indexes given 150,000 characters long.
    static String events(final int count, final int... big) {
        return IntStream.range(0, count)
                .mapToObj(i -> IntStream.of(big).anyMatch(b -> b == i)
                        ? "{\"n\":" + i + ",\"message\":\"" + "m".repeat(150_000) + "\"}"
                        : "{\"n\":" + i + ",\"message\":\"short\"}")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static PartSampler.Sample sample(final String text, final PartSampler.Limits limits) {
        try {
            return PartSampler.sample(() -> new StringReader(text), limits, () -> false);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
