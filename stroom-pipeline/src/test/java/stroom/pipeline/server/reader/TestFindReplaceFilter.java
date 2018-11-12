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
import stroom.pipeline.server.errorhandler.FatalErrorReceiver;
import stroom.pipeline.server.reader.FindReplaceFilter.Builder;
import stroom.pipeline.server.reader.FindReplaceFilter.SubSequence;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestFindReplaceFilter {
    private static final int BUFFER_SIZE = 4096;

    @Test
    public void test() {
        final Builder builder = new Builder()
                .find("nasty")
                .replacement("friendly");
        final String output = getOutput("This is a nasty string", builder);
        Assert.assertEquals("This is a friendly string", output);
    }

    @Test
    public void testSmallReads() {
        final Builder builder = new Builder()
                .find("nasty")
                .replacement("friendly");
        final String output = getOutput("This is a nasty string", builder, 2);
        Assert.assertEquals("This is a friendly string", output);
    }

    @Test
    public void testSingleReplacement() {
        final Builder builder = new Builder()
                .find("cat")
                .replacement("dog")
                .maxReplacements(1);
        final String output = getOutput("dog cat dog cat dog", builder, 2);
        Assert.assertEquals("dog dog dog cat dog", output);
    }

    @Test
    public void testBiggerReplacement() {
        final Builder builder = new Builder()
                .find("cat")
                .replacement("dog");
        final String output = getOutput(getDogCat(), builder);
        Assert.assertFalse(output.contains("cat"));
    }

    @Test
    public void testBiggerReplacement2() {
        final Builder builder = new Builder()
                .find("cat")
                .replacement("a");
        final String output = getOutput(getDogCat2(), builder, 100000);
        Assert.assertFalse(output.contains("cat"));
    }

    @Test
    public void testStartMatch() {
        final Builder builder = new Builder()
                .find("^cat")
                .replacement("dog")
                .regex(true);
        final String output = getOutput("cat dog cat dog", builder, 100000);
        Assert.assertTrue(output.endsWith("dog dog cat dog"));
    }

    @Test
    public void testEndMatch() {
        final Builder builder = new Builder()
                .find("cat$")
                .replacement("a")
                .regex(true);
        final String output = getOutput(getDogCat3(), builder, 100000);
        Assert.assertTrue(output.endsWith("aaacata"));
    }

    @Test
    public void testBigStartMatch() {
        final Builder builder = new Builder()
                .find("^a")
                .replacement("b")
                .regex(true);
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            sb.append("a");
        }

        final String input = sb.toString();
        final String output = getOutput(input, builder);
        final String expected = input.replaceAll("^a", "b");
        Assert.assertEquals(expected, output);
    }

    @Test
    public void testBigEndMatch() {
        final Builder builder = new Builder()
                .find("a$")
                .replacement("b")
                .regex(true);
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            sb.append("a");
        }

        final String input = sb.toString();
        final String output = getOutput(input, builder);
        final String expected = input.replaceAll("a$", "b");
        Assert.assertEquals(expected, output);
    }

    @Test
    public void testBigStartAndEndMatch() {
        final Builder builder = new Builder()
                .find("^a|a$")
                .replacement("b")
                .regex(true);
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2500; i++) {
            sb.append("a");
        }
        sb.append("b");
        for (int i = 0; i < 2499; i++) {
            sb.append("a");
        }

        final String input = sb.toString();
        final String output = getOutput(input, builder);
        final String expected = input.replaceAll("^a|a$", "b");
        Assert.assertEquals(expected, output);
    }

    @Test
    public void testInvalidRegex() {
        try {
            new Builder()
                    .find("{{bad}}")
                    .replacement("a")
                    .regex(true)
                    .build();
            Assert.fail("Shouldn't get here");
        } catch (final RuntimeException e) {
            // Ignore.
        }
    }

    @Test
    public void testEscapedChars() {
        final Builder builder = new Builder()
                .find("[\u0000-\u0009\u000C\u000E-\u001F]")
                .replacement(" ")
                .regex(true);
        final String output = getOutput("This\u0000string\u0001contains\u0002non\u0003alpha\u0004chars", builder);
        Assert.assertEquals("This string contains non alpha chars", output);
    }

    @Test
    public void testMatchMany() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("cat");
        }

        final Builder builder = new Builder()
                .find("cat")
                .replacement("dog");
        final String output = getOutput(sb.toString(), builder);
        Assert.assertEquals(sb.toString().replaceAll("cat", "dog"), output);
    }

    @Test
    public void testMatchFirstOnly() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("cat");
        }

        final Builder builder = new Builder()
                .find("^cat")
                .replacement("dog")
                .regex(true);
        final String output = getOutput(sb.toString(), builder);
        Assert.assertEquals(sb.toString().replaceFirst("cat", "dog"), output);
    }

    @Test
    public void testNoMatchInBuffer() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500000; i++) {
            if (i == 1999) {
                sb.append("\u0000");
            }
            sb.append("a");
        }

        final Builder builder = new Builder()
                .find("\u0000")
                .replacement("")
                .regex(true);
        final String output = getOutput(sb.toString(), builder);
