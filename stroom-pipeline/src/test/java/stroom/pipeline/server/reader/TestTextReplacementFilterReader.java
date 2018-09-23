/*
 * Copyright 2018 Crown Copyright
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

package stroom.pipeline.server.reader;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;

public class TestTextReplacementFilterReader {
    private static final int BUFFER_SIZE = 4096;

    @Test
    public void test() {
        final Reader reader = new StringReader("This is a nasty string");
        final TextReplacementFilterReader textReplacementFilterReader = new TextReplacementFilterReader.Builder()
                .reader(reader)
                .regex("nasty")
                .replacement("friendly")
                .build();
        final String output = getOutput(textReplacementFilterReader);
        Assert.assertEquals("This is a friendly string", output);
    }

    @Test
    public void testSmallReads() {
        final Reader reader = new StringReader("This is a nasty string");
        final TextReplacementFilterReader textReplacementFilterReader = new TextReplacementFilterReader.Builder()
                .reader(reader)
                .regex("nasty")
                .replacement("friendly")
                .build();
        final String output = getOutput(textReplacementFilterReader, 2);
        Assert.assertEquals("This is a friendly string", output);
    }

    @Test
    public void testSingleReplacement() {
        final Reader reader = new StringReader("dog cat dog cat dog");
        final TextReplacementFilterReader textReplacementFilterReader = new TextReplacementFilterReader.Builder()
                .reader(reader)
                .regex("cat")
                .replacement("dog")
                .maxReplacements(1)
                .build();
        final String output = getOutput(textReplacementFilterReader, 2);
        Assert.assertEquals("dog dog dog cat dog", output);
    }

    @Test
    public void testBiggerReplacement() {
        final Reader reader = new StringReader(getDogCat());
        final TextReplacementFilterReader textReplacementFilterReader = new TextReplacementFilterReader.Builder()
                .reader(reader)
                .regex("cat")
                .replacement("dog")
                .build();
        final String output = getOutput(textReplacementFilterReader);
        Assert.assertFalse(output.contains("cat"));
    }

    @Test
    public void testBiggerReplacement2() {
        final Reader reader = new StringReader(getDogCat2());
        final TextReplacementFilterReader textReplacementFilterReader = new TextReplacementFilterReader.Builder()
                .reader(reader)
                .regex("cat")
                .replacement("a")
                .build();
        final String output = getOutput(textReplacementFilterReader, 100000);
        Assert.assertFalse(output.contains("cat"));
    }

    private String getDogCat() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            if (i % 2 == 0) {
                sb.append("dog ");
            } else {
                sb.append("cat ");
            }
        }
        return sb.toString();
    }

    private String getDogCat2() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 39999; i++) {
                sb.append("a");
        }
        sb.append("cat");
        return sb.toString();
    }

    private String getOutput(final Reader reader) {
        return getOutput(reader, BUFFER_SIZE);
    }

    private String getOutput(final Reader reader, final int length) {
        try {
            final StringBuilder stringBuilder = new StringBuilder();
            final char[] buffer = new char[length];
            int len;
            while ((len = reader.read(buffer, 0, length)) != -1) {
                stringBuilder.append(buffer, 0, len);
            }
            return stringBuilder.toString();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
