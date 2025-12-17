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

package stroom.pipeline.xml.converter.ds3;


import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

class TestCharBuffer extends StroomUnitTest {

    private static final String BIG_BUFFER = "this is some additional text to put in the buffer";
    private String addBuffer = null;

    @Test
    void test() throws IOException {
        final StringReader stringReader = new StringReader("123456789abcdefghijklmnopqrstuvwxyz");
        final DS3Reader reader = new DS3Reader(stringReader, 100, 200000);

        reader.fillBuffer();
        assertThat(reader.isEof()).isTrue();

        Buffer forward = reader.subSequence(0, 23);
        assertThat(forward.toString()).isEqualTo("123456789abcdefghijklmn");

        Buffer reversed = reader.subSequence(0, 23).reverse();
        assertThat(reversed.toString()).isEqualTo("123456789abcdefghijklmn");

        final StringBuilder sb = new StringBuilder();

        sb.setLength(0);
        for (int i = 0; i < 10; i++) {
            sb.append(forward.charAt(i));
        }
        assertThat(sb.toString()).isEqualTo("123456789a");

        sb.setLength(0);
        for (int i = 0; i < 10; i++) {
            sb.append(reversed.charAt(i));
        }
        assertThat(sb.toString()).isEqualTo("nmlkjihgfe");

        forward.move(10);
        reversed.move(10);

        assertThat(forward.toString()).isEqualTo("bcdefghijklmn");
        assertThat(reversed.toString()).isEqualTo("123456789abcd");

        // Do further tests to prove reversal
        forward = reader.subSequence(3, 9);
        assertThat(forward.toString()).isEqualTo("456789abc");
        assertThat(forward.subSequence(2, 4).toString()).isEqualTo("6789");
        assertThat(forward.subSequence(2, 4).toString()).isEqualTo("6789");
        assertThat(forward.subSequence(2, 4).reverse().toString()).isEqualTo("6789");
        assertThat(forward.subSequence(2, 4).reverse().subSequence(2, 1).toString()).isEqualTo("7");
        assertThat(forward.subSequence(2, 4).reverse().subSequence(2, 1).reverse().toString()).isEqualTo("7");
        assertThat(forward.subSequence(2, 4).subSequence(2, 1).toString()).isEqualTo("8");
        assertThat(forward.subSequence(2, 4).subSequence(2, 1).reverse().toString()).isEqualTo("8");

        reversed = reader.subSequence(3, 9).reverse();
        assertThat(reversed.toString()).isEqualTo("456789abc");
        assertThat(reversed.subSequence(2, 4).toString()).isEqualTo("789a");
        assertThat(reversed.subSequence(2, 4).toString()).isEqualTo("789a");
        assertThat(reversed.subSequence(2, 4).reverse().toString()).isEqualTo("789a");
        assertThat(reversed.subSequence(2, 4).reverse().subSequence(2, 1).toString()).isEqualTo("8");
        assertThat(reversed.subSequence(2, 4).reverse().subSequence(2, 1).reverse().toString()).isEqualTo("8");
        assertThat(reversed.subSequence(2, 4).subSequence(2, 1).toString()).isEqualTo("9");
        assertThat(reversed.subSequence(2, 4).subSequence(2, 1).reverse().toString()).isEqualTo("9");
    }

    @Test
    void testTrimInBigBuffer() throws IOException {
        addBuffer = BIG_BUFFER;
        textInner();
    }

    @Test
    void testTrim() throws IOException {
        addBuffer = null;
        textInner();
    }

    private void textInner() {
        // Trim end
        assertThat(getBuffer("123456789abcdefghijklmnopqrstuvwxyz").trimEnd().toString()).isEqualTo(
                "123456789abcdefghijklmnopqrstuvwxyz");
        assertThat(getBuffer("   123456789abcdefghijklmnopqrstuvwxyz   ").trimEnd().toString()).isEqualTo(
                "   123456789abcdefghijklmnopqrstuvwxyz");
        assertThat(getBuffer("").trimEnd().toString()).isEqualTo("");
        assertThat(getBuffer("  \n 123456789abcdefghijklmnopqrstuvwxyz \n  ").trimEnd().toString()).isEqualTo(
                "  \n 123456789abcdefghijklmnopqrstuvwxyz");
        assertThat(getBuffer("  \n    \n  ").trimEnd().toString()).isEqualTo("");

        // Trim start
        assertThat(getBuffer("123456789abcdefghijklmnopqrstuvwxyz").trimStart().toString()).isEqualTo(
                "123456789abcdefghijklmnopqrstuvwxyz");
        assertThat(getBuffer("   123456789abcdefghijklmnopqrstuvwxyz   ").trimStart().toString()).isEqualTo(
                "123456789abcdefghijklmnopqrstuvwxyz   ");
        assertThat(getBuffer("").trimStart().toString()).isEqualTo("");
        assertThat(getBuffer("  \n 123456789abcdefghijklmnopqrstuvwxyz \n  ").trimStart().toString()).isEqualTo(
                "123456789abcdefghijklmnopqrstuvwxyz \n  ");
        assertThat(getBuffer("  \n    \n  ").trimStart().toString()).isEqualTo("");

        // Trim both
        assertThat(getBuffer("123456789abcdefghijklmnopqrstuvwxyz").trim().toString()).isEqualTo(
                "123456789abcdefghijklmnopqrstuvwxyz");
        assertThat(getBuffer("   123456789abcdefghijklmnopqrstuvwxyz   ").trim().toString()).isEqualTo(
                "123456789abcdefghijklmnopqrstuvwxyz");
        assertThat(getBuffer("").trim().toString()).isEqualTo("");
        assertThat(getBuffer("  \n 123456789abcdefghijklmnopqrstuvwxyz \n  ").trim().toString()).isEqualTo(
                "123456789abcdefghijklmnopqrstuvwxyz");
        assertThat(getBuffer("  \n    \n  ").trim().toString()).isEqualTo("");

        // Test empty
        assertThat(getBuffer("123456789abcdefghijklmnopqrstuvwxyz").isEmpty()).isFalse();
        assertThat(getBuffer("   123456789abcdefghijklmnopqrstuvwxyz   ").isEmpty()).isFalse();
        assertThat(getBuffer("").isEmpty()).isTrue();
        assertThat(getBuffer("  \n 123456789abcdefghijklmnopqrstuvwxyz \n  ").isEmpty()).isFalse();
        assertThat(getBuffer("  \n    \n  ").isEmpty()).isFalse();

        // Test blank
        assertThat(getBuffer("123456789abcdefghijklmnopqrstuvwxyz").isBlank()).isFalse();
        assertThat(getBuffer("   123456789abcdefghijklmnopqrstuvwxyz   ").isBlank()).isFalse();
        assertThat(getBuffer("").isBlank()).isTrue();
        assertThat(getBuffer("  \n 123456789abcdefghijklmnopqrstuvwxyz \n  ").isBlank()).isFalse();
        assertThat(getBuffer("  \n    \n  ").isBlank()).isTrue();
    }

