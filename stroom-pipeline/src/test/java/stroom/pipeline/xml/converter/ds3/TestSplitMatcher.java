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

package stroom.pipeline.xml.converter.ds3;


import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestSplitMatcher extends StroomUnitTest {

    private static final String TESTDATA = "" +
            ",692289,2012-08-28 09:56:00,2012-08-28 09:56:00,\"562\",8,Suc\"c\"ess Audit event,  \"3\" ,  " +
            "\"Objec\"t\" Access\"  ,   Secur\"ity,Sec\"urity   |5676|1144|C:\\WINNT\\system32\\svchost.exe," +
            "CODE123/,NT AUTHORITY\\SYSTEM,   \"  Handle/Closed,  \"  \": Ob/,ject \" Se,rver: " +
            "Security Handle ID: 5676 Process ID: 1144 Image File Name: C:\\WINNT\\system32\\svchost.exe ,\n";
    private static final char[] TESTARR = TESTDATA.toCharArray();

    @Test
    void test() {
        final CharBuffer input = new CharBuffer(TESTARR, 0, TESTARR.length);
        final SplitFactory splitFactory = new SplitFactory(null, "split", "\nSecurity");
        final Split split = new Split(null, splitFactory);
        split.setInput(input);

        final Match match = split.match();

        assertThat(match.start())
                .isEqualTo(0);
        assertThat(match.end())
                .isEqualTo(341);
    }

    @Test
    void testFilter() {
        final CharBuffer input = new CharBuffer(TESTARR, 0, TESTARR.length);
        final SplitFactory splitFactory = new SplitFactory(null, "split", "\nSecurity");
        final Split split = new Split(null, splitFactory);
        split.setInput(input);

        final Match match = split.match();

        assertThat(match.filter(input, 0).toString())
                .isEqualTo(TESTDATA);
        assertThat(match.filter(input, 1).toString())
                .isEqualTo(TESTDATA.trim());
    }

    @Test
    void testFilter2() {
        final CharBuffer input = new CharBuffer(TESTARR, 0, TESTARR.length);
        final SplitFactory splitFactory = new SplitFactory(
                null,
                "split",
                0,
                -1,
                null,
                ",",
                "/",
                "\"",
                "\"");
        final Split split = new Split(null, splitFactory);
        split.setInput(input);

        Match match = split.match();

        assertThat(match.filter(input, 0).toString())
                .isEqualTo(",");
        assertThat(match.filter(input, 1).toString())
                .isEqualTo("");

        input.move(match.end());
        match = split.match();

        assertThat(match.filter(input, 0).toString())
                .isEqualTo("692289,");
        assertThat(match.filter(input, 1).toString())
                .isEqualTo("692289");

        input.move(match.end());
        match = split.match();

        assertThat(match.filter(input, 0).toString())
                .isEqualTo("2012-08-28 09:56:00,");
        assertThat(match.filter(input, 1).toString())
                .isEqualTo("2012-08-28 09:56:00");

        input.move(match.end());
        match = split.match();

        assertThat(match.filter(input, 0).toString())
                .isEqualTo("2012-08-28 09:56:00,");
        assertThat(match.filter(input, 1).toString())
                .isEqualTo("2012-08-28 09:56:00");
        assertThat(match.filter(input, 2).toString())
                .isEqualTo("2012-08-28 09:56:00");
        assertThat(match.filter(input, 3).toString())
                .isEqualTo("2012-08-28 09:56:00");

        input.move(match.end());
        match = split.match();

        assertThat(match.filter(input, 0).toString())
                .isEqualTo("\"562\",");
        assertThat(match.filter(input, 1).toString())
                .isEqualTo("562");
        assertThat(match.filter(input, 2).toString())
                .isEqualTo("562");
        assertThat(match.filter(input, 3).toString())
                .isEqualTo("562");

        input.move(match.end());
        match = split.match();

        assertThat(match.filter(input, 0).toString())
                .isEqualTo("8,");
        assertThat(match.filter(input, 1).toString())
                .isEqualTo("8");
        assertThat(match.filter(input, 2).toString())
                .isEqualTo("8");
        assertThat(match.filter(input, 3).toString())
                .isEqualTo("8");

        input.move(match.end());
        match = split.match();

        assertThat(match.filter(input, 0).toString())
                .isEqualTo("Suc\"c\"ess Audit event,");
        assertThat(match.filter(input, 1).toString())
                .isEqualTo("Suc\"c\"ess Audit event");
        assertThat(match.filter(input, 2).toString())
                .isEqualTo("Suc\"c\"ess Audit event");
        assertThat(match.filter(input, 3).toString())
                .isEqualTo("Suc\"c\"ess Audit event");

        input.move(match.end());
        match = split.match();

        assertThat(match.filter(input, 0).toString())
                .isEqualTo("  \"3\" ,");
        assertThat(match.filter(input, 1).toString())
                .isEqualTo("3");
        assertThat(match.filter(input, 2).toString())
                .isEqualTo("3");
        assertThat(match.filter(input, 3).toString())
                .isEqualTo("3");

        input.move(match.end());
        match = split.match();

        assertThat(match.filter(input, 0).toString())
                .isEqualTo("  \"Objec\"t\" Access\"  ,");
        assertThat(match.filter(input, 1).toString())
                .isEqualTo("Objec\"t\" Access");
        assertThat(match.filter(input, 2).toString())
                .isEqualTo("Objec\"t\" Access");
        assertThat(match.filter(input, 3).toString())
                .isEqualTo("Objec\"t\" Access");

        input.move(match.end());
        match = split.match();

        assertThat(match.filter(input, 0).toString())
                .isEqualTo("   Secur\"ity,Sec\"urity   |5676|1144|C:\\WINNT\\system32\\svchost.exe,");
        assertThat(match.filter(input, 1).toString())
                .isEqualTo("Secur\"ity,Sec\"urity   |5676|1144|C:\\WINNT\\system32\\svchost.exe");
        assertThat(match.filter(input, 2).toString())
                .isEqualTo("Secur\"ity,Sec\"urity   |5676|1144|C:\\WINNT\\system32\\svchost.exe");
        assertThat(match.filter(input, 3).toString())
                .isEqualTo("Secur\"ity,Sec\"urity   |5676|1144|C:\\WINNT\\system32\\svchost.exe");

        input.move(match.end());
        match = split.match();

        assertThat(match.filter(input, 0).toString())
                .isEqualTo("CODE123/,NT AUTHORITY\\SYSTEM,");
        assertThat(match.filter(input, 1).toString())
                .isEqualTo("CODE123/,NT AUTHORITY\\SYSTEM");
        assertThat(match.filter(input, 2).toString())
                .isEqualTo("CODE123,NT AUTHORITY\\SYSTEM");
        assertThat(match.filter(input, 3).toString())
                .isEqualTo("CODE123,NT AUTHORITY\\SYSTEM");

        input.move(match.end());
        match = split.match();

        assertThat(match.filter(input, 0).toString())
                .isEqualTo("   \"  Handle/Closed,  \"  \": Ob/,ject \" Se,");
        assertThat(match.filter(input, 1).toString())
                .isEqualTo("Handle/Closed,  \"  \": Ob/,ject \" Se");
        assertThat(match.filter(input, 2).toString())
                .isEqualTo("HandleClosed,  \"  \": Ob,ject \" Se");
        assertThat(match.filter(input, 3).toString())
                .isEqualTo("Handle/Closed,  \"  \": Ob,ject \" Se");

    }
}
