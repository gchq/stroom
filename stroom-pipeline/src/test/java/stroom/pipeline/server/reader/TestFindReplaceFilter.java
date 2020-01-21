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
import stroom.pipeline.server.DefaultLocationFactory;
import stroom.pipeline.server.LocationFactory;
import stroom.pipeline.server.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.server.errorhandler.ProcessException;
import stroom.pipeline.server.reader.FindReplaceFilter.Builder;
import stroom.pipeline.server.reader.FindReplaceFilter.SubSequence;

import java.io.IOException;
import java.io.Reader;
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
        test(builder, "This is a nasty string", "This is a friendly string", null);
    }

    @Test
    public void testSmallReads() {
        final Builder builder = new Builder()
                .find("nasty")
                .replacement("friendly");
        test(builder, 2, "This is a nasty string", "This is a friendly string", null);
    }

    @Test
    public void testSingleReplacement() {
        final Builder builder = new Builder()
                .find("cat")
                .replacement("dog")
                .maxReplacements(1);
        test(builder, 2, "dog cat dog cat dog", "dog dog dog cat dog", null);
    }

    @Test
    public void testBiggerReplacement() {
        final String input = getDogCat();
        final String expected = input.replaceAll("cat", "dog");
        final Builder builder = new Builder()
                .find("cat")
                .replacement("dog");
        test(builder, input, expected, null);
    }

    @Test
    public void testBiggerReplacement2() {
        final String input = getDogCat2();
        final String expected = input.replaceAll("cat", "a");
        final Builder builder = new Builder()
                .find("cat")
                .replacement("a");
        test(builder, 100000, input, expected, null);
    }

    @Test
    public void testStartMatch() {
        final Builder builder = new Builder()
                .find("^cat")
                .replacement("dog")
                .regex(true);
        test(builder, 100000, "cat dog cat dog", "dog dog cat dog", null);
    }

    @Test
    public void testEndMatch() {
        final String input = getDogCat3();
        final String expected = input.replaceAll("cat$", "a");
        Assert.assertTrue(expected.endsWith("aaacata"));
        final Builder builder = new Builder()
                .find("cat$")
                .replacement("a")
                .regex(true);
        test(builder, 100000, input, expected, null);
    }

    @Test
    public void testBigStartMatch() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            sb.append("a");
        }
        final String input = sb.toString();
        final String expected = input.replaceAll("^a", "b");
        final Builder builder = new Builder()
                .find("^a")
                .replacement("b")
                .regex(true);
        test(builder, input, expected, null);
    }

    @Test
    public void testBigEndMatch() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            sb.append("a");
        }
        final String input = sb.toString();
        final String expected = input.replaceAll("a$", "b");
        final Builder builder = new Builder()
                .find("a$")
                .replacement("b")
                .regex(true);
        test(builder, input, expected, null);
    }

    @Test
    public void testBigStartAndEndMatch() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2500; i++) {
            sb.append("a");
        }
        sb.append("b");
        for (int i = 0; i < 2499; i++) {
            sb.append("a");
        }
        final String input = sb.toString();
        final String expected = input.replaceAll("^a|a$", "b");
        final Builder builder = new Builder()
                .find("^a|a$")
                .replacement("b")
                .regex(true);
        test(builder, input, expected, null);
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
        test(builder, "This\u0000string\u0001contains\u0002non\u0003alpha\u0004chars", "This string contains non alpha chars", null);
    }

    @Test
    public void testMatchMany() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("cat");
        }
        final String input = sb.toString();
        final String expected = input.replaceAll("cat", "dog");
        final Builder builder = new Builder()
                .find("cat")
                .replacement("dog");
        test(builder, input, expected, null);
    }

    @Test
    public void testMatchFirstOnly() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("cat");
        }
        final String input = sb.toString();
        final String expected = input.replaceAll("^cat", "dog");
        final Builder builder = new Builder()
                .find("^cat")
                .replacement("dog")
                .regex(true);
        test(builder, input, expected, null);
    }

    @Test
    public void testNoMatchInBuffer1() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500000; i++) {
            if (i == 1999) {
                sb.append("\u0000");
            }
            sb.append("a");
        }
        final String input = sb.toString();
        final String expected = input.replaceAll("\u0000", "");
        final Builder builder = new Builder()
                .find("\u0000")
                .replacement("")
                .regex(true);
        test(builder, input, expected, null);
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
//                test(sb.toString(), builder);
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
        final String input = sb.toString();
        final String expected = input.replaceAll("\u0000", "");
        final Builder builder = new Builder()
                .find("\u0000")
                .replacement("")
                .regex(true);
        test(builder, input, expected, null);
