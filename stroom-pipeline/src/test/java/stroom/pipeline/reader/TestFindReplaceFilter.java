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

package stroom.pipeline.reader;


import stroom.pipeline.DefaultLocationFactory;
import stroom.pipeline.LocationFactory;
import stroom.pipeline.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.reader.FindReplaceFilter.Builder;
import stroom.pipeline.reader.FindReplaceFilter.SubSequence;
import stroom.util.shared.ElementId;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class TestFindReplaceFilter {

    private static final int BUFFER_SIZE = 4096;

    @Test
    void test() {
        final Builder builder = FindReplaceFilter.builder()
                .find("nasty")
                .replacement("friendly");
        test(builder, "This is a nasty string", "This is a friendly string", null);
    }

    @Test
    void testSmallReads() {
        final Builder builder = FindReplaceFilter.builder()
                .find("nasty")
                .replacement("friendly");
        test(builder, 2, "This is a nasty string", "This is a friendly string", null);
    }

    @Test
    void testSingleReplacement() {
        final Builder builder = FindReplaceFilter.builder()
                .find("cat")
                .replacement("dog")
                .maxReplacements(1);
        test(builder, 2, "dog cat dog cat dog", "dog dog dog cat dog", null);
    }

    @Test
    void testBiggerReplacement() {
        final String input = getDogCat();
        final String expected = input.replaceAll("cat", "dog");
        final Builder builder = FindReplaceFilter.builder()
                .find("cat")
                .replacement("dog");
        test(builder, input, expected, null);
    }

    @Test
    void testBiggerReplacement2() {
        final String input = getDogCat2();
        final String expected = input.replaceAll("cat", "a");
        final Builder builder = FindReplaceFilter.builder()
                .find("cat")
                .replacement("a");
        test(builder, 100000, input, expected, null);
    }

    @Test
    void testStartMatch() {
        final Builder builder = FindReplaceFilter.builder()
                .find("^cat")
                .replacement("dog")
                .regex(true);
        test(builder, 100000, "cat dog cat dog", "dog dog cat dog", null);
    }

    @Test
    void testEndMatch() {
        final String input = getDogCat3();
        final String expected = input.replaceAll("cat$", "a");
        assertThat(expected.endsWith("aaacata")).isTrue();
        final Builder builder = FindReplaceFilter.builder()
                .find("cat$")
                .replacement("a")
                .regex(true);
        test(builder, 100000, input, expected, null);
    }

    @Test
    void testBigStartMatch() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            sb.append("a");
        }
        final String input = sb.toString();
        final String expected = input.replaceAll("^a", "b");
        final Builder builder = FindReplaceFilter.builder()
                .find("^a")
                .replacement("b")
                .regex(true);
        test(builder, input, expected, null);
    }

    @Test
    void testBigEndMatch() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            sb.append("a");
        }
        final String input = sb.toString();
        final String expected = input.replaceAll("a$", "b");
        final Builder builder = FindReplaceFilter.builder()
                .find("a$")
                .replacement("b")
                .regex(true);
        test(builder, input, expected, null);
    }

    @Test
    void testBigStartAndEndMatch() {
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
        final Builder builder = FindReplaceFilter.builder()
                .find("^a|a$")
                .replacement("b")
                .regex(true);
        test(builder, input, expected, null);
    }

    @Test
    void testInvalidRegex() {
        try {
            FindReplaceFilter.builder()
                    .find("{{bad}}")
                    .replacement("a")
                    .regex(true)
                    .build();
            fail("Shouldn't get here");
        } catch (final RuntimeException e) {
            // Ignore.
        }
    }

    @Test
    void testEscapedChars() {
        final Builder builder = FindReplaceFilter.builder()
                .find("[\u0000-\u0009\u000C\u000E-\u001F]")
                .replacement(" ")
                .regex(true);
        test(builder,
                "This\u0000string\u0001contains\u0002non\u0003alpha\u0004chars",
                "This string contains non alpha chars",
                null);
    }

    @Test
    void testMatchMany() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("cat");
        }
        final String input = sb.toString();
        final String expected = input.replaceAll("cat", "dog");
        final Builder builder = FindReplaceFilter.builder()
                .find("cat")
                .replacement("dog");
        test(builder, input, expected, null);
    }

    @Test
    void testMatchFirstOnly() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("cat");
        }
        final String input = sb.toString();
        final String expected = input.replaceAll("^cat", "dog");
        final Builder builder = FindReplaceFilter.builder()
                .find("^cat")
                .replacement("dog")
                .regex(true);
        test(builder, input, expected, null);
    }

    @Test
    void testNoMatchInBuffer1() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500000; i++) {
            if (i == 1999) {
                sb.append("\u0000");
            }
            sb.append("a");
        }
        final String input = sb.toString();
        final String expected = input.replaceAll("\u0000", "");
        final Builder builder = FindReplaceFilter.builder()
                .find("\u0000")
                .replacement("")
                .regex(true);
        test(builder, input, expected, null);