//        Assert.assertEquals("This string contains chars", output);
    }

//    @Test
//    public void testNoMatchInBuffer2222() {
//        for (int j = 0; j < 50000; j++) {
//            try {
//                final StringBuilder sb = new StringBuilder();
//                sb.append("\u0000");
//                for (int i = 0; i < 500000; i++) {
//                    if (i == j) {
//                        sb.append("\u0000");
//                    }
//                    sb.append("a");
//                }
//
//                final Builder builder = new Builder()
//                        .find("\u0000")
//                        .replacement("")
//                        .regex(true);
//                final String output = getOutput(sb.toString(), builder);
////        Assert.assertEquals("This string contains chars", output);
//            } catch (final Exception e) {
//                System.out.println(j);
//                System.out.println(e.getMessage());
//            }
//        }
//    }

    @Test
    public void testNoMatchInBuffer2() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500000; i++) {
            sb.append("a\u0000");
        }

        final Builder builder = new Builder()
                .find("\u0000")
                .replacement("")
                .regex(true);
        final String output = getOutput(sb.toString(), builder);
//        Assert.assertEquals("This string contains chars", output);
    }

    @Test
    public void testNoMatchInBuffer3() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500000; i++) {
            sb.append("\u0000a");
        }

        final Builder builder = new Builder()
                .find("\u0000")
                .replacement("")
                .regex(true);
        final String output = getOutput(sb.toString(), builder);
//        Assert.assertEquals("This string contains chars", output);
    }

    // IGNORE: EXPENSIVE TEST
