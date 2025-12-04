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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

class TestRingBuffer extends StroomUnitTest {

    @Test
    void testString() {
        test(Mode.STRING);
    }

    @Test
    void testCharArray() {
        test(Mode.CHAR_ARRAY);
    }

    @Test
    void testUnsafeCopy() {
        test(Mode.UNSAFE_COPY);
    }

    @Test
    void testCopy() {
        test(Mode.COPY);
    }

    @Test
    void testCharAt() {
        final RingBuffer buffer = RingBuffer.fromString("0123456789");

        // Test charAt.
        buffer.append("0123456789012345678901234567890123");
        assertThat(buffer.toString()).isEqualTo("4567890123");
        try {
            buffer.charAt(-1);
            fail("Should have thrown exception.");
        } catch (final IndexOutOfBoundsException e) {
            // Ignore
        }
        try {
            buffer.charAt(buffer.length());
            fail("Should have thrown exception.");
        } catch (final IndexOutOfBoundsException e) {
            // Ignore
        }

        assertThat(buffer.charAt(0)).isEqualTo('4');
        assertThat(buffer.charAt(1)).isEqualTo('5');
        assertThat(buffer.charAt(2)).isEqualTo('6');
        assertThat(buffer.charAt(3)).isEqualTo('7');
        assertThat(buffer.charAt(4)).isEqualTo('8');
        assertThat(buffer.charAt(5)).isEqualTo('9');
        assertThat(buffer.charAt(6)).isEqualTo('0');
        assertThat(buffer.charAt(7)).isEqualTo('1');
        assertThat(buffer.charAt(8)).isEqualTo('2');
        assertThat(buffer.charAt(9)).isEqualTo('3');
    }

    @Test
    void testLength() {
        final RingBuffer buffer = RingBuffer.fromString("0123456789");
        assertThat(buffer.length()).isEqualTo(10);
        buffer.setLength(5);
        assertThat(buffer.length()).isEqualTo(5);
        assertThat(buffer.toString()).isEqualTo("01234");
        buffer.append("999");
        assertThat(buffer.length()).isEqualTo(8);
        assertThat(buffer.toString()).isEqualTo("01234999");
        buffer.append("6667");
        assertThat(buffer.length()).isEqualTo(10);
        assertThat(buffer.toString()).isEqualTo("2349996667");
        buffer.setLength(5);
        assertThat(buffer.length()).isEqualTo(5);
        assertThat(buffer.toString()).isEqualTo("23499");
        buffer.setLength(10);
        assertThat(buffer.length()).isEqualTo(10);
        assertThat(buffer.toString()).isEqualTo("2349996667");

        try {
            buffer.setLength(-1);
            fail("You cannot set the length of a buffer less than 0");
        } catch (final IndexOutOfBoundsException e) {
            // Ignore.
        }

        try {
            buffer.setLength(11);
            fail("You cannot set the length of a buffer greater than the buffer size");
        } catch (final IndexOutOfBoundsException e) {
            // Ignore.
        }
    }

    @Test
    void testBlank() {
        final RingBuffer buffer = RingBuffer.fromString("0123456789");
        assertThat(buffer.isBlank()).isFalse();
        buffer.clear();
        assertThat(buffer.isBlank()).isTrue();
        buffer.append("          ");
        assertThat(buffer.toString()).isEqualTo("          ");
        assertThat(buffer.isBlank()).isTrue();
        buffer.append("     ");
        assertThat(buffer.toString()).isEqualTo("          ");
        assertThat(buffer.isBlank()).isTrue();
        buffer.setLength(0);
        assertThat(buffer.isBlank()).isTrue();
    }

    @Test
    void testEmpty() {
        final RingBuffer buffer = RingBuffer.fromString("0123456789");
        assertThat(buffer.isEmpty()).isFalse();
        buffer.clear();
        assertThat(buffer.isEmpty()).isTrue();
        buffer.append("          ");
        assertThat(buffer.toString()).isEqualTo("          ");
        assertThat(buffer.isEmpty()).isFalse();
        buffer.append("     ");
        assertThat(buffer.toString()).isEqualTo("          ");
        assertThat(buffer.isEmpty()).isFalse();
        buffer.setLength(0);
        assertThat(buffer.isEmpty()).isTrue();
    }

