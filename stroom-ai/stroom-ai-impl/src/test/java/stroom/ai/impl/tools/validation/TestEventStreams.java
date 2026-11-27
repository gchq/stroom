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

package stroom.ai.impl.tools.validation;

import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.streams.StreamReader;
import stroom.ai.impl.tools.streams.StreamReader.Fetched;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The Events of processed streams read by the tool itself: Qwen in VS Code read 50 processed events back 23 at a
/// time, 47 s a read, to send them to check_events.
class TestEventStreams {

    private final StreamReader streamReader = Mockito.mock(StreamReader.class);
    private final EventStreams eventStreams = new EventStreams(streamReader);

    @Test
    void testTheEventsStreamsAreReadAndMerged() {
        final String bad = TestEventChecks.EVENT.replace("2026-09-28T10:00:00.000Z", "not-a-date");
        final List<String> records = List.of(TestEventChecks.EVENT, TestEventChecks.EVENT, bad);
        for (int i = 0; i < records.size(); i++) {
            Mockito.when(streamReader.fetch(42L, i, null)).thenReturn(fetched("Events", records.get(i),
                    records.size()));
        }
        final EventStreams.Read read = eventStreams.read(List.of(42L), 1000);
        assertThat(read.read()).isEqualTo(Map.of(42L, Map.of("checked", 3, "records", 3L)));
        final EventChecks checks = new EventChecks(new HeldSchemasWorld()
                .hold("file://event-logging-v9.9.9.xsd", "event-logging:3", TestEventChecks.XSD)
                .heldSchemas());
        final Map<String, Object> schema = checks.validateEvents(read.eventsXml(), null);
        assertThat(schema.get("valid")).isEqualTo(false);
        assertThat(schema.get("error_count")).isEqualTo(1);
        assertThat(EventChecks.checkEventQuality(read.eventsXml()).get("events_checked")).isEqualTo(3);
    }

    @Test
    void testAnXml11StreamHoldingAControlCharacterReferenceValidates() {
        // Seen in chat 181: Stroom wrote a Teams message's form feed as &#xc; in its XML 1.1 Events stream, and
        // check_events refused the stream as not well-formed, reading the merged events as XML 1.0.
        final List<String> records = List.of(TestEventChecks.EVENT, withFormFeed(TestEventChecks.EVENT));
        for (int i = 0; i < records.size(); i++) {
            Mockito.when(streamReader.fetch(42L, i, null)).thenReturn(fetched("Events", records.get(i),
                    records.size()));
        }

        final EventStreams.Read read = eventStreams.read(List.of(42L), 1000);

        assertThat(read.eventsXml()).startsWith("<?xml version=\"1.1\"");
        assertThat(read.eventsXml()).containsAnyOf("&#xc;", "&#12;");
        final EventChecks checks = new EventChecks(new HeldSchemasWorld()
                .hold("file://event-logging-v9.9.9.xsd", "event-logging:3", TestEventChecks.XSD)
                .heldSchemas());
        final Map<String, Object> schema = checks.validateEvents(read.eventsXml(), null);
        assertThat(schema.get("valid")).isEqualTo(true);
        final Map<String, Object> quality = EventChecks.checkEventQuality(read.eventsXml());
        assertThat(quality.get("events_checked")).isEqualTo(2);
        assertThat(quality.get("ok")).isEqualTo(true);
    }

    @Test
    void testAnXml10RecordHoldingAControlCharacterReferenceIsRefusedSayingWhere() {
        // A form feed reference really is not well-formed in XML 1.0: refused, naming the stream, the event, the
        // column and the text around it, not only "line 1".
        final String xml10 = TestEventChecks.EVENT.replace("version=\"1.1\"", "version=\"1.0\"");
        final List<String> records = List.of(xml10, withFormFeed(xml10));
        for (int i = 0; i < records.size(); i++) {
            Mockito.when(streamReader.fetch(42L, i, null)).thenReturn(fetched("Events", records.get(i),
                    records.size()));
        }

        assertThatThrownBy(() -> eventStreams.read(List.of(42L), 1000))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageStartingWith("Event 2 of stream 42 is not well-formed XML: Character reference "
                                        + "\"&#xc\" is an invalid XML character.")
                .hasMessageContaining(", column ")
                .hasMessageContaining("<Generator>g&#xc;")
                .hasMessageContaining("&#xc; (U+000C, a control character) is a character reference XML 1.0 "
                                      + "forbids and XML 1.1 allows")
                .hasMessageContaining("strip_control");
    }