//    @Test
//    public void testSingleMatch() {
//        for (int j = 1000; j < 5000; j++) {
//            System.out.println(j);
//
//            final StringBuilder sb = new StringBuilder();
//            for (int i = 0; i < j; i++) {
//                sb.append("a");
//            }
//            sb.append("\u0003");
//            for (int i = 0; i < j; i++) {
//                sb.append("a");
//            }
//            final String value = sb.toString();
//
//            final Builder builder = new Builder()
//                    .find("[\u0000-\u0009\u000C\u000E-\u001F]")
//                    .replacement("a")
//                    .regex(true);
//            final String output = getOutput(value, builder);
//            final String expected = value.replaceAll("[^a]", "a");
//            Assert.assertEquals(expected, output);
//        }
//    }

    @Test
    public void testSingleMatch2() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1999; i++) {
            sb.append("a");
        }
        sb.append("\u0003");
        for (int i = 0; i < 1999; i++) {
            sb.append("a");
        }
        final String value = sb.toString();

        final Builder builder = new Builder()
                .find("[\u0000-\u0009\u000C\u000E-\u001F]")
                .replacement("a")
                .regex(true);
        final String output = getOutput(value, builder);
        final String expected = value.replaceAll("[^a]", "a");
        Assert.assertEquals(expected, output);
    }

    @Test
    public void testEmptyMatch() {
        final Builder builder = new Builder()
                .find("^$")
                .replacement("<EventRoot/>")
                .regex(true);
        final String output = getOutput("", builder);
        Assert.assertEquals("<EventRoot/>", output);
    }

    @Test
    public void testNegativeEmptyMatch() {
        final Builder builder = new Builder()
                .find("^$")
                .replacement("<EventRoot/>")
                .regex(true);
        final String output = getOutput("text", builder);
        Assert.assertEquals("text", output);
    }

    @Test
    public void testStartAnchor() {
        for (int i = 0; i < 9; i++) {
            final Matcher matcher = Pattern.compile("^dog").matcher("catdogcat");
            Assert.assertFalse(matcher.find(i));
        }

        Matcher matcher = Pattern.compile("^dog").matcher("dogcatcat");
        Assert.assertTrue(matcher.find(0));

        matcher = Pattern.compile("^dog").matcher("dogcatcat");
        Assert.assertTrue(matcher.find(0));
        StringBuffer sb = new StringBuffer();
        matcher.appendReplacement(sb, "cat");
        matcher.appendTail(sb);
        Assert.assertEquals("catcatcat", sb.toString());


        String input = "dogcatratdogcatratdogcatrat";
        matcher = Pattern.compile("^dog").matcher(input);
        sb = new StringBuffer();
        int start = 0;
        while (matcher.find(start)) {
            matcher.appendReplacement(sb, "cat");
            start = matcher.end();
        }
        sb.append(input.substring(start));
        Assert.assertEquals("catcatratdogcatratdogcatrat", sb.toString());

        input = "dogcatratdogcatratdogcatrat";
        matcher = Pattern.compile("dog").matcher(input);
        sb = new StringBuffer();
        start = 0;
        while (matcher.find(start)) {
            final StringBuffer replacement = new StringBuffer();
            matcher.appendReplacement(replacement, "cat");
            sb.append(replacement, start, replacement.length());

            start = matcher.end();
        }
        sb.append(input.substring(start));
        Assert.assertEquals("catcatratcatcatratcatcatrat", sb.toString());

    }

    @Test
    public void testPaddingWrapper() {
        Assert.assertEquals("aaa", new FindReplaceFilter.PaddingWrapper("aaa", false).toString());
        Assert.assertEquals(((char) 0) + "aaa", new FindReplaceFilter.PaddingWrapper("aaa", true).toString());
    }

    @Test
    public void testSubsequence() {
        Assert.assertEquals("aaa", "aaabbbccc".subSequence(0, 3).toString());
        Assert.assertEquals("bbb", "aaabbbccc".subSequence(3, 6).toString());
        Assert.assertEquals("ccc", "aaabbbccc".subSequence(6, 9).toString());
        Assert.assertEquals("ccc", "aaabbbccc".subSequence(3, 9).subSequence(3, 6).toString());

        Assert.assertEquals("aaa", new SubSequence("aaabbbccc", 0, 3).toString());
        Assert.assertEquals("bbb", new SubSequence("aaabbbccc", 3, 6).toString());
        Assert.assertEquals("ccc", new SubSequence("aaabbbccc", 6, 9).toString());
        Assert.assertEquals("ccc", new SubSequence("aaabbbccc", 3, 9).subSequence(3, 6).toString());
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

    private String getDogCat3() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 39997; i++) {
            sb.append("a");
        }
        sb.append("catcat");
        return sb.toString();
    }

    private String getOutput(final String input, final Builder builder) {
        return getOutput(input, builder, BUFFER_SIZE);
    }

    private String getOutput(final String input, final Builder builder, final int length) {
        try {
            final FindReplaceFilter reader = builder
                    .reader(new StringReader(input))
                    .errorReceiver(new FatalErrorReceiver())
                    .build();

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
