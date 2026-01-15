/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.pipeline.xml.converter;


import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test that we can chunk up a input stream into records.
 */

class TestRecordReader extends StroomUnitTest {

    private RecordReader createTest(final String buffer, final String delimiter) {
        final StringReader stringReader = new StringReader(buffer);
        return new RecordReader(stringReader, delimiter);
    }

    @Test
    void testSimple() throws IOException {
        final RecordReader recordReader = createTest("line1\nline2\nline3", null);
        assertThat(recordReader.readRecord()).isEqualTo("line1");
        assertThat(recordReader.readRecord()).isEqualTo("line2");
        assertThat(recordReader.readRecord()).isEqualTo("line3");
        assertThat(recordReader.readRecord()).isNull();
    }

    @Test
    void testBlankLineBreak() throws IOException {
        final RecordReader recordReader = createTest("line1\nline2\n\nline3", "\n\n");
        assertThat(recordReader.readRecord()).isEqualTo("line1\nline2");
        assertThat(recordReader.readRecord()).isEqualTo("line3");
        assertThat(recordReader.readRecord()).isNull();
    }

    @Test
    void testBlankLineBreak2() throws IOException {
        final RecordReader recordReader = createTest("line1\nline2\n\nline3\n", "\n\n");
        assertThat(recordReader.readRecord()).isEqualTo("line1\nline2");
        assertThat(recordReader.readRecord()).isEqualTo("line3\n");
        assertThat(recordReader.readRecord()).isNull();
    }

    @Test
    void testBlankLineBreak3() throws IOException {
        final RecordReader recordReader = createTest("line1\nline2\n\nline3\n\n", "\n\n");
        assertThat(recordReader.readRecord()).isEqualTo("line1\nline2");
        assertThat(recordReader.readRecord()).isEqualTo("line3");
        assertThat(recordReader.readRecord()).isNull();
    }

    @Test
    void testBlankLineBreak4StartingWithBlankLine() throws IOException {
        final RecordReader recordReader = createTest("\nline1\nline2\n\nline3\n\n", "\n\n");
        assertThat(recordReader.readRecord()).isEqualTo("\nline1\nline2");
        assertThat(recordReader.readRecord()).isEqualTo("line3");
        assertThat(recordReader.readRecord()).isNull();
    }

    @Test
    void testUnixMultiLineFormat1() throws IOException {
        final RecordReader recordReader = createTest("----\nline1\nline2\n----\nline3\n----\n", "----\n");
        assertThat(recordReader.readRecord()).isEqualTo("line1\nline2\n");
        assertThat(recordReader.readRecord()).isEqualTo("line3\n");
        assertThat(recordReader.readRecord()).isNull();
    }

    @Test
    void testUnixMultiLineFormat2() throws IOException {
        final RecordReader recordReader = createTest("line1\nline2\n----\nline3\n----\n", "\n----\n");
        assertThat(recordReader.readRecord()).isEqualTo("line1\nline2");
        assertThat(recordReader.readRecord()).isEqualTo("line3");
        assertThat(recordReader.readRecord()).isNull();
    }

    @Test
    void testUnixMultiLineFormat3OddDelimiters() throws IOException {
        final RecordReader recordReader = createTest("line1\nline2\n----\nline3\n----\n----\n----\n", "----\n");
        assertThat(recordReader.readRecord()).isEqualTo("line1\nline2\n");
        assertThat(recordReader.readRecord()).isEqualTo("line3\n");
        assertThat(recordReader.readRecord()).isNull();
    }

}