    @Test
    void testEventsXmlIsReadAsTheVersionItDeclares() {
        final EventChecks checks = new EventChecks(new HeldSchemasWorld()
                .hold("file://event-logging-v9.9.9.xsd", "event-logging:3", TestEventChecks.XSD)
                .heldSchemas());
        final String twoEvents = TestEventChecks.EVENT.replace("</Event></Events>", "</Event>"
                + TestEventChecks.EVENT.substring(TestEventChecks.EVENT.indexOf("<Event>"))
                        .replace("<Generator>g</Generator>", "<Generator>g&#xc;h</Generator>"));

        // Sent as Stroom writes it, XML 1.1, it is valid.
        assertThat(checks.validateEvents(twoEvents, null).get("valid")).isEqualTo(true);
        assertThat(EventChecks.checkEventQuality(twoEvents).get("ok")).isEqualTo(true);
        // As XML 1.0 it is refused, saying which event and where.
        final String xml10 = twoEvents.replace("version=\"1.1\"", "version=\"1.0\"");
        assertThatThrownBy(() -> checks.validateEvents(xml10, null))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageStartingWith("Events XML is not well-formed XML: Character reference \"&#xc\"")
                .hasMessageContaining(", in Event 2; near \"")
                .hasMessageContaining("<Generator>g&#xc;");
    }

    @Test
    void testNoMoreThanTheCapIsRead() {
        Mockito.when(streamReader.fetch(Mockito.eq(1L), Mockito.anyLong(), Mockito.isNull()))
                .thenReturn(fetched("Events", TestEventChecks.EVENT, 10));
        Mockito.when(streamReader.fetch(Mockito.eq(2L), Mockito.anyLong(), Mockito.isNull()))
                .thenReturn(fetched("Events", TestEventChecks.EVENT, 10));
        final EventStreams.Read read = eventStreams.read(List.of(1L, 2L), 4);
        assertThat(read.read()).isEqualTo(Map.of(1L, Map.of("checked", 4, "records", 10L, "spread",
                "the events checked are a block at the stream's start and the rest spread over it")));
        // A block at its head, the rest spread over the stream, not its first four: seen in testing, the first 500 of
        // 6,000 events were almost all of one kind.
        for (final long index : List.of(0L, 1L, 4L, 7L)) {
            Mockito.verify(streamReader).fetch(1L, index, null);
        }
    }

    @Test
    void testAStreamThatIsNotEventsIsRefused() {
        Mockito.when(streamReader.fetch(7L, 0, null)).thenReturn(fetched("Raw Events", "a,b", 1));
        assertThatThrownBy(() -> eventStreams.read(List.of(7L), 10))
                .isInstanceOf(ToolRefusal.class)
                .hasMessage("Stream 7 is Raw Events, not Events: give the Events streams processing wrote "
                            + "(wait_for_processing lists them)");
    }

    @Test
    void testAStreamStroomCantReadSaysWhy() {
        Mockito.when(streamReader.fetch(8L, 0, null)).thenReturn(new Fetched("F", "Events", null, true, 1L,
                List.of(), List.of("Unable to decode")));
        assertThatThrownBy(() -> eventStreams.read(List.of(8L), 10))
                .hasMessage("Stroom could not read stream 8: Unable to decode");
    }

    // The record with a form feed, as Stroom writes it, in its Generator.
    private static String withFormFeed(final String record) {
        return record.replace("<Generator>g</Generator>", "<Generator>g&#xc;h</Generator>");
    }

    private static Fetched fetched(final String type, final String data, final long total) {
        return new Fetched("FEED", type, data, true, total, List.of(), List.of());
    }
}