    @Test
    void testTrim() {
        // Test trim end.
        final RingBuffer buffer = RingBuffer.fromString("0123456789");
        buffer.removeEnd(3);
        assertThat(buffer.toString()).isEqualTo("0123456");
        buffer.removeEnd(3);
        assertThat(buffer.toString()).isEqualTo("0123");
        buffer.removeEnd(3);
        assertThat(buffer.toString()).isEqualTo("0");
        try {
            buffer.removeEnd(3);
            fail("You can't remove more content than exists");
        } catch (final IndexOutOfBoundsException e) {
            // Ignore.
        }
        assertThat(buffer.toString()).isEqualTo("0");
        buffer.removeEnd(1);
        assertThat(buffer.toString()).isEqualTo("");

        try {
            buffer.removeEnd(-1);
            fail("You can't remove a negative number of characters");
        } catch (final IllegalArgumentException e) {
            // Ignore.
        }

        // Test trim start.
        buffer.append("0123456789");
        buffer.removeStart(3);
        assertThat(buffer.toString()).isEqualTo("3456789");
        buffer.removeStart(3);
        assertThat(buffer.toString()).isEqualTo("6789");
        buffer.removeStart(3);
        assertThat(buffer.toString()).isEqualTo("9");
        try {
            buffer.removeStart(3);
            fail("You can't remove more content than exists");
        } catch (final IndexOutOfBoundsException e) {
            // Ignore.
        }
        assertThat(buffer.toString()).isEqualTo("9");
        buffer.removeStart(1);
        assertThat(buffer.toString()).isEqualTo("");

        try {
            buffer.removeStart(-1);
            fail("You can't remove a negative number of characters");
        } catch (final IllegalArgumentException e) {
            // Ignore.
        }

        // Test space trimming.
        buffer.append("0123456789");
        buffer.trim();
        assertThat(buffer.toString()).isEqualTo("0123456789");
        buffer.append("   ");
        assertThat(buffer.toString()).isEqualTo("3456789   ");
        buffer.trim();
        assertThat(buffer.toString()).isEqualTo("3456789");
        buffer.append("   ");
        assertThat(buffer.toString()).isEqualTo("3456789   ");
        buffer.append(" 0123   ");
        assertThat(buffer.toString()).isEqualTo("   0123   ");
        buffer.trim();
        assertThat(buffer.toString()).isEqualTo("0123");
        buffer.append("   0123   ");
        assertThat(buffer.toString()).isEqualTo("   0123   ");
        buffer.trimStart();
        assertThat(buffer.toString()).isEqualTo("0123   ");
        buffer.append("   0123   ");
        assertThat(buffer.toString()).isEqualTo("   0123   ");
        buffer.trimEnd();
        assertThat(buffer.toString()).isEqualTo("   0123");
    }

