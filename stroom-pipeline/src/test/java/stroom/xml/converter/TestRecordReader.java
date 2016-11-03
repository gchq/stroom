/*
 * Copyright 2016 Crown Copyright
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

package stroom.xml.converter;

import java.io.IOException;
import java.io.StringReader;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import stroom.util.test.StroomUnitTest;
import stroom.util.test.StroomJUnit4ClassRunner;

/**
 * Test that we can chunk up a input stream into records.
 */
@RunWith(StroomJUnit4ClassRunner.class)
public class TestRecordReader extends StroomUnitTest {
    private RecordReader createTest(final String buffer, final String delimiter) {
        final StringReader stringReader = new StringReader(buffer);
        return new RecordReader(stringReader, delimiter);
    }

    @Test
    public void testSimple() throws IOException {
        final RecordReader recordReader = createTest("line1\nline2\nline3", null);
        Assert.assertEquals("line1", recordReader.readRecord());
        Assert.assertEquals("line2", recordReader.readRecord());
        Assert.assertEquals("line3", recordReader.readRecord());
        Assert.assertNull(recordReader.readRecord());
    }

    @Test
    public void testBlankLineBreak() throws IOException {
        final RecordReader recordReader = createTest("line1\nline2\n\nline3", "\n\n");
        Assert.assertEquals("line1\nline2", recordReader.readRecord());
        Assert.assertEquals("line3", recordReader.readRecord());
        Assert.assertNull(recordReader.readRecord());
    }

    @Test
    public void testBlankLineBreak2() throws IOException {
        final RecordReader recordReader = createTest("line1\nline2\n\nline3\n", "\n\n");
        Assert.assertEquals("line1\nline2", recordReader.readRecord());
        Assert.assertEquals("line3\n", recordReader.readRecord());
        Assert.assertNull(recordReader.readRecord());
    }

    @Test
    public void testBlankLineBreak3() throws IOException {
        final RecordReader recordReader = createTest("line1\nline2\n\nline3\n\n", "\n\n");
        Assert.assertEquals("line1\nline2", recordReader.readRecord());
        Assert.assertEquals("line3", recordReader.readRecord());
        Assert.assertNull(recordReader.readRecord());
    }

    @Test
    public void testBlankLineBreak4StartingWithBlankLine() throws IOException {
        final RecordReader recordReader = createTest("\nline1\nline2\n\nline3\n\n", "\n\n");
        Assert.assertEquals("\nline1\nline2", recordReader.readRecord());
        Assert.assertEquals("line3", recordReader.readRecord());
        Assert.assertNull(recordReader.readRecord());
    }

    @Test
    public void testUnixMultiLineFormat1() throws IOException {
        final RecordReader recordReader = createTest("----\nline1\nline2\n----\nline3\n----\n", "----\n");
        Assert.assertEquals("line1\nline2\n", recordReader.readRecord());
        Assert.assertEquals("line3\n", recordReader.readRecord());
        Assert.assertNull(recordReader.readRecord());
    }

    @Test
    public void testUnixMultiLineFormat2() throws IOException {
        final RecordReader recordReader = createTest("line1\nline2\n----\nline3\n----\n", "\n----\n");
        Assert.assertEquals("line1\nline2", recordReader.readRecord());
        Assert.assertEquals("line3", recordReader.readRecord());
        Assert.assertNull(recordReader.readRecord());
    }

    @Test
    public void testUnixMultiLineFormat3OddDelimiters() throws IOException {
        final RecordReader recordReader = createTest("line1\nline2\n----\nline3\n----\n----\n----\n", "----\n");
        Assert.assertEquals("line1\nline2\n", recordReader.readRecord());
        Assert.assertEquals("line3\n", recordReader.readRecord());
        Assert.assertNull(recordReader.readRecord());
    }

}
