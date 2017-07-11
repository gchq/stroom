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

import java.io.IOException;
import java.io.StringReader;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestCharBuffer extends StroomUnitTest {
    private static final String BIG_BUFFER = "this is some additional text to put in the buffer";
    private String addBuffer = null;

    @Test
    public void test() throws IOException {
        final StringReader stringReader = new StringReader("123456789abcdefghijklmnopqrstuvwxyz");
        final DS3Reader reader = new DS3Reader(stringReader, 100, 200000);

        reader.fillBuffer();
        Assert.assertTrue(reader.isEof());

        Buffer forward = reader.subSequence(0, 23);
        Assert.assertEquals("123456789abcdefghijklmn", forward.toString());

        Buffer reversed = reader.subSequence(0, 23).reverse();
        Assert.assertEquals("123456789abcdefghijklmn", reversed.toString());

        final StringBuilder sb = new StringBuilder();

        sb.setLength(0);
        for (int i = 0; i < 10; i++) {
            sb.append(forward.charAt(i));
        }
        Assert.assertEquals("123456789a", sb.toString());

        sb.setLength(0);
        for (int i = 0; i < 10; i++) {
            sb.append(reversed.charAt(i));
        }
        Assert.assertEquals("nmlkjihgfe", sb.toString());

        forward.move(10);
        reversed.move(10);

        Assert.assertEquals("bcdefghijklmn", forward.toString());
        Assert.assertEquals("123456789abcd", reversed.toString());

        // Do further tests to prove reversal
        forward = reader.subSequence(3, 9);
        Assert.assertEquals("456789abc", forward.toString());
        Assert.assertEquals("6789", forward.subSequence(2, 4).toString());
        Assert.assertEquals("6789", forward.subSequence(2, 4).toString());
        Assert.assertEquals("6789", forward.subSequence(2, 4).reverse().toString());
        Assert.assertEquals("7", forward.subSequence(2, 4).reverse().subSequence(2, 1).toString());
        Assert.assertEquals("7", forward.subSequence(2, 4).reverse().subSequence(2, 1).reverse().toString());
        Assert.assertEquals("8", forward.subSequence(2, 4).subSequence(2, 1).toString());
        Assert.assertEquals("8", forward.subSequence(2, 4).subSequence(2, 1).reverse().toString());

        reversed = reader.subSequence(3, 9).reverse();
        Assert.assertEquals("456789abc", reversed.toString());
        Assert.assertEquals("789a", reversed.subSequence(2, 4).toString());
        Assert.assertEquals("789a", reversed.subSequence(2, 4).toString());
        Assert.assertEquals("789a", reversed.subSequence(2, 4).reverse().toString());
        Assert.assertEquals("8", reversed.subSequence(2, 4).reverse().subSequence(2, 1).toString());
        Assert.assertEquals("8", reversed.subSequence(2, 4).reverse().subSequence(2, 1).reverse().toString());
        Assert.assertEquals("9", reversed.subSequence(2, 4).subSequence(2, 1).toString());
        Assert.assertEquals("9", reversed.subSequence(2, 4).subSequence(2, 1).reverse().toString());
    }

    @Test
    public void testTrimInBigBuffer() throws IOException {
        addBuffer = BIG_BUFFER;
        textInner();
    }

    @Test
    public void testTrim() throws IOException {
        addBuffer = null;
        textInner();
    }

    private void textInner() {
        // Trim end
        Assert.assertEquals("123456789abcdefghijklmnopqrstuvwxyz",
                getBuffer("123456789abcdefghijklmnopqrstuvwxyz").trimEnd().toString());
        Assert.assertEquals("   123456789abcdefghijklmnopqrstuvwxyz",
                getBuffer("   123456789abcdefghijklmnopqrstuvwxyz   ").trimEnd().toString());
        Assert.assertEquals("", getBuffer("").trimEnd().toString());
        Assert.assertEquals("  \n 123456789abcdefghijklmnopqrstuvwxyz",
                getBuffer("  \n 123456789abcdefghijklmnopqrstuvwxyz \n  ").trimEnd().toString());
        Assert.assertEquals("", getBuffer("  \n    \n  ").trimEnd().toString());

        // Trim start
        Assert.assertEquals("123456789abcdefghijklmnopqrstuvwxyz",
                getBuffer("123456789abcdefghijklmnopqrstuvwxyz").trimStart().toString());
        Assert.assertEquals("123456789abcdefghijklmnopqrstuvwxyz   ",
                getBuffer("   123456789abcdefghijklmnopqrstuvwxyz   ").trimStart().toString());
        Assert.assertEquals("", getBuffer("").trimStart().toString());
        Assert.assertEquals("123456789abcdefghijklmnopqrstuvwxyz \n  ",
                getBuffer("  \n 123456789abcdefghijklmnopqrstuvwxyz \n  ").trimStart().toString());
        Assert.assertEquals("", getBuffer("  \n    \n  ").trimStart().toString());

        // Trim both
        Assert.assertEquals("123456789abcdefghijklmnopqrstuvwxyz",
                getBuffer("123456789abcdefghijklmnopqrstuvwxyz").trim().toString());
        Assert.assertEquals("123456789abcdefghijklmnopqrstuvwxyz",
                getBuffer("   123456789abcdefghijklmnopqrstuvwxyz   ").trim().toString());
        Assert.assertEquals("", getBuffer("").trim().toString());
        Assert.assertEquals("123456789abcdefghijklmnopqrstuvwxyz",
                getBuffer("  \n 123456789abcdefghijklmnopqrstuvwxyz \n  ").trim().toString());
        Assert.assertEquals("", getBuffer("  \n    \n  ").trim().toString());

        // Test empty
        Assert.assertFalse(getBuffer("123456789abcdefghijklmnopqrstuvwxyz").isEmpty());
        Assert.assertFalse(getBuffer("   123456789abcdefghijklmnopqrstuvwxyz   ").isEmpty());
        Assert.assertTrue(getBuffer("").isEmpty());
        Assert.assertFalse(getBuffer("  \n 123456789abcdefghijklmnopqrstuvwxyz \n  ").isEmpty());
        Assert.assertFalse(getBuffer("  \n    \n  ").isEmpty());

        // Test blank
        Assert.assertFalse(getBuffer("123456789abcdefghijklmnopqrstuvwxyz").isBlank());
        Assert.assertFalse(getBuffer("   123456789abcdefghijklmnopqrstuvwxyz   ").isBlank());
        Assert.assertTrue(getBuffer("").isBlank());
        Assert.assertFalse(getBuffer("  \n 123456789abcdefghijklmnopqrstuvwxyz \n  ").isBlank());
        Assert.assertTrue(getBuffer("  \n    \n  ").isBlank());
    }

    @Test
    public void testTrimInBigBufferReverse() throws IOException {
        addBuffer = BIG_BUFFER;
        textInnerReverse();
    }

    @Test
    public void testTrimReverse() throws IOException {
        addBuffer = null;
        textInnerReverse();
    }

    private void textInnerReverse() {
        // Trim end
        Assert.assertEquals("123456789abcdefghijklmnopqrstuvwxyz",
                getReverseBuffer("123456789abcdefghijklmnopqrstuvwxyz").trimEnd().toString());
        Assert.assertEquals("   123456789abcdefghijklmnopqrstuvwxyz",
                getReverseBuffer("   123456789abcdefghijklmnopqrstuvwxyz   ").trimEnd().toString());
        Assert.assertEquals("", getReverseBuffer("").trimEnd().toString());
        Assert.assertEquals("  \n 123456789abcdefghijklmnopqrstuvwxyz",
                getReverseBuffer("  \n 123456789abcdefghijklmnopqrstuvwxyz \n  ").trimEnd().toString());
        Assert.assertEquals("", getReverseBuffer("  \n    \n  ").trimEnd().toString());

        // Trim start
        Assert.assertEquals("123456789abcdefghijklmnopqrstuvwxyz",
                getReverseBuffer("123456789abcdefghijklmnopqrstuvwxyz").trimStart().toString());
        Assert.assertEquals("123456789abcdefghijklmnopqrstuvwxyz   ",
                getReverseBuffer("   123456789abcdefghijklmnopqrstuvwxyz   ").trimStart().toString());
        Assert.assertEquals("", getReverseBuffer("").trimStart().toString());
        Assert.assertEquals("123456789abcdefghijklmnopqrstuvwxyz \n  ",
                getReverseBuffer("  \n 123456789abcdefghijklmnopqrstuvwxyz \n  ").trimStart().toString());
        Assert.assertEquals("", getReverseBuffer("  \n    \n  ").trimStart().toString());

        // Trim both
        Assert.assertEquals("123456789abcdefghijklmnopqrstuvwxyz",
                getReverseBuffer("123456789abcdefghijklmnopqrstuvwxyz").trim().toString());
        Assert.assertEquals("123456789abcdefghijklmnopqrstuvwxyz",
                getReverseBuffer("   123456789abcdefghijklmnopqrstuvwxyz   ").trim().toString());
        Assert.assertEquals("", getReverseBuffer("").trim().toString());
        Assert.assertEquals("123456789abcdefghijklmnopqrstuvwxyz",
                getReverseBuffer("  \n 123456789abcdefghijklmnopqrstuvwxyz \n  ").trim().toString());
        Assert.assertEquals("", getReverseBuffer("  \n    \n  ").trim().toString());

        // Test empty
        Assert.assertFalse(getReverseBuffer("123456789abcdefghijklmnopqrstuvwxyz").isEmpty());
        Assert.assertFalse(getReverseBuffer("   123456789abcdefghijklmnopqrstuvwxyz   ").isEmpty());
        Assert.assertTrue(getReverseBuffer("").isEmpty());
        Assert.assertFalse(getReverseBuffer("  \n 123456789abcdefghijklmnopqrstuvwxyz \n  ").isEmpty());
        Assert.assertFalse(getReverseBuffer("  \n    \n  ").isEmpty());

        // Test blank
        Assert.assertFalse(getReverseBuffer("123456789abcdefghijklmnopqrstuvwxyz").isBlank());
        Assert.assertFalse(getReverseBuffer("   123456789abcdefghijklmnopqrstuvwxyz   ").isBlank());
        Assert.assertTrue(getReverseBuffer("").isBlank());
        Assert.assertFalse(getReverseBuffer("  \n 123456789abcdefghijklmnopqrstuvwxyz \n  ").isBlank());
        Assert.assertTrue(getReverseBuffer("  \n    \n  ").isBlank());
    }

    private Buffer getBuffer(final String input) {
        // Make sure the buffer always copes with being part of a bigger char
        // array.
        String in = input;
        if (addBuffer != null) {
            in = addBuffer + in + addBuffer;
        }

        final char[] chars = in.toCharArray();
        Buffer buffer = new CharBuffer(chars, 0, chars.length);

        if (addBuffer != null) {
            buffer = buffer.subSequence(addBuffer.length(), buffer.length() - (addBuffer.length() * 2));
        }

        return buffer;
    }

    private Buffer getReverseBuffer(final String input) {
        // Make sure the buffer always copes with being part of a bigger char
        // array.
        String in = input;
        if (addBuffer != null) {
            in = addBuffer + in + addBuffer;
        }

        final char[] chars = in.toCharArray();
        Buffer buffer = new CharBuffer(chars, 0, chars.length);

        if (addBuffer != null) {
            buffer = buffer.subSequence(addBuffer.length(), buffer.length() - (addBuffer.length() * 2));
        }

        return buffer.reverse();
    }
}
