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

/// The longest line and record of text read through once, as a Data Splitter's buffer must hold them.
class TestRecordLengths {

    @Test
    void theLongestLineIsFoundWhereverItIs() throws IOException {
        // Seen: a sample of a stream's start held records of about 52,000 characters, and one of over 105,000
        // further in was lost.
        final String text = "a,b\n" + "1,2\n".repeat(1000) + "x".repeat(150_000) + "\n" + "3,4\n";

        final RecordLengths.Measured measured = measure(text, null);

        assertThat(measured.longestLine()).isEqualTo(new RecordLengths.Span(150_000, 1002));
        assertThat(measured.longestRecord()).isNull();
        assertThat(measured.longest()).isEqualTo(150_000);
        assertThat(measured.lines()).isEqualTo(1003);
        assertThat(measured.chars()).isEqualTo(text.length());
        assertThat(measured.complete()).isTrue();
    }

    @Test
    void aQuotedValueHoldingNewLinesKeepsItsRecordWhole() throws IOException {
        // A record is as long as all its lines: its buffer must hold them together.
        final String text = "id,note\n1,\"one\nline two\nline three\"\n2,short\n";

        final RecordLengths.Measured measured = measure(text, '"');

        assertThat(measured.longestLine().chars()).isEqualTo("line three\"".length());
        assertThat(measured.longestRecord()).isEqualTo(new RecordLengths.Span(
                "1,\"one\nline two\nline three\"\n".length(), 2));
        assertThat(measured.recordsOverLines()).isEqualTo(1);
        assertThat(measured.longest()).isEqualTo(measured.longestRecord().chars());
        // The same as the dry run measures the sample.
        assertThat(measured.longestRecord().chars()).isEqualTo(SplitterDryRun.quotedLines(text, "\"").stream()
                .mapToInt(record -> record.length() + 1).max().orElseThrow());
    }

    @Test
    void doubledQuotesInsideAValueDoNotOpenARecord() throws IOException {
        final String text = "1,\"say \"\"hi\"\"\",x\n2,\"plain\",y\n";

        final RecordLengths.Measured measured = measure(text, '"');

        assertThat(measured.recordsOverLines()).isZero();
        assertThat(measured.longestRecord().line()).isEqualTo(1);
    }

    @Test
    void anUnclosedQuoteRunsToTheEndAndALastLineWithoutANewLineCounts() throws IOException {
        final String text = "1,\"open\n2,b\n3,c";

        final RecordLengths.Measured measured = measure(text, '"');

        assertThat(measured.longestRecord()).isEqualTo(new RecordLengths.Span(text.length(), 1));
        assertThat(measured.lines()).isEqualTo(3);
        assertThat(measured.longestLine()).isEqualTo(new RecordLengths.Span(7, 1));
    }

    @Test
    void blankLinesBetweenRecordsAndAByteOrderMarkAreNotPartOfARecord() throws IOException {
        final String text = "﻿abc\n\n  \nde\n";

        final RecordLengths.Measured measured = measure(text, '"');

        assertThat(measured.longestLine()).isEqualTo(new RecordLengths.Span(3, 1));
        assertThat(measured.longestRecord()).isEqualTo(new RecordLengths.Span(4, 1));
        assertThat(measured.chars()).isEqualTo(text.length() - 1);
    }

    @Test
    void emptyTextHasNothingToMeasure() throws IOException {
        final RecordLengths.Measured measured = measure("", '"');

        assertThat(measured.lines()).isZero();
        assertThat(measured.longest()).isZero();
        assertThat(measured.longestLine().line()).isZero();
    }

    @Test
    void whenTimeIsUpWhatWasReadSoFarIsGiven() throws IOException {
        final String text = "x".repeat(100_000) + "\n" + "y".repeat(200_000) + "\n";
        final AtomicInteger looks = new AtomicInteger();

        final RecordLengths.Measured measured = RecordLengths.measure(new StringReader(text), null,
                () -> looks.incrementAndGet() >= 1);

        assertThat(measured.complete()).isFalse();
        assertThat(measured.chars()).isLessThan(text.length());
        assertThat(measured.longest()).isLessThan(200_000);
    }

    private static RecordLengths.Measured measure(final String text, final Character quote) throws IOException {
        return RecordLengths.measure(new StringReader(text), quote, () -> false);
    }
}
