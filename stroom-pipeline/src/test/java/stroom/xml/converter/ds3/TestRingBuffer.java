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

import stroom.util.test.StroomUnitTest;
import stroom.util.test.StroomJUnit4ClassRunner;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestRingBuffer extends StroomUnitTest {
    private enum Mode {
        STRING, CHAR_ARRAY, UNSAFE_COPY, COPY
    }

    @Test
    public void testString() {
        test(Mode.STRING);
    }

    @Test
    public void testCharArray() {
        test(Mode.CHAR_ARRAY);
    }

    @Test
    public void testUnsafeCopy() {
        test(Mode.UNSAFE_COPY);
    }

    @Test
    public void testCopy() {
        test(Mode.COPY);
    }

    @Test
    public void testCharAt() {
        final RingBuffer buffer = RingBuffer.fromString("0123456789");

        // Test charAt.
        buffer.append("0123456789012345678901234567890123");
        Assert.assertEquals("4567890123", buffer.toString());
        try {
            buffer.charAt(-1);
            Assert.fail("Should have thrown exception.");
        } catch (final IndexOutOfBoundsException e) {
            // Ignore
        }
        try {
            buffer.charAt(buffer.length());
            Assert.fail("Should have thrown exception.");
        } catch (final IndexOutOfBoundsException e) {
            // Ignore
        }

        Assert.assertEquals('4', buffer.charAt(0));
        Assert.assertEquals('5', buffer.charAt(1));
        Assert.assertEquals('6', buffer.charAt(2));
        Assert.assertEquals('7', buffer.charAt(3));
        Assert.assertEquals('8', buffer.charAt(4));
        Assert.assertEquals('9', buffer.charAt(5));
        Assert.assertEquals('0', buffer.charAt(6));
        Assert.assertEquals('1', buffer.charAt(7));
        Assert.assertEquals('2', buffer.charAt(8));
        Assert.assertEquals('3', buffer.charAt(9));
    }

    @Test
    public void testLength() {
        final RingBuffer buffer = RingBuffer.fromString("0123456789");
        Assert.assertEquals(10, buffer.length());
        buffer.setLength(5);
        Assert.assertEquals(5, buffer.length());
        Assert.assertEquals("01234", buffer.toString());
        buffer.append("999");
        Assert.assertEquals(8, buffer.length());
        Assert.assertEquals("01234999", buffer.toString());
        buffer.append("6667");
        Assert.assertEquals(10, buffer.length());
        Assert.assertEquals("2349996667", buffer.toString());
        buffer.setLength(5);
        Assert.assertEquals(5, buffer.length());
        Assert.assertEquals("23499", buffer.toString());
        buffer.setLength(10);
        Assert.assertEquals(10, buffer.length());
        Assert.assertEquals("2349996667", buffer.toString());

        try {
            buffer.setLength(-1);
            Assert.fail("You cannot set the length of a buffer less than 0");
        } catch (final IndexOutOfBoundsException e) {
            // Ignore.
        }

        try {
            buffer.setLength(11);
            Assert.fail("You cannot set the length of a buffer greater than the buffer size");
        } catch (final IndexOutOfBoundsException e) {
            // Ignore.
        }
    }

    @Test
    public void testBlank() {
        final RingBuffer buffer = RingBuffer.fromString("0123456789");
        Assert.assertFalse(buffer.isBlank());
        buffer.clear();
        Assert.assertTrue(buffer.isBlank());
        buffer.append("          ");
        Assert.assertEquals("          ", buffer.toString());
        Assert.assertTrue(buffer.isBlank());
        buffer.append("     ");
        Assert.assertEquals("          ", buffer.toString());
        Assert.assertTrue(buffer.isBlank());
        buffer.setLength(0);
        Assert.assertTrue(buffer.isBlank());
    }

    @Test
    public void testEmpty() {
        final RingBuffer buffer = RingBuffer.fromString("0123456789");
        Assert.assertFalse(buffer.isEmpty());
        buffer.clear();
        Assert.assertTrue(buffer.isEmpty());
        buffer.append("          ");
        Assert.assertEquals("          ", buffer.toString());
        Assert.assertFalse(buffer.isEmpty());
        buffer.append("     ");
        Assert.assertEquals("          ", buffer.toString());
        Assert.assertFalse(buffer.isEmpty());
        buffer.setLength(0);
        Assert.assertTrue(buffer.isEmpty());
    }

    @Test
    public void testTrim() {
        // Test trim end.
        final RingBuffer buffer = RingBuffer.fromString("0123456789");
        buffer.removeEnd(3);
        Assert.assertEquals("0123456", buffer.toString());
        buffer.removeEnd(3);
        Assert.assertEquals("0123", buffer.toString());
        buffer.removeEnd(3);
        Assert.assertEquals("0", buffer.toString());
        try {
            buffer.removeEnd(3);
            Assert.fail("You can't remove more content than exists");
        } catch (final IndexOutOfBoundsException e) {
            // Ignore.
        }
        Assert.assertEquals("0", buffer.toString());
        buffer.removeEnd(1);
        Assert.assertEquals("", buffer.toString());

        try {
            buffer.removeEnd(-1);
            Assert.fail("You can't remove a negative number of characters");
        } catch (final IllegalArgumentException e) {
            // Ignore.
        }

        // Test trim start.
        buffer.append("0123456789");
        buffer.removeStart(3);
        Assert.assertEquals("3456789", buffer.toString());
        buffer.removeStart(3);
        Assert.assertEquals("6789", buffer.toString());
        buffer.removeStart(3);
        Assert.assertEquals("9", buffer.toString());
        try {
            buffer.removeStart(3);
            Assert.fail("You can't remove more content than exists");
        } catch (final IndexOutOfBoundsException e) {
            // Ignore.
        }
        Assert.assertEquals("9", buffer.toString());
        buffer.removeStart(1);
        Assert.assertEquals("", buffer.toString());

        try {
            buffer.removeStart(-1);
            Assert.fail("You can't remove a negative number of characters");
        } catch (final IllegalArgumentException e) {
            // Ignore.
        }

        // Test space trimming.
        buffer.append("0123456789");
        buffer.trim();
        Assert.assertEquals("0123456789", buffer.toString());
        buffer.append("   ");
        Assert.assertEquals("3456789   ", buffer.toString());
        buffer.trim();
        Assert.assertEquals("3456789", buffer.toString());
        buffer.append("   ");
        Assert.assertEquals("3456789   ", buffer.toString());
        buffer.append(" 0123   ");
        Assert.assertEquals("   0123   ", buffer.toString());
        buffer.trim();
        Assert.assertEquals("0123", buffer.toString());
        buffer.append("   0123   ");
        Assert.assertEquals("   0123   ", buffer.toString());
        buffer.trimStart();
        Assert.assertEquals("0123   ", buffer.toString());
        buffer.append("   0123   ");
        Assert.assertEquals("   0123   ", buffer.toString());
        buffer.trimEnd();
        Assert.assertEquals("   0123", buffer.toString());
    }

    @Test
    public void testSubSequence() {
        final RingBuffer buffer = RingBuffer.fromString("0123456789");
        Assert.assertEquals("567", buffer.subSequence(5, 3).toString());
        Assert.assertEquals("89", buffer.subSequence(8, 2).toString());
        buffer.append("01234");
        Assert.assertEquals("5678901234", buffer.toString());
        Assert.assertEquals("012", buffer.subSequence(5, 3).toString());
        Assert.assertEquals("34", buffer.subSequence(8, 2).toString());
        Assert.assertEquals("34567", buffer.subSequence(8, 5).toString());

        try {
            Assert.assertEquals("34", buffer.subSequence(10, 2).toString());
            Assert.fail();
        } catch (final IndexOutOfBoundsException e) {
            // Ignore.
        }
        try {
            Assert.assertEquals("34", buffer.subSequence(-1, 2).toString());
            Assert.fail();
        } catch (final IndexOutOfBoundsException e) {
            // Ignore.
        }
        try {
            Assert.assertEquals("34", buffer.subSequence(8, 11).toString());
            Assert.fail();
        } catch (final IndexOutOfBoundsException e) {
            // Ignore.
        }
        try {
            Assert.assertEquals("34", buffer.subSequence(8, -1).toString());
            Assert.fail();
        } catch (final IndexOutOfBoundsException e) {
            // Ignore.
        }
    }

    @Test
    public void testEqualsAndHash() {
        final RingBuffer buffer1 = RingBuffer.fromString("0123456789");
        Assert.assertEquals("0123456789", buffer1.toString());
        final RingBuffer buffer2 = RingBuffer.fromString("0123456789");
        Assert.assertEquals("0123456789", buffer1.toString());

        testEquals(buffer1, buffer2, true);

        buffer1.append("01234");
        Assert.assertEquals("5678901234", buffer1.toString());
        testEquals(buffer1, buffer2, false);
        buffer2.append("01234");
        Assert.assertEquals("5678901234", buffer2.toString());

        testEquals(buffer1, buffer2, true);

        buffer1.append("9988998899");
        Assert.assertEquals("9988998899", buffer1.toString());
        testEquals(buffer1, buffer2, false);
        buffer2.append("9988998899");
        Assert.assertEquals("9988998899", buffer2.toString());

        testEquals(buffer1, buffer2, true);

        buffer1.append("8899");
        Assert.assertEquals("9988998899", buffer1.toString());

        testEquals(buffer1, buffer2, true);
    }

    private void testEquals(final RingBuffer buffer1, final RingBuffer buffer2, final boolean expected) {
        Assert.assertEquals(expected, buffer1.equals(buffer2));
        Assert.assertEquals(expected, buffer2.equals(buffer1));
        Assert.assertEquals(expected, buffer1.hashCode() == buffer2.hashCode());
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
        Assert.assertEquals("567899", getString(buffer, mode));
        buffer.append('9');
        Assert.assertEquals("5678999", getString(buffer, mode));
        buffer.append('9');
        Assert.assertEquals("56789999", getString(buffer, mode));
        buffer.append('9');
        Assert.assertEquals("567899999", getString(buffer, mode));
        buffer.append('9');
        Assert.assertEquals("5678999999", getString(buffer, mode));
        buffer.append('9');
        Assert.assertEquals("6789999999", getString(buffer, mode));
        buffer.append('9');
        Assert.assertEquals("7899999999", getString(buffer, mode));
        buffer.append('9');
        Assert.assertEquals("8999999999", getString(buffer, mode));
        buffer.append('9');
        Assert.assertEquals("9999999999", getString(buffer, mode));

        // Test string appends.
        buffer.append("0123456789");
        Assert.assertEquals("0123456789", getString(buffer, mode));
        buffer.append("01234");
        Assert.assertEquals("5678901234", getString(buffer, mode));
        buffer.append("012345678901234567890123456789");
        Assert.assertEquals("0123456789", getString(buffer, mode));
        buffer.append("0123456789012345678901234567890123");
        Assert.assertEquals("4567890123", getString(buffer, mode));

        // Test clear.
        buffer.clear();
        Assert.assertEquals("", getString(buffer, mode));
    }

    private void testMove(final int increment, final String expected, final RingBuffer buffer, final Mode mode) {
        buffer.move(increment);
        Assert.assertEquals(expected, getString(buffer, mode));
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
}