    @Test
    void testTrimInBigBufferReverse() throws IOException {
        addBuffer = BIG_BUFFER;
        textInnerReverse();
    }

    @Test
    void testTrimReverse() throws IOException {
        addBuffer = null;
        textInnerReverse();
    }

    private void textInnerReverse() {
        // Trim end
        assertThat(getReverseBuffer("123456789abcdefghijklmnopqrstuvwxyz").trimEnd().toString()).isEqualTo(
                "123456789abcdefghijklmnopqrstuvwxyz");
        assertThat(getReverseBuffer("   123456789abcdefghijklmnopqrstuvwxyz   ").trimEnd().toString()).isEqualTo(
                "   123456789abcdefghijklmnopqrstuvwxyz");
        assertThat(getReverseBuffer("").trimEnd().toString()).isEqualTo("");
        assertThat(getReverseBuffer("  \n 123456789abcdefghijklmnopqrstuvwxyz \n  ").trimEnd().toString()).isEqualTo(
                "  \n 123456789abcdefghijklmnopqrstuvwxyz");
        assertThat(getReverseBuffer("  \n    \n  ").trimEnd().toString()).isEqualTo("");

        // Trim start
        assertThat(getReverseBuffer("123456789abcdefghijklmnopqrstuvwxyz").trimStart().toString()).isEqualTo(
                "123456789abcdefghijklmnopqrstuvwxyz");
        assertThat(getReverseBuffer("   123456789abcdefghijklmnopqrstuvwxyz   ").trimStart().toString()).isEqualTo(
                "123456789abcdefghijklmnopqrstuvwxyz   ");
        assertThat(getReverseBuffer("").trimStart().toString()).isEqualTo("");
        assertThat(getReverseBuffer("  \n 123456789abcdefghijklmnopqrstuvwxyz \n  ").trimStart().toString()).isEqualTo(
                "123456789abcdefghijklmnopqrstuvwxyz \n  ");
        assertThat(getReverseBuffer("  \n    \n  ").trimStart().toString()).isEqualTo("");

        // Trim both
        assertThat(getReverseBuffer("123456789abcdefghijklmnopqrstuvwxyz").trim().toString()).isEqualTo(
                "123456789abcdefghijklmnopqrstuvwxyz");
        assertThat(getReverseBuffer("   123456789abcdefghijklmnopqrstuvwxyz   ").trim().toString()).isEqualTo(
                "123456789abcdefghijklmnopqrstuvwxyz");
        assertThat(getReverseBuffer("").trim().toString()).isEqualTo("");
        assertThat(getReverseBuffer("  \n 123456789abcdefghijklmnopqrstuvwxyz \n  ").trim().toString()).isEqualTo(
                "123456789abcdefghijklmnopqrstuvwxyz");
        assertThat(getReverseBuffer("  \n    \n  ").trim().toString()).isEqualTo("");

        // Test empty
        assertThat(getReverseBuffer("123456789abcdefghijklmnopqrstuvwxyz").isEmpty()).isFalse();
        assertThat(getReverseBuffer("   123456789abcdefghijklmnopqrstuvwxyz   ").isEmpty()).isFalse();
        assertThat(getReverseBuffer("").isEmpty()).isTrue();
        assertThat(getReverseBuffer("  \n 123456789abcdefghijklmnopqrstuvwxyz \n  ").isEmpty()).isFalse();
        assertThat(getReverseBuffer("  \n    \n  ").isEmpty()).isFalse();

        // Test blank
        assertThat(getReverseBuffer("123456789abcdefghijklmnopqrstuvwxyz").isBlank()).isFalse();
        assertThat(getReverseBuffer("   123456789abcdefghijklmnopqrstuvwxyz   ").isBlank()).isFalse();
        assertThat(getReverseBuffer("").isBlank()).isTrue();
        assertThat(getReverseBuffer("  \n 123456789abcdefghijklmnopqrstuvwxyz \n  ").isBlank()).isFalse();
        assertThat(getReverseBuffer("  \n    \n  ").isBlank()).isTrue();
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
