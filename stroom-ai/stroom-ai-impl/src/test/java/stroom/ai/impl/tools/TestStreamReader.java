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
import stroom.security.shared.AppPermission;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.xml.sax.InputSource;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
    void aStreamWhoseChildTypesHoldANullIsRead() {
        // Seen in an integration test: Stroom's data viewer gave a processed Events stream's child types with a
        // null among them, and check_events failed on every Events stream.
        world.addSegmented(7, StreamTypeNames.EVENTS, null, null, List.of(EVENT));
        world.childTypes.add(null);

        final StreamReader.Fetched fetched = reader.fetch(7, 0, null);

        assertThat(fetched.availableChildTypes()).containsExactly(StreamTypeNames.META);
        assertThat(reader.readRecords(7, 0, 1, null, 100_000).records()).containsExactly(EVENT);
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
    void theHeadOfAStreamStroomWontShowAsTextIsReadThroughItsBytesAndCut() {
        // As the Stroom MCP server's read_head read such a stream through its HEX view: the head is the start of
        // the stream decoded as a pipeline decodes it, said to be cut, and what was replaced is remembered.
        world.addRaw(9, "");
        final ByteArrayOutputStream lines = new ByteArrayOutputStream();
        for (int i = 0; i < 20; i++) {
            lines.writeBytes(TestStreamCharsets.MIXED);
            lines.write('\n');
        }
        world.bytes.put(9L, lines.toByteArray());

        final StreamReader.RawHead head = reader.readHead(9, 0, 2_000);

        assertThat(head.truncated()).isTrue();
        assertThat(head.text()).hasSizeLessThanOrEqualTo(2_000);
        assertThat(StreamCharsets.decode(lines.toByteArray(), "UTF-8", true).text()).startsWith(head.text());
        assertThat(head.parts()).isEqualTo(1);
        assertThat(reader.charsetReport(9)).isNotNull();
    }

    @Test
    void windowsAreReadSpreadToAPartsEndEachStartingOnALineItsNumberGiven() {
        // Seen in testing: only a 160 MB part's first 5,000,000 characters were surveyed, so kinds of event further
        // in were found only once the feed was processed.
        final StringBuilder text = new StringBuilder("n,kind\n");
        for (int n = 0; n < 2_000; n++) {
            text.append(n).append(',').append(n == 1_990 ? "rare" : "common").append('\n');
        }
        world.addRaw(12, "");
        world.bytes.put(12L, text.toString().getBytes(StandardCharsets.UTF_8));

        final List<StreamReader.Window> windows = reader.readWindows(12, 0, 2_000, 4, 300);

        assertThat(windows).hasSize(4);
        for (final StreamReader.Window window : windows) {
            assertThat(window.fromByte()).isGreaterThanOrEqualTo(2_000);
            assertThat(window.text()).as("whole lines").endsWith("\n").hasSizeLessThanOrEqualTo(300);
            // The line it starts with is the record its number says (line 0 is the header).
            final String firstLine = window.text().substring(0, window.text().indexOf('\n'));
            assertThat(firstLine).startsWith((window.firstLine() - 1) + ",");
        }
        assertThat(windows.getLast().text()).as("the last is the part's tail").endsWith("1999,common\n")
                .contains("1990,rare");
        assertThat(windows).extracting(StreamReader.Window::fromByte).isSorted();
    }

    @Test
    void noWindowsAreReadOfAPartNoLongerThanItsHeadOrWhoseLineEndIsNotOneByte() {
        world.addRaw(13, "");
        world.bytes.put(13L, "a\nb\n".getBytes(StandardCharsets.UTF_8));
        assertThat(reader.readWindows(13, 0, 4, 4, 100)).isEmpty();

        world.addRaw(14, "");
        world.bytes.put(14L, "a,b\n1,2\n".repeat(100).getBytes(StandardCharsets.UTF_16LE));
        world.encodings.put(StreamWorld.FEED, "UTF-16LE");
        assertThat(reader.readWindows(14, 0, 10, 4, 100)).isEmpty();
    }

    @Test
    void eachPartOfAStreamOfSeveralIsReadAsItsOwnHead() {
        // A stream of several files received at once has a part each: a survey reads each part's head.
        world.addRaw(11, "");
        world.parts.put(11L, List.of("first,part\n1,2\n".getBytes(Charset.forName("UTF-8")),
                "second,part\n3,4\n".getBytes(Charset.forName("UTF-8"))));

        final StreamReader.RawHead second = reader.readHead(11, 1, 1_000);

        assertThat(second).isEqualTo(new StreamReader.RawHead("second,part\n3,4\n", false, 2));
        assertThat(reader.rawText(11, 1_000).text()).isEqualTo("first,part\n1,2\n");
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
    void aTextOverTheCacheCapIsNotKeptAndKeepsTheOthers() {
        // Before, one text over the cap flushed every other and was kept anyway, over the cap.
        final StreamReader cached = new StreamReader(() -> world.metaService, () -> world.dataService,
                () -> world.store, () -> world.feedProperties, () -> world.securityContext, () -> 1_000L, 10);
        world.addRaw(7, "a\nb\n");
        world.addRaw(8, "0123456789abcdef\n");

        cached.cachedRawText(7, 100);
        assertThat(cached.cachedRawText(8, 100).text()).isEqualTo("0123456789abcdef\n");
        cached.cachedRawText(7, 100);
        cached.cachedRawText(8, 100);

        // 7 is still kept, 8 never was.
        Mockito.verify(world.store, Mockito.times(1)).openSource(7L);
        Mockito.verify(world.store, Mockito.times(2)).openSource(8L);
    }

    @Test
    void noStreamsTextIsReadWithoutTheViewDataPermission() throws Exception {
        // Before, the stream store was opened directly: it checks the feed, not View Data, which the data viewer
        // needs. Seen in review: Error streams and a build's sample head were read without it.
        world.addRaw(7, "a\nb\n");
        assertThat(reader.cachedRawText(7, 100).text()).isEqualTo("a\nb\n");
        Mockito.when(world.securityContext.hasAppPermission(AppPermission.VIEW_DATA_PERMISSION)).thenReturn(false);

        assertThatThrownBy(() -> reader.readHead(7, 0, 100))
                .isInstanceOf(StreamReader.StreamReadException.class)
                .hasMessage("Stream 7's text can't be read: you don't have permission to view data (View Data), "
                            + "which reading a stream's text needs, as the data viewer does.");
        // Not from the cache either: the permission may have been taken away since.
        assertThatThrownBy(() -> reader.cachedRawText(7, 100)).isInstanceOf(StreamReader.StreamReadException.class);
        assertThatThrownBy(() -> reader.errorStreamText(7, 100)).isInstanceOf(StreamReader.StreamReadException.class);
        assertThatThrownBy(() -> StreamReader.errorStreamText(world.store, () -> false, 7, 100))
                .isInstanceOf(StreamReader.StreamReadException.class);
        Mockito.verify(world.store, Mockito.times(1)).openSource(7L);
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