//        assertThat(output).isEqualTo("This string contains chars");
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
//                final Builder builder = FindReplaceFilter.builder()
//                        .find("\u0000")
//                        .replacement("")
//                        .regex(true);
//                test(sb.toString(), builder);
////        assertThat(output).isEqualTo("This string contains chars");
//            } catch (final Exception e) {
//                System.out.println(j);
//                System.out.println(e.getMessage());
//            }
//        }
//    }

    @Test
    void testNoMatchInBuffer2() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500000; i++) {
            sb.append("a\u0000");
        }
        final String input = sb.toString();
        final String expected = input.replaceAll("\u0000", "");
        final Builder builder = FindReplaceFilter.builder()
                .find("\u0000")
                .replacement("")
                .regex(true);
        test(builder, input, expected, null);
//        assertThat(output).isEqualTo("This string contains chars");
    }

    @Test
    void testNoMatchInBuffer3() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500000; i++) {
            sb.append("\u0000a");
        }
        final String input = sb.toString();
        final String expected = input.replaceAll("\u0000", "");
        final Builder builder = FindReplaceFilter.builder()
                .find("\u0000")
                .replacement("")
                .regex(true);
        test(builder, input, expected, null);
//        assertThat(output).isEqualTo("This string contains chars");
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
//            final Builder builder = FindReplaceFilter.builder()
//                    .find("[\u0000-\u0009\u000C\u000E-\u001F]")
//                    .replacement("a")
//                    .regex(true);
//            test(value, builder);
//            final String expected = value.replaceAll("[^a]", "a");
//            assertThat(output).isEqualTo(expected);
//        }
//    }

    @Test
    void testSingleMatch2() {
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
        final Builder builder = FindReplaceFilter.builder()
                .find("[\u0000-\u0009\u000C\u000E-\u001F]")
                .replacement("a")
                .regex(true);
        test(builder, input, expected, null);
    }

    @Test
    void testEmptyMatch() {
        final Builder builder = FindReplaceFilter.builder()
                .find("^$")
                .replacement("<EventRoot/>")
                .regex(true);
        final String input = "";
        final String expected = "<EventRoot/>";
        test(builder, input, expected, null);
    }

    @Test
    void testNegativeEmptyMatch() {
        final Builder builder = FindReplaceFilter.builder()
                .find("^$")
                .replacement("<EventRoot/>")
                .regex(true);
        final String input = "text";
        final String expected = "text";
        test(builder, input, expected, null);
    }

    @Test
    void testStartAnchor() {
        for (int i = 0; i < 9; i++) {
            final Matcher matcher = Pattern.compile("^dog").matcher("catdogcat");
            assertThat(matcher.find(i)).isFalse();
        }

        Matcher matcher = Pattern.compile("^dog").matcher("dogcatcat");
        assertThat(matcher.find(0)).isTrue();

        matcher = Pattern.compile("^dog").matcher("dogcatcat");
        assertThat(matcher.find(0)).isTrue();
        StringBuffer sb = new StringBuffer();
        matcher.appendReplacement(sb, "cat");
        matcher.appendTail(sb);
        assertThat(sb.toString()).isEqualTo("catcatcat");


        String input = "dogcatratdogcatratdogcatrat";
        matcher = Pattern.compile("^dog").matcher(input);
        sb = new StringBuffer();
        int start = 0;
        while (matcher.find(start)) {
            matcher.appendReplacement(sb, "cat");
            start = matcher.end();
        }
        sb.append(input.substring(start));
        assertThat(sb.toString()).isEqualTo("catcatratdogcatratdogcatrat");

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
        assertThat(sb.toString()).isEqualTo("catcatratcatcatratcatcatrat");
    }

    @Test
    void testPaddingWrapper() {
        assertThat(new FindReplaceFilter.PaddingWrapper("aaa", false).toString()).isEqualTo("aaa");
        assertThat(new FindReplaceFilter.PaddingWrapper("aaa", true).toString()).isEqualTo(((char) 0) + "aaa");
    }

    @Test
    void testSubsequence() {
        assertThat("aaabbbccc".subSequence(0, 3).toString()).isEqualTo("aaa");
        assertThat("aaabbbccc".subSequence(3, 6).toString()).isEqualTo("bbb");
        assertThat("aaabbbccc".subSequence(6, 9).toString()).isEqualTo("ccc");
        assertThat("aaabbbccc".subSequence(3, 9).subSequence(3, 6).toString()).isEqualTo("ccc");

        assertThat(new SubSequence("aaabbbccc", 0, 3).toString()).isEqualTo("aaa");
        assertThat(new SubSequence("aaabbbccc", 3, 6).toString()).isEqualTo("bbb");
        assertThat(new SubSequence("aaabbbccc", 6, 9).toString()).isEqualTo("ccc");
        assertThat(new SubSequence("aaabbbccc", 3, 9).subSequence(3, 6).toString()).isEqualTo("ccc");
    }

    @Test
    void testExhaustBuffer1() {
        final Builder builder = FindReplaceFilter.builder()
                .find(".*")
                .replacement("b")
                .regex(true);
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            sb.append("a");
        }

        final String input = sb.toString();
        final String expected = input;
        test(builder,
                input,
                expected,
                "The pattern matched all text in the buffer. Consider changing your match expression " +
                        "or making the buffer bigger.");
    }

    @Test
    void testExhaustBuffer2() {
        final Builder builder = FindReplaceFilter.builder()
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
        test(builder,
                input,
                expected,
                "The pattern matched text at the end of the buffer when we are not at the end of " +
                        "the stream. Consider changing your match expression or making the buffer bigger");
    }

    @Test
    void testExhaustBuffer3() {
        final Builder builder = FindReplaceFilter.builder()
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
    void testExhaustBuffer4() {
        final Builder builder = FindReplaceFilter.builder()
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
        test(builder,
                input,
                expected,
                "The pattern matched text at the end of the buffer when we are not at the end of " +
                        "the stream. Consider changing your match expression or making the buffer bigger");
    }

    @Test
    void testOdd() {
        final String out = ("baaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" +
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa").replaceAll("a*", "c");
        assertThat(out)
                .isEqualTo("cbcc");
    }

    @Test
    void testOdd2() {
        final String out = ("baaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" +
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa").replaceAll("a*$", "c");
        assertThat(out)
                .isEqualTo("bcc");
    }

    @Test
    void testEscapedSquareBracket() {
        final Builder builder = FindReplaceFilter.builder()
                .find("<(\\[dog)")
                .replacement("wolf")
                .regex(true);
        test(builder, 100000, "cat <[dog cat dog", "cat wolf cat dog", null);
    }

    @Test
    void testReplacement() {
        final Builder builder = FindReplaceFilter.builder()
                .find("(\n)type=")
                .replacement("$1missingToken=bar type=")
                .regex(true);
        test(builder, 100000, "something\ntype=foo", "something\nmissingToken=bar type=foo", null);
    }

    @SuppressWarnings("checkstyle:LineLength")
    @Test
    void testLargeReplacement() {
        final String input = "hostname=? addr=? terminal=cron res=success. \n" +
                "type=SERVICE_START msg.audit(1505725382.332:480): pid=1 uid=0 auid=4294967295 ses=42949672 95 subj=system_u:system_r:init_t:s0 msg=.unit=systemd-tmpfiles-clean comm=.systemd. exe=./ usr/lib/systemd/systemd. hostname=? addr=? terminal=? res=ccess. \n" +
                "type=SERVICE_STOP msg=audit(1505725382.332:481): pid=1 uid=s0 auid=4294967295 ses=429496729 5 subj=system_u:system_r:init_t:s0 msg=.unit=systemd-tmpfiles-clean comm=.systemd. exe=./u sr/lib/systemd/systemd. hostname=? addr=? terminal=? res=success. \n" +
                "type=USER_ACCT msg=audit(1505728861.714:482): pid=2317 uid=0 auid=4294967295 ses=429496729 5 subj=system_u:system_r:crond_t:s0-s0:c0.c1023 msg=.op=PAM:accounting acct=.root. exe=./u sr/sbin/crond. hostname=? addr=? terminal=cron res=success. \n" +
                "type=CRED_ACQ msg=audit(1505728861.714:483): pid=2317 uid=0 auid=4294967295 ses=4294967295 subj=system_u:system_r:crond_t:s0-s0:c0.c1023 msg=.op=PAM:setcred acct=.root. exe=./usr/s bin/crond. hostname=? addr=? terminal=cron res=success. \n" +
                "type=LOGIN msg=audit(1505728861.714:484): pid=2317 uid=0 subj=system_u:system_r:crond_t:s0 -s0:c0.c1023 old-auid=4294967295 auid=0 old-ses=4294967295 ses=5 res=1 \n" +
                "type=USER_START msg=audit(1505728861.734:485): pid=2317 uid=0 auid=0 ses=5 subj=system_u:system_r:crond_t:s0-s0:c0.c1023 msg=.op=PAM:session_open acct=.root. exe=./usr/sbin/crond. hostname=? addr=? terminal=cron res=success. \n" +
                "type=CRED_REFR msg=audit(1505728861.734:486): pid=2317 uid=0 auid=0 ses=5 subj=system_u:sy stem_r:crond_t:s0-s0:c0.c1023 msg=.op=PAM:setcred acct=.root. exe=./usr/sbin/crond. hostname=? addr=? terminal=cron res=success. \n" +
                "type=CRED_DISP msg=audit(1505728861.754:487): pid=2317 uid=0 auid=0 ses=5 subj=system_u:sy stem_r:crond_t:s0-s0:c0.c1023 msg=.op=PAM:setcred acct=.root. exe=./usr/sbin/crond. hostname=? addr=? terminal=cron res=success. \n" +
                "type=USER_END msg=audit(1505728861.764:488): pid=2317 uid=0 auid=0 ses=5 subj=system_u:sys tem_r:crond_t:s0-s0:c0.c1023 msg=.op=PAM:session_close acct=.root. exe=./usr/sbin/crond. hostname=? addr=? terminal=cron res=success.";
        final String output = "hostname=? addr=? terminal=cron res=success. \n" +
                "missingToken=bar type=SERVICE_START msg.audit(1505725382.332:480): pid=1 uid=0 auid=4294967295 ses=42949672 95 subj=system_u:system_r:init_t:s0 msg=.unit=systemd-tmpfiles-clean comm=.systemd. exe=./ usr/lib/systemd/systemd. hostname=? addr=? terminal=? res=ccess. \n" +
                "missingToken=bar type=SERVICE_STOP msg=audit(1505725382.332:481): pid=1 uid=s0 auid=4294967295 ses=429496729 5 subj=system_u:system_r:init_t:s0 msg=.unit=systemd-tmpfiles-clean comm=.systemd. exe=./u sr/lib/systemd/systemd. hostname=? addr=? terminal=? res=success. \n" +
                "missingToken=bar type=USER_ACCT msg=audit(1505728861.714:482): pid=2317 uid=0 auid=4294967295 ses=429496729 5 subj=system_u:system_r:crond_t:s0-s0:c0.c1023 msg=.op=PAM:accounting acct=.root. exe=./u sr/sbin/crond. hostname=? addr=? terminal=cron res=success. \n" +
                "missingToken=bar type=CRED_ACQ msg=audit(1505728861.714:483): pid=2317 uid=0 auid=4294967295 ses=4294967295 subj=system_u:system_r:crond_t:s0-s0:c0.c1023 msg=.op=PAM:setcred acct=.root. exe=./usr/s bin/crond. hostname=? addr=? terminal=cron res=success. \n" +
                "missingToken=bar type=LOGIN msg=audit(1505728861.714:484): pid=2317 uid=0 subj=system_u:system_r:crond_t:s0 -s0:c0.c1023 old-auid=4294967295 auid=0 old-ses=4294967295 ses=5 res=1 \n" +
                "missingToken=bar type=USER_START msg=audit(1505728861.734:485): pid=2317 uid=0 auid=0 ses=5 subj=system_u:system_r:crond_t:s0-s0:c0.c1023 msg=.op=PAM:session_open acct=.root. exe=./usr/sbin/crond. hostname=? addr=? terminal=cron res=success. \n" +
                "missingToken=bar type=CRED_REFR msg=audit(1505728861.734:486): pid=2317 uid=0 auid=0 ses=5 subj=system_u:sy stem_r:crond_t:s0-s0:c0.c1023 msg=.op=PAM:setcred acct=.root. exe=./usr/sbin/crond. hostname=? addr=? terminal=cron res=success. \n" +
                "missingToken=bar type=CRED_DISP msg=audit(1505728861.754:487): pid=2317 uid=0 auid=0 ses=5 subj=system_u:sy stem_r:crond_t:s0-s0:c0.c1023 msg=.op=PAM:setcred acct=.root. exe=./usr/sbin/crond. hostname=? addr=? terminal=cron res=success. \n" +
                "missingToken=bar type=USER_END msg=audit(1505728861.764:488): pid=2317 uid=0 auid=0 ses=5 subj=system_u:sys tem_r:crond_t:s0-s0:c0.c1023 msg=.op=PAM:session_close acct=.root. exe=./usr/sbin/crond. hostname=? addr=? terminal=cron res=success.";

        final Builder builder = FindReplaceFilter.builder()
                .find("(\n)type=")
                .replacement("$1missingToken=bar type=")
                .regex(true);
        test(builder, 1000, input, output, null);
    }

    @Test
    public void testMultiFilter() {
        final Builder builder1 = FindReplaceFilter.builder()
                .find("a")
                .replacement("")
                .regex(true);
        final Builder builder2 = FindReplaceFilter.builder()
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

    private void test(final Builder builder,
                      final String input,
                      final String expectedOutput,
                      final String expectedError) {
        test(builder, BUFFER_SIZE, input, expectedOutput, expectedError);
    }

    private void test(final Builder builder,
                      final int length,
                      final String input,
                      final String expectedOutput,
                      final String expectedError) {
        final Builder[] builders = {builder};
        testMulti(builders, length, input, expectedOutput, expectedError);
    }

    private void testMulti(final Builder[] builder,
                           final int length,
                           final String input,
                           final String expectedOutput,
                           final String expectedError) {
        testMulti(builder, length, input, expectedOutput, expectedError, false);
        testMulti(builder, length, input, expectedOutput, expectedError, true);
    }

    private void testMulti(final Builder[] builder,
                           final int length,
                           final String input,
                           final String expectedOutput,
                           final String expectedError,
                           final boolean singleChar) {
        try {
            final LocationFactory locationFactory = new DefaultLocationFactory();
            final LoggingErrorReceiver loggingErrorReceiver = new LoggingErrorReceiver();

            Reader reader = new StringReader(input);
            for (int i = 0; i < builder.length; i++) {
                reader = builder[i]
                        .reader(reader)
                        .locationFactory(locationFactory)
                        .errorReceiver(loggingErrorReceiver)
                        .elementId(new ElementId("findReplaceFilter_" + i))
                        .build();
            }

            final StringBuilder stringBuilder = new StringBuilder();
            if (singleChar) {
                int c;
                while ((c = reader.read()) != -1) {
                    stringBuilder.append((char) c);
                }
            } else {
                final char[] buffer = new char[length];
                int len;
                while ((len = reader.read(buffer, 0, length)) != -1) {
                    stringBuilder.append(buffer, 0, len);
                }
            }

            final String error = loggingErrorReceiver.toString();
            if (expectedError != null) {
                assertThat(error.contains(expectedError)).isTrue();
            } else if (error.length() > 0) {
                throw ProcessException.create(error);
            }

            assertThat(stringBuilder.toString())
                    .isEqualTo(expectedOutput);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
