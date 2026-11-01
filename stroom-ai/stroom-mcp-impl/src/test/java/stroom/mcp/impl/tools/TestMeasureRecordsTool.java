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

import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.StreamReader.PartLengths;
import stroom.ai.impl.tools.sample.RecordLengths.Measured;
import stroom.ai.impl.tools.sample.RecordLengths.Span;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// measure_records: the longest record of whole raw streams, and the Data Splitter buffer that holds it.
@SuppressWarnings("unchecked")
class TestMeasureRecordsTool {

    private final TranslationWorld world = new TranslationWorld();
    private final MeasureRecordsTool tool = new MeasureRecordsTool(world.streamReader);

    @Test
    void theLongestRecordOfEveryPartGivesTheBuffer() {
        // Asked for by the user: records of over 105,000 characters lost in a 159 MB stream, whose longest record
        // the agent couldn't find.
        Mockito.when(world.streamReader.recordLengths(73191L, null)).thenReturn(new PartLengths(List.of(
                new Measured(500, 2_000_000, new Span(52_000, 12), null, 0, true),
                new Measured(700, 9_000_000, new Span(131_000, 502), null, 0, true)), 2));

        final Map<String, Object> result = world.call(tool, Map.of("stream_ids", List.of(73191)));

        assertThat(result).containsEntry("longest", 131_000).containsEntry("buffer_size", 530_000)
                .containsEntry("complete", true).doesNotContainKey("not_measured");
        assertThat((List<Map<String, Object>>) result.get("streams")).singleElement().satisfies(stream -> {
            assertThat(stream).containsEntry("parts", 2).containsEntry("lines", 1200).containsEntry("chars",
                    11_000_000).doesNotContainKey("longest_record");
            assertThat(stream.get("longest_line")).isEqualTo(Map.of("chars", 131_000, "part", 1, "line", 502));
        });
        assertThat((String) result.get("hint")).startsWith("build_data_splitter with buffer_size=530000");
    }

    @Test
    void withAQuoteARecordIsItsLinesJoined() {
        Mockito.when(world.streamReader.recordLengths(7L, '"')).thenReturn(new PartLengths(List.of(
                new Measured(40, 90_000, new Span(9_000, 3), new Span(30_000, 2), 4, true)), 1));

        final Map<String, Object> result = world.call(tool, Map.of("stream_ids", List.of(7), "quote", "\""));

        assertThat(result).containsEntry("longest", 30_000).containsEntry("buffer_size", 120_000);
        assertThat((List<Map<String, Object>>) result.get("streams")).singleElement().satisfies(stream -> {
            assertThat(stream.get("longest_record")).isEqualTo(Map.of("chars", 30_000, "line", 2));
            assertThat(stream).containsEntry("records_over_lines", 4);
        });
    }

    @Test
    void recordsTheDefaultBufferHoldsSaySoAndNoBuffer() {
        Mockito.when(world.streamReader.recordLengths(7L, null)).thenReturn(new PartLengths(List.of(
                new Measured(3, 30, new Span(9_000, 1), null, 0, true)), 1));

        final Map<String, Object> result = world.call(tool, Map.of("stream_ids", List.of(7)));

        assertThat(result).doesNotContainKey("buffer_size");
        assertThat((String) result.get("hint")).contains("holds every record measured");
    }

    @Test
    void aStreamNotReadThroughStopsTheCallAndSaysSo() {
        Mockito.when(world.streamReader.recordLengths(7L, null)).thenReturn(new PartLengths(List.of(
                new Measured(3, 30, new Span(25_000, 1), null, 0, false)), 1));

        final Map<String, Object> result = world.call(tool, Map.of("stream_ids", List.of(7, 8)));

        assertThat(result).containsEntry("complete", false);
        assertThat((List<?>) result.get("streams")).hasSize(1);
        assertThat((String) result.get("not_measured")).contains("one a call");
        Mockito.verify(world.streamReader, Mockito.never()).recordLengths(8L, null);
    }

    @Test
    void aRecordOverHalfTheLargestBufferCantBeHeld() {
        Mockito.when(world.streamReader.recordLengths(7L, null)).thenReturn(new PartLengths(List.of(
                new Measured(1, 600_000_000, new Span(600_000_000, 1), null, 0, true)), 1));

        final Map<String, Object> result = world.call(tool, Map.of("stream_ids", List.of(7)));

        assertThat((String) result.get("hint")).contains("no Data Splitter buffer is sure to hold it");
    }

    @Test
    void streamsOutOfRangeAQuoteOfMoreThanOneCharacterAndAnUnreadableStreamAreRefused() {
        assertThat((String) world.call(tool, Map.of("stream_ids", List.of())).get("error"))
                .startsWith("Give 1 to 10 stream_ids");
        assertThat((String) world.call(tool, Map.of("stream_ids", List.of(7), "quote", "''")).get("error"))
                .startsWith("quote is one character");
        Mockito.when(world.streamReader.recordLengths(9L, null))
                .thenThrow(new StreamReader.StreamReadException("Stroom could not read stream 9: gone"));
        assertThat((String) world.call(tool, Map.of("stream_ids", List.of(9))).get("error"))
                .startsWith("Stroom could not read stream 9");
    }

    @Test
    void itIsReadOnly() {
        assertThat(tool.isReadOnly()).isTrue();
        assertThat(tool.definition().name()).isEqualTo(MeasureRecordsTool.NAME);
    }
}