//        Assert.assertEquals("This string contains chars", output);
    }

    @Test
    public void testNoMatchInBuffer3() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500000; i++) {
            sb.append("\u0000a");
        }
        final String input = sb.toString();
        final String expected = input.replaceAll("\u0000", "");
        final Builder builder = new Builder()
                .find("\u0000")
                .replacement("")
                .regex(true);
        test(builder, input, expected, null);
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
//            test(value, builder);
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
        final String input = sb.toString();
        final String expected = input.replaceAll("[^a]", "a");
        final Builder builder = new Builder()
                .find("[\u0000-\u0009\u000C\u000E-\u001F]")
                .replacement("a")
                .regex(true);
        test(builder, input, expected, null);
    }

    @Test
    public void testEmptyMatch() {
        final Builder builder = new Builder()
                .find("^$")
                .replacement("<EventRoot/>")
                .regex(true);
        final String input = "";
        final String expected = "<EventRoot/>";
        test(builder, input, expected, null);
    }

    @Test
    public void testNegativeEmptyMatch() {
        final Builder builder = new Builder()
                .find("^$")
                .replacement("<EventRoot/>")
                .regex(true);
        final String input = "text";
        final String expected = "text";
        test(builder, input, expected, null);
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

    @Test
    public void testExhaustBuffer1() {
        final Builder builder = new Builder()
                .find(".*")
                .replacement("b")
                .regex(true);
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            sb.append("a");
        }

        final String input = sb.toString();
        final String expected = input;
        test(builder, input, expected, "The pattern matched all text in the buffer. Consider changing your match expression or making the buffer bigger.");
    }

    @Test
    public void testExhaustBuffer2() {
        final Builder builder = new Builder()
                .find("a*")
                .replacement("c")
                .regex(true);
        final StringBuilder sb = new StringBuilder();
        sb.append("b");
        for (int i = 0; i < 5000; i++) {
            sb.append("a");
        }

        final String input = sb.toString();
        final String expected = input.replaceFirst("a*", "c");
        test(builder, input, expected, "The pattern matched text at the end of the buffer when we are not at the end of the stream. Consider changing your match expression or making the buffer bigger");
    }

    @Test
    public void testExhaustBuffer3() {
        final Builder builder = new Builder()
                .find("a*")
                .replacement("c")
                .regex(true);
        final StringBuilder sb = new StringBuilder();
        sb.append("b");
        for (int i = 0; i < 100; i++) {
            sb.append("a");
        }

        final String input = sb.toString();
        final String expected = input.replaceAll("a*", "c");
        test(builder, input, expected, null);
    }

    @Test
    public void testExhaustBuffer4() {
        final Builder builder = new Builder()
                .find("a+")
                .replacement("c")
                .regex(true);
        final StringBuilder sb = new StringBuilder();
        sb.append("b");
        for (int i = 0; i < 5000; i++) {
            sb.append("a");
        }

        final String input = sb.toString();
        final String expected = input;
        test(builder, input, expected, "The pattern matched text at the end of the buffer when we are not at the end of the stream. Consider changing your match expression or making the buffer bigger");
    }

    @Test
    public void testOdd() {
        final String out = "baaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".replaceAll("a*", "c");
        Assert.assertEquals("cbcc", out);
    }

    @Test
    public void testOdd2() {
        final String out = "baaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".replaceAll("a*$", "c");
        Assert.assertEquals("bcc", out);
    }

    @Test
    public void testEscapedSquareBracket() {
        final Builder builder = new Builder()
                .find("<(\\[dog)")
                .replacement("wolf")
                .regex(true);
        test(builder, 100000, "cat <[dog cat dog", "cat wolf cat dog", null);
    }

    @Test
    public void testMultiFilter() {
        final Builder builder1 = new Builder()
                .find("a")
                .replacement("")
                .regex(true);
        final Builder builder2 = new Builder()
                .find("b")
                .replacement("c")
                .regex(true);
        final Builder[] builders = {builder1, builder2};
        testMulti(builders, 100000, "abb", "cc", null);
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

    private void test(final Builder builder, final String input, final String expectedOutput, final String expectedError) {
        test(builder, BUFFER_SIZE, input, expectedOutput, expectedError);
    }

    private void test(final Builder builder, final int length, final String input, final String expectedOutput, final String expectedError) {
//        try {
//            final LoggingErrorReceiver loggingErrorReceiver = new LoggingErrorReceiver();
//            final FindReplaceFilter reader = builder
//                    .reader(new StringReader(input))
//                    .locationFactory(new DefaultLocationFactory())
//                    .errorReceiver(loggingErrorReceiver)
//                    .elementId("findReplaceFilter")
//                    .build();
//
//            final StringBuilder stringBuilder = new StringBuilder();
//            final char[] buffer = new char[length];
//            int len;
//            while ((len = reader.read(buffer, 0, length)) != -1) {
//                stringBuilder.append(buffer, 0, len);
//            }
//
//            final String error = loggingErrorReceiver.toString();
//            if (expectedError != null) {
//                Assert.assertTrue(error.contains(expectedError));
//            } else if (error.length() > 0) {
//                throw new ProcessException(error);
//            }
//
//            Assert.assertEquals(expectedOutput, stringBuilder.toString());
//        } catch (final IOException e) {
//            throw new UncheckedIOException(e);
//        }

        final Builder[] builders = {builder};
        testMulti(builders, length, input, expectedOutput, expectedError);
    }

    private void testMulti(final Builder[] builder, final int length, final String input, final String expectedOutput, final String expectedError) {
        try {
            final LocationFactory locationFactory = new DefaultLocationFactory();
            final LoggingErrorReceiver loggingErrorReceiver = new LoggingErrorReceiver();

            Reader reader = new StringReader(input);
            for (int i = 0; i < builder.length; i++) {
                reader = builder[i]
                        .reader(reader)
                        .locationFactory(locationFactory)
                        .errorReceiver(loggingErrorReceiver)
                        .elementId("findReplaceFilter_" + i)
                        .build();
            }

            final StringBuilder stringBuilder = new StringBuilder();
            final char[] buffer = new char[length];
            int len;
            while ((len = reader.read(buffer, 0, length)) != -1) {
                stringBuilder.append(buffer, 0, len);
            }

            final String error = loggingErrorReceiver.toString();
            if (expectedError != null) {
                Assert.assertTrue(error.contains(expectedError));
            } else if (error.length() > 0) {
                throw new ProcessException(error);
            }

            Assert.assertEquals(expectedOutput, stringBuilder.toString());
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
