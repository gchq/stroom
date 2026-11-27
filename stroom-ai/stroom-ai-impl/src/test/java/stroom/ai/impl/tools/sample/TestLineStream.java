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

import static org.assertj.core.api.Assertions.assertThat;

/// The lines of one-record-a-line text read for a sample: kept exactly as they are, a line too long to sample left
/// out (not cut), and reading going on past it.
class TestLineStream {

    @Test
    void linesAreKeptAsTheyAreWithTheirLineEndsAndBlankLines() throws IOException {
        final String text = "a=1\r\n\nb=2\n   c=3\n";

        final LineStream.Read read = LineStream.read(new StringReader(text), 100, 1000, () -> false);

        assertThat(read.text()).isEqualTo(text);
        assertThat(read.lines()).isEqualTo(3);
        assertThat(read.ended()).isTrue();
        assertThat(read.skipped()).isZero();
    }

    @Test
    void aLineLongerThanTheMostIsLeftOutAndReadingGoesOn() throws IOException {
        final String big = "{\"message\":\"" + "x".repeat(500) + "\"}";

        final LineStream.Read read = LineStream.read(new StringReader("{\"a\":1}\n" + big + "\n{\"a\":2}\n"), 100,
                1000, () -> false);

        assertThat(read.text()).isEqualTo("{\"a\":1}\n{\"a\":2}\n");
        assertThat(read.skipped()).isEqualTo(1);
        assertThat(read.largestSkipped()).isEqualTo(big.length());
        assertThat(read.longestSkippedText()).isEqualTo(500);
        assertThat(read.longestSkippedPath()).isEqualTo("message");
        assertThat(read.seen()).isEqualTo(3);
    }

    @Test
    void aLastLineWithoutALineEndIsKept() throws IOException {
        final LineStream.Read read = LineStream.read(new StringReader("a\nb"), 100, 1000, () -> false);

        assertThat(read.text()).isEqualTo("a\nb");
        assertThat(read.lines()).isEqualTo(2);
    }

    @Test
    void aLastLineTooLongWithoutALineEndIsLeftOut() throws IOException {
        final LineStream.Read read = LineStream.read(new StringReader("a\n" + "b".repeat(200)), 100, 1000,
                () -> false);

        assertThat(read.text()).isEqualTo("a\n");
        assertThat(read.skipped()).isEqualTo(1);
    }

    @Test
    void aLineThatDoesntFitTheBudgetIsLeftForALaterReadNotCut() throws IOException {
        final LineStream.Read read = LineStream.read(new StringReader("aaaa\nbbbb\ncccc\n"), 100, 12, () -> false);

        assertThat(read.text()).isEqualTo("aaaa\nbbbb\n");
        assertThat(read.full()).isTrue();
        assertThat(read.ended()).isFalse();
    }

    @Test
    void aLineOfExactlyTheMostIsKept() throws IOException {
        final LineStream.Read read = LineStream.read(new StringReader("x".repeat(100) + "\r\n"), 100, 1000,
                () -> false);

        assertThat(read.skipped()).isZero();
        assertThat(read.lines()).isEqualTo(1);
    }

    @Test
    void emptyTextHasNoLines() throws IOException {
        final LineStream.Read read = LineStream.read(new StringReader(""), 100, 1000, () -> false);

        assertThat(read.text()).isEmpty();
        assertThat(read.ended()).isTrue();
    }
}
