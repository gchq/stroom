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

package stroom.ai.impl.tools;

import stroom.data.shared.StreamTypeNames;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestStreamReader {

    static final String PREFIXED = "<?xml version=\"1.1\" encoding=\"UTF-8\"?><evt:Events "
                                   + "xmlns:evt=\"event-logging:3\" Version=\"4.1.0\"><evt:Event><evt:EventDetail>"
                                   + "<evt:TypeId>x</evt:TypeId></evt:EventDetail></evt:Event></Events>";
    static final String EVENT = "<?xml version=\"1.1\" encoding=\"UTF-8\"?><Events xmlns=\"event-logging:3\"><Event>"
                                + "<EventDetail><TypeId>x</TypeId></EventDetail></Event></Events>";

    private final StreamWorld world = new StreamWorld();
    private final StreamReader reader = world.streamReader();

    @Test
    void aPrefixedRootIsClosedByItsOwnName() {
        // Stroom closes a segmented record's root by its local name, so an XSLT that writes <evt:Events> reads back
        // as <evt:Events>...</Events>, which nothing could parse.
        final String fixed = StreamReader.rootClosed(PREFIXED);

        assertThat(fixed).endsWith("</evt:Events>");
        assertThat(parses(fixed)).isTrue();
        final String plain = "<Events xmlns=\"event-logging:3\"><Event/></Events>";
        assertThat(StreamReader.rootClosed(plain)).isEqualTo(plain);
        assertThat(StreamReader.rootClosed("raw text, not xml")).isEqualTo("raw text, not xml");
    }

    @Test
    void segmentedRecordsAreReadWellFormed() {
        world.addSegmented(7, StreamTypeNames.EVENTS, null, null, List.of(PREFIXED));

        assertThat(reader.readRecords(7, 0, 5, null, 100_000).records())
                .containsExactly(StreamReader.rootClosed(PREFIXED));
    }

    @Test
    void rawDataIsReturnedAsItIs() {
        world.addRaw(8, "a,b\n1,2\n</Events>");

        assertThat(reader.readRecords(8, 0, 1, null, 100_000).records()).containsExactly("a,b\n1,2\n</Events>");
    }

    @Test
    void aRecordTheSizeLimitWouldCutIsLeftForTheNextPage() {
        // Seen: 100 events of a large stream read at once, the last cut mid-attribute and validated as "attributes
        // construct error". Whole records only.
        world.addSegmented(7, StreamTypeNames.EVENTS, null, null, List.of(EVENT, EVENT, EVENT, EVENT, EVENT));

        final List<String> records = reader.readRecords(7, 0, 5, null, EVENT.length() * 3 - 5).records();

        assertThat(records).hasSize(2).allMatch(TestStreamReader::parses);
        // A single record larger than the limit is still returned (cut).
        assertThat(reader.readRecords(7, 0, 5, null, 40).records()).hasSize(1);
    }

    @Test
    void aStreamStroomCantReadSaysWhy() {
        world.addSegmented(7, StreamTypeNames.EVENTS, null, null, List.of(EVENT));
        world.errors.put(7L, List.of("You cannot view locked streams."));

        assertThatThrownBy(() -> reader.readRecords(7, 0, 1, null, 1000))
                .hasMessage("Stroom could not read stream 7: You cannot view locked streams.");
    }

    @Test
    void aRawStreamIsReadWholeAsStoredEmojiAndAll() {
        // Seen in production: a survey of tweets read a clean stream as records with <data ne="..."> in them, as
        // each page after an emoji started early by one character per emoji. Read through the bytes, it can't.
        final String tweets = "<tweets>" + IntStream.range(0, 400)
                .mapToObj(n -> "<tweet><data name=\"id\" value=\"" + n + "\" /><data name=\"text\" value=\"look "
                               + "👀".repeat(n % 4) + " ❤️ şike " + n + "\" /></tweet>")
                .collect(Collectors.joining()) + "</tweets>\n";
        world.addRaw(1, tweets);

        final StreamReader.RawText text = reader.rawText(1, 10 * tweets.length());

        assertThat(text.text()).isEqualTo(tweets);
        assertThat(text.truncated()).isFalse();
    }

    @Test
    void aRawStreamReadUpToALimitIsCutAtALineEnd() {
        final String text = IntStream.range(0, 100).mapToObj(n -> "line " + n + "\n").collect(Collectors.joining());
        world.addRaw(7, text);

        final StreamReader.RawText head = reader.rawText(7, 300);

        assertThat(head.truncated()).isTrue();
        assertThat(text).startsWith(head.text());
        assertThat(head.text()).endsWith("\n").hasSizeLessThanOrEqualTo(300);
        assertThat(reader.rawText(7, 10_000)).isEqualTo(new StreamReader.RawText(text, false));
    }

    @Test
    void aSampleWithNoFinalNewlineKeepsItsLastLine() {
        // Seen: eval case 16's JSON array, cut back to its last line end, failed to parse.
        final String array = "[{\"a\": 1},\n {\"a\": 2}]";
        world.addRaw(7, array);

        assertThat(reader.rawText(7, 10_000)).isEqualTo(new StreamReader.RawText(array, false));
    }

    @Test
    void aStreamStroomWontShowAsTextIsReadThroughItsBytes() {
        world.addRaw(7, "");
        world.bytes.put(7L, TestStreamCharsets.MIXED);

        final StreamReader.RawText text = reader.rawText(7, 1_000_000);

        assertThat(text.text()).contains("latin1 caf�").contains("cp1252 �q�");
        assertThat(text.truncated()).isFalse();
        assertThat(reader.charsetReport(7).get("undecodable_bytes")).isEqualTo(3);
    }

    @Test
    void theFeedsOwnEncodingDecodesTheBytes() {
        final String text = "café “q”\n".repeat(50);
        world.addRaw(10, "");
        world.bytes.put(10L, text.getBytes(Charset.forName("windows-1252")));
        world.encodings.put(StreamWorld.FEED, "windows-1252");

        assertThat(reader.rawText(10, 100_000).text()).isEqualTo(text);
        assertThat(reader.charsetReport(10)).isNull();
    }

    @Test
    void aRawStreamsTextIsKeptAWhileForEachUser() {
        final AtomicLong now = new AtomicLong(1_000);
        final StreamReader cached = new StreamReader(() -> world.metaService, () -> world.dataService,
                () -> world.store, () -> world.feedProperties, () -> world.securityContext, now::get);
        world.addRaw(7, "a\nb\n");

        cached.cachedRawText(7, 100);
        cached.cachedRawText(7, 100);
        // Seen: a hundred attempts at a mapping, each reading the same sample from Stroom again.
        Mockito.verify(world.store, Mockito.times(1)).openSource(7L);

        now.addAndGet(StreamReader.SAMPLE_TTL_MS + 1);
        cached.cachedRawText(7, 100);
        Mockito.verify(world.store, Mockito.times(2)).openSource(7L);
    }

    @Test
    void aRawStreamHeldAsOneRecordIsSplitIntoItsRecords() {
        // Seen in production: reads of records 200, 450, 700 and 950 of a 1,000-fragment sample each returned its
        // first four, as Stroom holds a raw stream as one record.
        final List<String> lines = IntStream.range(0, 1000)
                .mapToObj(n -> "<Event xmlns=\"urn:win\"><Id>" + n + "</Id></Event>")
                .toList();
        world.addRaw(7, String.join("\n", lines) + "\n");

        final Map<String, Object> read = reader.splitRaw(7, 450, 2, 60_000);

        // As in the stream.
        assertThat(read.get("records")).isEqualTo(lines.subList(450, 452));
        assertThat(read.get("total_records")).isEqualTo(1000);
        assertThat((String) read.get("records_are")).contains("xml fragments");
        final Map<String, Object> past = reader.splitRaw(7, 1000, 2, 60_000);
        assertThat(past.get("records")).isEqualTo(List.of());
        assertThat((String) past.get("hint")).contains("past the end");
    }

    @Test
    void aRawStreamReadOnlyInPartSaysSo() {
        final List<String> lines = IntStream.range(0, 30_000)
                .mapToObj(n -> "<Event xmlns=\"urn:win\"><Id>" + n + "</Id></Event>")
                .toList();
        world.addRaw(7, String.join("\n", lines) + "\n");

        final Map<String, Object> partly = reader.splitRaw(7, 29_000, 2, 60_000);

        assertThat(partly.get("records")).isEqualTo(List.of());
        assertThat(partly.get("total_records")).isNull();
        assertThat((String) partly.get("hint")).contains("characters read");
    }

    @Test
    void aStreamOfOneRecordIsNotSplit() {
        world.addRaw(7, "just one line of text");

        assertThat(reader.splitRaw(7, 0, 5, 60_000)).isNull();
    }

    @Test
    void aDelimitedStreamsHeaderComesBackWithItsRecords() {
        // Seen in e2e (evaluate_and_fix): the header line was dropped from the records and given nowhere.
        world.addRaw(1, "time,user,host\n2026-09-28T10:00:00,alice,ws01\n2026-09-28T10:05:00,bob,ws02\n");

        final Map<String, Object> out = reader.splitRaw(1, 0, 5, 10_000);

        assertThat(out.get("header")).isEqualTo("time,user,host");
        assertThat(out.get("records")).isEqualTo(List.of("2026-09-28T10:00:00,alice,ws01",
                "2026-09-28T10:05:00,bob,ws02"));
        assertThat((String) out.get("records_are")).contains("is header");
    }

    @Test
    void theAttributesARawStreamCarriesAreItsReceiptHeaders() {
        world.addRaw(7, "x");
        world.headers.put(7L, "Feed:ACME\nRemoteAddress: 10.0.0.1\nnot a header\n");

        final Map<String, Object> attributes = reader.streamAttributes(7);

        assertThat(attributes.get("attributes")).isEqualTo(Map.of("Feed", "ACME", "RemoteAddress", "10.0.0.1"));
        assertThat(attributes.get("stream_id")).isEqualTo(7L);
    }

    @Test
    void aStreamThatIsntThereIsSaid() {
        assertThatThrownBy(() -> reader.meta(99)).hasMessage("No stream with id 99");
    }

    private static boolean parses(final String xml) {
        try {
            StreamSummaries.documentBuilder().parse(new InputSource(new StringReader(xml)));
            return true;
        } catch (final Exception e) {
            return false;
        }
    }
}
