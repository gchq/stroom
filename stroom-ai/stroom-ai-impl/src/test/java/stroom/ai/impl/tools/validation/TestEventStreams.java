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

import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.StreamReader.Fetched;
import stroom.ai.impl.tools.ToolRefusal;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The Events of processed streams read by the tool itself. Ported from the Stroom MCP server's
/// `tests/test_validation.py` (`test_check_events_reads_the_events_streams_itself`): Qwen in VS Code read 50
/// processed events back 23 at a time, 47 s a read, to send them to check_events.
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
    void testNoMoreThanTheCapIsRead() {
        Mockito.when(streamReader.fetch(Mockito.eq(1L), Mockito.anyLong(), Mockito.isNull()))
                .thenReturn(fetched("Events", TestEventChecks.EVENT, 10));
        Mockito.when(streamReader.fetch(Mockito.eq(2L), Mockito.anyLong(), Mockito.isNull()))
                .thenReturn(fetched("Events", TestEventChecks.EVENT, 10));
        final EventStreams.Read read = eventStreams.read(List.of(1L, 2L), 4);
        assertThat(read.read()).isEqualTo(Map.of(1L, Map.of("checked", 4, "records", 10L)));
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

    private static Fetched fetched(final String type, final String data, final long total) {
        return new Fetched("FEED", type, data, true, total, List.of(), List.of());
    }
}
