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

package stroom.util.shared;

import org.junit.Assert;
import org.junit.Test;

public class TestModelStringUtil {
    @Test
    public void testTimeSizeDividerNull() {
        doTest("", null);
    }

    @Test
    public void testTimeSizeDivider1() {
        doTest("1", 1L);
    }

    @Test
    public void testTimeSizeDivider1000() {
        doTest("1000", 1000L);
    }

    @Test
    public void testTimeSizeDivider1Ms() {
        doTest("1MS", 1L);
    }

    @Test
    public void testTimeSizeDivider1ms() {
        doTest("1 ms", 1L);
    }

    @Test
    public void testTimeSizeDivider1s() {
        doTest("1 s", 1000L);
    }

    @Test
    public void testTimeSizeDivider1m() {
        doTest("1 m", 60 * 1000L);
    }

    @Test
    public void testTimeSizeDivider1h() {
        doTest("1 h", 60 * 60 * 1000L);
    }

    @Test
    public void testTimeSizeDivider1d() {
        doTest("1 d", 24 * 60 * 60 * 1000L);
    }

    private Long doTest(String input, Long expected) {
        Long output = ModelStringUtil.parseDurationString(input);

        Assert.assertEquals(expected, output);

        System.out.println(input + " = " + output);

        return output;

    }

}
