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

package stroom.xml.converter.ds3;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestSplitMatcher extends StroomUnitTest {
    private static final String TESTDATA = ",692289,2012-08-28 09:56:00,2012-08-28 09:56:00,\"562\",8,Suc\"c\"ess Audit event,  \"3\" ,  \"Objec\"t\" Access\"  ,   Secur\"ity,Sec\"urity   |5676|1144|C:\\WINNT\\system32\\svchost.exe,CODE123/,NT AUTHORITY\\SYSTEM,   \"  Handle Closed,  \"  \": Ob/,ject \" Se,rver: Security Handle ID: 5676 Process ID: 1144 Image File Name: C:\\WINNT\\system32\\svchost.exe ,\n";
    private static final char[] TESTARR = TESTDATA.toCharArray();

    @Test
    public void test() {
        final CharBuffer input = new CharBuffer(TESTARR, 0, TESTARR.length);
        final SplitFactory splitFactory = new SplitFactory(null, "split", "\nSecurity");
        final Split split = new Split(null, splitFactory);
        split.setInput(input);

        final Match match = split.match();

        Assert.assertEquals(0, match.start());
        Assert.assertEquals(341, match.end());
    }

    @Test
    public void testFilter() {
        final CharBuffer input = new CharBuffer(TESTARR, 0, TESTARR.length);
        final SplitFactory splitFactory = new SplitFactory(null, "split", "\nSecurity");
        final Split split = new Split(null, splitFactory);
        split.setInput(input);

        final Match match = split.match();

        Assert.assertEquals(TESTDATA, match.filter(input, 0).toString());
        Assert.assertEquals(TESTDATA.trim(), match.filter(input, 1).toString());
    }

    @Test
    public void testFilter2() {
        final CharBuffer input = new CharBuffer(TESTARR, 0, TESTARR.length);
        final SplitFactory splitFactory = new SplitFactory(null, "split", 0, -1, null, ",", "/", "\"", "\"");
        final Split split = new Split(null, splitFactory);
        split.setInput(input);

        Match match = split.match();

        Assert.assertEquals(",", match.filter(input, 0).toString());
        Assert.assertEquals("", match.filter(input, 1).toString());

        input.move(match.end());
        match = split.match();

        Assert.assertEquals("692289,", match.filter(input, 0).toString());
        Assert.assertEquals("692289", match.filter(input, 1).toString());

        input.move(match.end());
        match = split.match();

        Assert.assertEquals("2012-08-28 09:56:00,", match.filter(input, 0).toString());
        Assert.assertEquals("2012-08-28 09:56:00", match.filter(input, 1).toString());

        input.move(match.end());
        match = split.match();

        Assert.assertEquals("2012-08-28 09:56:00,", match.filter(input, 0).toString());
        Assert.assertEquals("2012-08-28 09:56:00", match.filter(input, 1).toString());
        Assert.assertEquals("2012-08-28 09:56:00", match.filter(input, 2).toString());

        input.move(match.end());
        match = split.match();

        Assert.assertEquals("\"562\",", match.filter(input, 0).toString());
        Assert.assertEquals("562", match.filter(input, 1).toString());
        Assert.assertEquals("562", match.filter(input, 2).toString());

        input.move(match.end());
        match = split.match();

        Assert.assertEquals("8,", match.filter(input, 0).toString());
        Assert.assertEquals("8", match.filter(input, 1).toString());
        Assert.assertEquals("8", match.filter(input, 2).toString());

        input.move(match.end());
        match = split.match();

        Assert.assertEquals("Suc\"c\"ess Audit event,", match.filter(input, 0).toString());
        Assert.assertEquals("Suc\"c\"ess Audit event", match.filter(input, 1).toString());
        Assert.assertEquals("Suc\"c\"ess Audit event", match.filter(input, 2).toString());

        input.move(match.end());
        match = split.match();

        Assert.assertEquals("  \"3\" ,", match.filter(input, 0).toString());
        Assert.assertEquals("3", match.filter(input, 1).toString());
        Assert.assertEquals("3", match.filter(input, 2).toString());

        input.move(match.end());
        match = split.match();

        Assert.assertEquals("  \"Objec\"t\" Access\"  ,", match.filter(input, 0).toString());
        Assert.assertEquals("Objec\"t\" Access", match.filter(input, 1).toString());
        Assert.assertEquals("Objec\"t\" Access", match.filter(input, 2).toString());

        input.move(match.end());
        match = split.match();

        Assert.assertEquals("   Secur\"ity,Sec\"urity   |5676|1144|C:\\WINNT\\system32\\svchost.exe,",
                match.filter(input, 0).toString());
        Assert.assertEquals("Secur\"ity,Sec\"urity   |5676|1144|C:\\WINNT\\system32\\svchost.exe",
                match.filter(input, 1).toString());
        Assert.assertEquals("Secur\"ity,Sec\"urity   |5676|1144|C:\\WINNT\\system32\\svchost.exe",
                match.filter(input, 2).toString());

        input.move(match.end());
        match = split.match();

        Assert.assertEquals("CODE123/,NT AUTHORITY\\SYSTEM,", match.filter(input, 0).toString());
        Assert.assertEquals("CODE123/,NT AUTHORITY\\SYSTEM", match.filter(input, 1).toString());
        Assert.assertEquals("CODE123,NT AUTHORITY\\SYSTEM", match.filter(input, 2).toString());

        input.move(match.end());
        match = split.match();

        Assert.assertEquals("   \"  Handle Closed,  \"  \": Ob/,ject \" Se,", match.filter(input, 0).toString());
        Assert.assertEquals("Handle Closed,  \"  \": Ob/,ject \" Se", match.filter(input, 1).toString());
        Assert.assertEquals("Handle Closed,  \"  \": Ob,ject \" Se", match.filter(input, 2).toString());

    }
}