    @Test
    void testSubSequence() {
        final RingBuffer buffer = RingBuffer.fromString("0123456789");
        assertThat(buffer.subSequence(5, 3).toString()).isEqualTo("567");
        assertThat(buffer.subSequence(8, 2).toString()).isEqualTo("89");
        buffer.append("01234");
        assertThat(buffer.toString()).isEqualTo("5678901234");
        assertThat(buffer.subSequence(5, 3).toString()).isEqualTo("012");
        assertThat(buffer.subSequence(8, 2).toString()).isEqualTo("34");
        assertThat(buffer.subSequence(8, 5).toString()).isEqualTo("34567");

        assertThatThrownBy(() -> buffer.subSequence(10, 2)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> buffer.subSequence(-1, 2)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> buffer.subSequence(8, 11)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> buffer.subSequence(8, -1)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void testEqualsAndHash() {
        final RingBuffer buffer1 = RingBuffer.fromString("0123456789");
        assertThat(buffer1.toString()).isEqualTo("0123456789");
        final RingBuffer buffer2 = RingBuffer.fromString("0123456789");
        assertThat(buffer1.toString()).isEqualTo("0123456789");

        testEquals(buffer1, buffer2, true);

        buffer1.append("01234");
        assertThat(buffer1.toString()).isEqualTo("5678901234");
        testEquals(buffer1, buffer2, false);
        buffer2.append("01234");
        assertThat(buffer2.toString()).isEqualTo("5678901234");

        testEquals(buffer1, buffer2, true);

        buffer1.append("9988998899");
        assertThat(buffer1.toString()).isEqualTo("9988998899");
        testEquals(buffer1, buffer2, false);
        buffer2.append("9988998899");
        assertThat(buffer2.toString()).isEqualTo("9988998899");

        testEquals(buffer1, buffer2, true);

        buffer1.append("8899");
        assertThat(buffer1.toString()).isEqualTo("9988998899");

        testEquals(buffer1, buffer2, true);
    }

    private void testEquals(final RingBuffer buffer1, final RingBuffer buffer2, final boolean expected) {
        assertThat(buffer1.equals(buffer2)).isEqualTo(expected);
        assertThat(buffer2.equals(buffer1)).isEqualTo(expected);
        assertThat(buffer1.hashCode() == buffer2.hashCode()).isEqualTo(expected);
    }

    private void test(final Mode mode) {
        final RingBuffer buffer = RingBuffer.fromString("0123456789");

        // Test move.
        testMove(1, "123456789", buffer, mode);
        testMove(1, "23456789", buffer, mode);
        testMove(1, "3456789", buffer, mode);
        testMove(1, "456789", buffer, mode);
        testMove(1, "56789", buffer, mode);
        testMove(1, "6789", buffer, mode);
        testMove(1, "789", buffer, mode);
        testMove(1, "89", buffer, mode);
        testMove(1, "9", buffer, mode);
        testMove(1, "", buffer, mode);
        testMove(1, "", buffer, mode);
        testMove(1, "", buffer, mode);
        testMove(-1, "9", buffer, mode);
        testMove(-1, "89", buffer, mode);
        testMove(-1, "789", buffer, mode);
        testMove(-1, "6789", buffer, mode);
        testMove(-1, "56789", buffer, mode);
        testMove(-1, "456789", buffer, mode);
        testMove(-1, "3456789", buffer, mode);
        testMove(-1, "23456789", buffer, mode);
        testMove(-1, "123456789", buffer, mode);
        testMove(-1, "0123456789", buffer, mode);
        testMove(-1, "0123456789", buffer, mode);
        testMove(-1, "0123456789", buffer, mode);

        testMove(-10, "0123456789", buffer, mode);
        testMove(10, "", buffer, mode);
        testMove(-5, "56789", buffer, mode);
        testMove(-5, "0123456789", buffer, mode);
        testMove(-5, "0123456789", buffer, mode);
        testMove(5, "56789", buffer, mode);
        testMove(10, "", buffer, mode);

        testMove(-3, "789", buffer, mode);
        testMove(-3, "456789", buffer, mode);
        testMove(-3, "123456789", buffer, mode);
        testMove(-3, "0123456789", buffer, mode);
        testMove(-3, "0123456789", buffer, mode);
        testMove(3, "3456789", buffer, mode);
        testMove(3, "6789", buffer, mode);
        testMove(3, "9", buffer, mode);
        testMove(3, "", buffer, mode);
        testMove(3, "", buffer, mode);

        // Test wrapping characters.
        testMove(-5, "56789", buffer, mode);
        buffer.append('9');
        assertThat(getString(buffer, mode)).isEqualTo("567899");
        buffer.append('9');
        assertThat(getString(buffer, mode)).isEqualTo("5678999");
        buffer.append('9');
        assertThat(getString(buffer, mode)).isEqualTo("56789999");
        buffer.append('9');
        assertThat(getString(buffer, mode)).isEqualTo("567899999");
        buffer.append('9');
        assertThat(getString(buffer, mode)).isEqualTo("5678999999");
        buffer.append('9');
        assertThat(getString(buffer, mode)).isEqualTo("6789999999");
        buffer.append('9');
        assertThat(getString(buffer, mode)).isEqualTo("7899999999");
        buffer.append('9');
        assertThat(getString(buffer, mode)).isEqualTo("8999999999");
        buffer.append('9');
        assertThat(getString(buffer, mode)).isEqualTo("9999999999");

        // Test string appends.
        buffer.append("0123456789");
        assertThat(getString(buffer, mode)).isEqualTo("0123456789");
        buffer.append("01234");
        assertThat(getString(buffer, mode)).isEqualTo("5678901234");
        buffer.append("012345678901234567890123456789");
        assertThat(getString(buffer, mode)).isEqualTo("0123456789");
        buffer.append("0123456789012345678901234567890123");
        assertThat(getString(buffer, mode)).isEqualTo("4567890123");

        // Test clear.
        buffer.clear();
        assertThat(getString(buffer, mode)).isEqualTo("");
    }

    private void testMove(final int increment, final String expected, final RingBuffer buffer, final Mode mode) {
        buffer.move(increment);
        assertThat(getString(buffer, mode)).isEqualTo(expected);
    }

    private String getString(final RingBuffer buffer, final Mode mode) {
        switch (mode) {
            case STRING:
                return buffer.toString();

            case CHAR_ARRAY:
                final char[] chars = buffer.toCharArray();
                return new String(chars, 0, chars.length);

            case UNSAFE_COPY:
                return buffer.unsafeCopy().toString();

            case COPY:
                return buffer.copy().toString();
        }

        return null;
    }

    private enum Mode {
        STRING,
        CHAR_ARRAY,
        UNSAFE_COPY,
        COPY
    }
}
