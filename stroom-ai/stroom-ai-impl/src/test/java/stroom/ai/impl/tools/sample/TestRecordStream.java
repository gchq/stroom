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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/// Records of one-record-a-line text read through to the ones wanted.
class TestRecordStream {

    private static final String CSV = "﻿action,user\r\n"
                                      + "allow,a\r\n"
                                      + "\r\n"
                                      + "allow,\"two\nlines\"\r\n"
                                      + " block,c\n"
                                      + "bypass,d";

    @Test
    void recordsAreNumberedAfterTheHeaderPassingOverBlankLines() throws IOException {
        final RecordStream.Read read = read(CSV, '"', true, 1, 2, 1000);

        assertThat(read.header()).isEqualTo("action,user");
        // A quoted value's new line is in its record; a record's own leading space is kept.
        assertThat(read.records()).containsExactly("allow,\"two\nlines\"", " block,c");
        assertThat(read.ended()).isFalse();
        assertThat(read.timedOut()).isFalse();
    }

    @Test
    void theLastRecordNeedsNoNewLineAndTheEndSaysHowManyThereAre() throws IOException {
        final RecordStream.Read read = read(CSV, '"', true, 3, 5, 1000);

        assertThat(read.records()).containsExactly("bypass,d");
        assertThat(read.ended()).isTrue();
        assertThat(read.seen()).isEqualTo(4);
        final RecordStream.Read past = read(CSV, '"', true, 9, 1, 1000);
        assertThat(past.records()).isEmpty();
        assertThat(past.seen()).isEqualTo(4);
    }

    @Test
    void withoutAQuoteEachLineIsARecordAndWithoutAHeaderTheFirstLineCounts() throws IOException {
        final RecordStream.Read read = read(CSV, null, false, 0, 4, 1000);

        assertThat(read.header()).isNull();
        assertThat(read.records()).containsExactly("action,user", "allow,a", "allow,\"two", "lines\"");
    }

    @Test
    void theBudgetCutsOnlyAFirstRecordAndLeavesTheRestForLater() throws IOException {
        final RecordStream.Read cut = read("a,b\n" + "x".repeat(50) + "\nnext\n", null, true, 0, 2, 10);
        assertThat(cut.records()).containsExactly("x".repeat(10));
        assertThat(cut.cut()).isTrue();

        final RecordStream.Read whole = read("a,b\n1234\n567890\n", null, true, 0, 2, 6);
        assertThat(whole.records()).containsExactly("1234");
        assertThat(whole.cut()).isTrue();
    }

    @Test
    void readingStopsWhenTimeIsUp() throws IOException {
        // More than one chunk, so time is asked after the first.
        final String text = "a\n".repeat(100_000);
        final AtomicInteger asked = new AtomicInteger();

        final RecordStream.Read read = RecordStream.read(new StringReader(text), null, false, 90_000, 1, 1000,
                () -> asked.incrementAndGet() > 0);

        assertThat(read.records()).isEmpty();
        assertThat(read.timedOut()).isTrue();
        assertThat(read.ended()).isFalse();
        assertThat(read.seen()).isLessThan(90_000);
    }

    private static RecordStream.Read read(final String text,
                                          final Character quote,
                                          final boolean header,
                                          final long first,
                                          final int count,
                                          final int budget) throws IOException {
        return RecordStream.read(new StringReader(text), quote, header, first, count, budget, () -> false);
    }
}
