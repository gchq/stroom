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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestRecordSplitter {

    @Test
    void xmlFragmentsAreRecordsWithTheirOwnNamespace() {
        final String text = IntStream.range(0, 5)
                .mapToObj(n -> "<Event xmlns=\"urn:win\"><Id>" + n + "</Id></Event>")
                .reduce("", (a, b) -> a + b + "\n");

        final RecordSplitter.Chunk chunk = RecordSplitter.split(text, 1000, false);

        assertThat(chunk.format()).isEqualTo(RecordSplitter.XML_FRAGMENTS);
        assertThat(chunk.records()).hasSize(5);
        assertThat(chunk.records().get(2)).contains("urn:win").contains("<Id>2</Id>");
    }

    @Test
    void theChildrenOfAnXmlRootAreRecordsEvenWhenItIsCutOff() {
        final String text = "<?xml version=\"1.0\"?><records><r><a>1</a></r><r><a>2</a></r><r><a>3";

        final RecordSplitter.Chunk chunk = RecordSplitter.split(text, 1000, true);

        assertThat(chunk.format()).isEqualTo(RecordSplitter.XML);
        assertThat(chunk.records()).hasSize(2);
        assertThat(chunk.records().get(1)).contains("<a>2</a>");
    }

    @Test
    void theItemsOfAJsonArrayAreRecordsUpToACutOffEnd() {
        final RecordSplitter.Chunk chunk = RecordSplitter.split("[{\"a\": 1},\n {\"a\": 2},\n {\"a\": 3", 1000,
                true);

        assertThat(chunk.format()).isEqualTo(RecordSplitter.JSON_ARRAY);
        assertThat(chunk.records()).containsExactly("{\"a\": 1}", "{\"a\": 2}");
    }

    @Test
    void theArrayOneObjectWrapsTheRecordsInHoldsThem() {
        // As AWS CloudTrail writes them.
        final RecordSplitter.Chunk chunk = RecordSplitter.split(
                "{\"Records\": [{\"e\": 1}, {\"e\": 2}, {\"e\": 3}], \"n\": [1]}", 1000, false);

        assertThat(chunk.format()).isEqualTo(RecordSplitter.JSON_OBJECT);
        assertThat(chunk.records()).hasSize(3);
    }

    @Test
    void aDelimitedStreamsHeaderIsGivenApartFromItsRecords() {
        // Seen in e2e (evaluate_and_fix): the header line was dropped from the records and given nowhere.
        final RecordSplitter.Chunk chunk = RecordSplitter.split(
                "time,user,host\n2026-09-28T10:00:00,alice,ws01\n2026-09-28T10:05:00,bob,ws02\n", 1000, false);

        assertThat(chunk.format()).isEqualTo(RecordSplitter.DELIMITED);
        assertThat(chunk.header()).isEqualTo("time,user,host");
        assertThat(chunk.records()).containsExactly("2026-09-28T10:00:00,alice,ws01", "2026-09-28T10:05:00,bob,ws02");
    }

    @Test
    void jsonLinesAreRecordsALine() {
        final RecordSplitter.Chunk chunk = RecordSplitter.split("{\"a\":1}\n{\"a\":2}\n", 1000, false);

        assertThat(chunk.format()).isEqualTo(RecordSplitter.JSON_LINES);
        assertThat(chunk.records()).hasSize(2);
    }

    @Test
    void otherTextIsALineARecordWithACutOffLastLineDropped() {
        final RecordSplitter.Chunk chunk = RecordSplitter.split("one thing\nanother thing\nhalf a th", 1000, true);

        assertThat(chunk.format()).isEqualTo(RecordSplitter.UNKNOWN_TEXT);
        assertThat(chunk.records()).isEqualTo(List.of("one thing", "another thing"));
    }
}
