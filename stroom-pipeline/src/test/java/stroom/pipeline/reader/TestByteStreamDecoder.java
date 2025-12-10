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

import stroom.bytebuffer.ByteArrayUtils;
import stroom.pipeline.reader.ByteStreamDecoder.DecodedChar;
import stroom.pipeline.reader.ByteStreamDecoder.DecoderException;
import stroom.pipeline.reader.ByteStreamDecoder.Mode;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.string.ByteArrayBuilder;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestByteStreamDecoder {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestByteStreamDecoder.class);

    private Mode mode = Mode.STRICT;

    @BeforeEach
    void setUp() {
        mode = Mode.STRICT;
    }

    @Test
    void testEmptySupplier() {
        final Charset charset = StandardCharsets.UTF_8;
        final Supplier<Byte> supplier = () -> null;
        final ByteStreamDecoder byteStreamDecoder = new ByteStreamDecoder(charset, supplier);

        DecodedChar decodedChar = byteStreamDecoder.decodeNextChar();
        assertThat(decodedChar)
                .isNull();

        // Once more for luck
        decodedChar = byteStreamDecoder.decodeNextChar();
        assertThat(decodedChar)
                .isNull();
    }

    @Test
    void testByteStreamDecoder() throws IOException {

        final Charset charset = StandardCharsets.UTF_8;

        final String input = "𝄞🌉hello↵byeᚏ"; // 𝄞 is 4 bytes, 𩸽 is 4 bytes, ↵ is 2 bytes, ᚏ is three
        final int inputByteCount = input.getBytes(charset).length;
        LOGGER.info("Input: {}, byteCount: {}, bytesPerChar: {}",
                input, inputByteCount, (double) inputByteCount / input.length());

        final MyByteBuffer myByteBuffer = new MyByteBuffer();
        myByteBuffer.write(input.getBytes(charset));

        final StringBuilder outputStringBuilder = new StringBuilder();

        final AtomicInteger byteOffset = new AtomicInteger(0);
        final ByteStreamDecoder byteStreamDecoder = new ByteStreamDecoder(
                charset,
                () ->
                        myByteBuffer.getByte(byteOffset.getAndIncrement()));

        while (byteOffset.get() < input.getBytes(charset).length) {

            final DecodedChar decodedChar = byteStreamDecoder.decodeNextChar();

            outputStringBuilder.append(decodedChar.getAsString());
        }

        LOGGER.info("outputStringBuilder: {}", outputStringBuilder);

        assertThat(outputStringBuilder.toString())
                .isEqualTo(input);

        assertThat(byteOffset)
                .hasValue(inputByteCount);
    }

    @Test
    void testFlagEmoji() throws IOException {
        final String gbFlag = fromUnicode(0x1F1EC, 0x1F1E7);
        doTest(gbFlag);
    }

    @Test
    void testCompoundEmoji() throws IOException {
        final String womanMediumDarkSkinToneWhiteHair = fromUnicode(
                0x1F469,
                0x1F3FE,
                0x200D,
                0x1F9B3);
        doTest(womanMediumDarkSkinToneWhiteHair);
    }

    @Test
    void testHeartsEmoji() throws IOException {
        final String heartsEmoji = "💕";
        doTest(heartsEmoji);
    }

    @Test
    void testSmilyEmoji() throws IOException {
        final String heartsEmoji = "😀";
        doTest(heartsEmoji);
    }

    @Test
    void testLeftToRightMark() throws IOException {
        final String leftToRightMark = "\u200e";
        doTest(leftToRightMark);
    }

    @Test
    void testMalformed_lenient() throws IOException {
        mode = Mode.LENIENT;
        final Charset charset = StandardCharsets.UTF_8;
        final byte[] bytes = new ByteArrayBuilder(charset)
                .append(0x80)
                .append("hello")
                .toByteArray();

        final String output = doTest(bytes, charset);
        assertThat(output)
                .isEqualTo(DecodedChar.UNKNOWN_CHAR_REPLACEMENT_STRING);
    }

    @Test
    void testMalformed_lenient2() {
        mode = Mode.LENIENT;
        final String validStr = "hello this is my little string";
        final Charset charset = StandardCharsets.UTF_8;
        final byte[] bytes = new ByteArrayBuilder(charset)
                .append(0x80)
                .append(0x8E)
                .append(validStr)
                .toByteArray();

        final int expectedValidCharCount = validStr.length();
        final int expectedUnknownCharCount = 1; // One replacement char for the two malformed bytes

        doMalformedTest(
                bytes,
                charset,
                expectedValidCharCount,
                expectedUnknownCharCount,
                "�hello this is my little string");
    }

    @Test
    void testMalformed_lenient3() {
        mode = Mode.LENIENT;
        final String validStr1 = "hello this is";
        final String validStr2 = "my little string";
        final Charset charset = StandardCharsets.UTF_8;
        // bad bytes in two diff places
        final byte[] bytes = new ByteArrayBuilder(charset)
                .append(0x80)
                .append(validStr1)
                .append(0x80)
                .append(0x8E)
                .append(validStr2)
                .append(0x8E)
                .toByteArray();

        final int expectedValidCharCount = validStr1.length() + validStr2.length();
        final int expectedUnknownCharCount = 3; // two replacement chars as bad bytes in two places

        doMalformedTest(
                bytes,
                charset,
                expectedValidCharCount,
                expectedUnknownCharCount,
                "�hello this is�my little string�");
    }

    @Test
    void testMalformed_lenient4() {
        mode = Mode.LENIENT;
        final Charset charset = StandardCharsets.UTF_8;
        final ByteArrayBuilder builder = new ByteArrayBuilder(charset)
                .append("This bit is ok");
        // 40 bytes of rubbish
        for (int i = 0; i < 20; i++) {
            builder.append(0x80)
                    .append(0x8E);
        }
        final byte[] bytes = builder.toByteArray();

        final int expectedValidCharCount = 0;
        final int expectedUnknownCharCount = 1; // One replacement char for the two malformed bytes

        Assertions.assertThatThrownBy(
                        () -> {
                            doMalformedTest(
                                    bytes,
                                    charset,
                                    expectedValidCharCount,
                                    expectedUnknownCharCount,
                                    "�");
                        })
                .isInstanceOf(DecoderException.class)
                .hasMessageContaining("offset 14"); // offset of just after 'ok'
    }

    @Test
    void testMalformed_lenient5() {
        mode = Mode.LENIENT;
        final String validStr1 = "hello this is";
        final String validStr2 = "my little string";
        final Charset charset = StandardCharsets.UTF_8;
        // bad bytes in two diff places
        final byte[] bytes = new ByteArrayBuilder(charset)
                .append("\"Da")
                .append(0x80)
                .append(0x8E)
                .append("te\"")
                .toByteArray();

        final int expectedValidCharCount = 6;
        final int expectedUnknownCharCount = 1; // two replacement chars as bad bytes in two places

        doMalformedTest(
                bytes,
                charset,
                expectedValidCharCount,
                expectedUnknownCharCount,
                "\"Da�te\"");
    }

    @Test
    void testMalformed_strict() throws IOException {
        mode = Mode.STRICT;
        final String validStr1 = "hello this is";
        final String validStr2 = "my little string";
        final Charset charset = StandardCharsets.UTF_8;
        // bad bytes in two diff places
        final byte[] bytes = new ByteArrayBuilder(charset)
                .append(0x80)
                .append(validStr1)
                .append(0x8E)
                .append(validStr2)
                .toByteArray();

        final int expectedValidCharCount = validStr1.length() + validStr2.length();
        final int expectedUnknownCharCount = 2; // two replacement chars as bad bytes in two places

        Assertions.assertThatThrownBy(
                        () -> {
                            doMalformedTest(
                                    bytes,
                                    charset,
                                    expectedValidCharCount,
                                    expectedUnknownCharCount,
                                    "�hello this is�my little string");
                        })
                .isInstanceOf(DecoderException.class);
    }

    private void doMalformedTest(final byte[] bytes,
                                 final Charset charset,
                                 final int expectedValidCharCount,
                                 final int expectedUnknownCharCount,
                                 final String expectedOutput) {
        LOGGER.debug("bytes: {}", ByteArrayUtils.byteArrayToHex(bytes));

        final AtomicInteger byteOffset = new AtomicInteger(0);
        final ByteStreamDecoder byteStreamDecoder = new ByteStreamDecoder(charset, mode, () -> {
            final int offset = byteOffset.getAndIncrement();
            if (offset < bytes.length) {
                return bytes[offset];
            } else {
                return null;
            }
        });

        final List<DecodedChar> decodedChars = new ArrayList<>();
        DecodedChar decodedChar;
        int iterations = 0;
        do {
            decodedChar = byteStreamDecoder.decodeNextChar();
            LOGGER.debug("decodedChar: {}", decodedChar);
            NullSafe.consume(decodedChar, decodedChars::add);
            if (++iterations > 100) {
                throw new RuntimeException(LogUtil.message("Too many iterations"));
            }
        } while (decodedChar != null);

        final String output = decodedChars.stream()
                .map(DecodedChar::getAsString)
                .collect(Collectors.joining());

        LOGGER.debug("output: '{}'", output);

        assertThat(output)
                .isEqualTo(expectedOutput);

        assertThat(decodedChars)
                .hasSize(expectedValidCharCount + expectedUnknownCharCount);
        assertThat(decodedChars.stream().filter(DecodedChar::isUnknown).count())
                .isEqualTo(expectedUnknownCharCount);
    }

    @Test()
    void testMissingBytes() throws IOException {
        mode = Mode.LENIENT;

        LOGGER.debug("unknown char replacement: {}", DecodedChar.UNKNOWN_CHAR_REPLACEMENT);
        final Charset charset = StandardCharsets.UTF_8;
        final String leftToRightMark = "\u200e";
        final byte[] bytes = leftToRightMark.getBytes(charset);
        // Drop the last byte
        final byte[] truncatedBytes = Arrays.copyOf(bytes, bytes.length - 1);
        assertThat(bytes)
                .hasSize(3);
        assertThat(truncatedBytes)
                .hasSize(2);

        final String output = doTest(truncatedBytes, charset);
        assertThat(output)
                .isEqualTo(DecodedChar.UNKNOWN_CHAR_REPLACEMENT_STRING);
    }

    private String fromUnicode(final int... unicodePoints) {
        return new String(unicodePoints, 0, unicodePoints.length);
    }

    void doTest(final String input) throws IOException {
        final Charset charset = StandardCharsets.UTF_8;

        final int inputByteCount = input.getBytes(charset).length;
        LOGGER.info("Input: [{}], byteCount: {}, bytesPerChar: {}, mode: {}",
                input, inputByteCount, (double) inputByteCount / input.length(), mode);
        final byte[] bytes = input.getBytes(charset);

        final String output = doTest(bytes, charset);

        assertThat(output)
                .isEqualTo(input);
    }

    void doTest(final String input, final String expectedOutput) throws IOException {
        final Charset charset = StandardCharsets.UTF_8;

        final int inputByteCount = input.getBytes(charset).length;
        LOGGER.info("Input: [{}], byteCount: {}, bytesPerChar: {}",
                input, inputByteCount, (double) inputByteCount / input.length());
        final byte[] bytes = input.getBytes(charset);

        final String output = doTest(bytes, charset);

        assertThat(output)
                .isEqualTo(expectedOutput);
    }

    String doTest(final byte[] bytes, final Charset charset) throws IOException {

        LOGGER.debug("bytes: {}", ByteArrayUtils.byteArrayToHex(bytes));

        final MyByteBuffer myByteBuffer = new MyByteBuffer();
        myByteBuffer.write(bytes);

        final StringBuilder outputStringBuilder = new StringBuilder();

        final AtomicInteger byteOffset = new AtomicInteger(0);
        final ByteStreamDecoder byteStreamDecoder = new ByteStreamDecoder(charset, mode, () -> {
            final Byte b = myByteBuffer.getByte(byteOffset.get());
            if (b != null) {
                byteOffset.incrementAndGet();
            }
            return b;
        });

        int outputByteCount = 0;
        int outputVisibleCharCount = 0;
        while (byteOffset.get() < bytes.length) {

            final DecodedChar decodedChar = byteStreamDecoder.decodeNextChar();

            outputByteCount += decodedChar.getByteCount();
            outputVisibleCharCount++;

            LOGGER.info("decodedChar {}", decodedChar);

            outputStringBuilder.append(decodedChar.getAsString());
        }

        LOGGER.info("outputStringBuilder: [{}], byte count {}, visible char count {}",
                outputStringBuilder,
                outputByteCount,
                outputVisibleCharCount);


        assertThat(byteOffset)
                .hasValue(bytes.length);

        return outputStringBuilder.toString();
    }

    @Test
    void testByteStreamDecoder_withBOM() throws IOException {

        final Charset charset = StandardCharsets.UTF_8;

        final String input = "hello";

        final MyByteBuffer myByteBuffer = new MyByteBuffer();
        final byte[] bytes = {(byte) 0xef, (byte) 0xbb, (byte) 0xbf};
        myByteBuffer.write(bytes);
        myByteBuffer.write(input.getBytes(charset));

        final StringBuilder outputStringBuilder = new StringBuilder();

        final AtomicInteger byteOffset = new AtomicInteger(0);
        final ByteStreamDecoder byteStreamDecoder = new ByteStreamDecoder(charset, () ->
                myByteBuffer.getByte(byteOffset.getAndIncrement()));

        final List<DecodedChar> decodedChars = new ArrayList<>();
        while (byteOffset.get() < myByteBuffer.size()) {

            final DecodedChar decodedChar = byteStreamDecoder.decodeNextChar();

            decodedChars.add(decodedChar);

            if (decodedChar.isByteOrderMark()) {
                LOGGER.info("BOM found");
            }

            outputStringBuilder.append(decodedChar.getAsString());
        }

        LOGGER.info("outputStringBuilder: [{}]", outputStringBuilder.toString());

        assertThat(decodedChars.get(0).isNonVisibleCharacter())
                .isTrue();
        assertThat(decodedChars.get(1).isNonVisibleCharacter())
                .isFalse();
    }

    /**
     * Little manual test to make a file with bad bytes in it for curling into stroom
     */
    @Disabled // Manual only
    @Test
    void createTestFileWithBadBytes() throws IOException {
        final byte[] bytes = new ByteArrayBuilder(StandardCharsets.UTF_8)
                .append("this bit is ok")
                .append(0x8E)
                .append(0x80)
                .append("and so is this")
                .append(0x80)
                .append(0x8E)
                .toByteArray();

        Files.write(
                Path.of("/tmp/1.txt"),
                bytes,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
    }

    /**
     * Little manual test to make a file with bad bytes in it for curling into stroom
     */
    @Disabled // Manual only
    @Test
    void createTestFileWithBadBytes2() throws IOException {

        @SuppressWarnings("checkstyle:LineLength") final byte[] bytes = new ByteArrayBuilder(StandardCharsets.UTF_8)
                .append("""
                        {
                            "id": "2489651045""")
                .append(0x8E)
                .append(0x80)
                .append("""
                        ",
                        {
                            "id": "2489651045",
                            "type": "CreateEvent",
                            "actor": {
                              "id": 665991,
                              "gravatar_id": "",
                            },
                            "repo": {
                              "id": 28688495,
                            },
                            "payload": {
                              "ref": "master",
                              "ref_type": "branch",
                              "master_branch": "master",
                              "pusher_type": "user"
                            },
                            "public": true,
                            "created_at": "2015-01-01T15:00:00Z"
                          }""")
                .toByteArray();

        Files.write(
                Path.of("/tmp/2.json"),
                bytes,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
    }

    /**
     * Little manual test to make a file with bad bytes in it for curling into stroom
     */
    @Disabled // Manual only
    @Test
    void createTestFileWithBadBytes3() throws IOException {

        @SuppressWarnings("checkstyle:LineLength") final byte[] bytes = new ByteArrayBuilder(StandardCharsets.UTF_8)
                .append("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <records
                          xmlns="records:2"
                          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="records:2 file://records-v2.0.xsd" version="2.0">
                          <record>
                            <data name="Da""")
                .append(0x80)
                .append(0x8E)
                .append("""
                        te" value="01/01/2010"/>
                            <data name="Time" value="00:00:00"/>
                            <data name="FileNo" value="4"/>
                            <data name="LineNo" value="1"/>
                            <data name="User" value="user1"/>
                            <data name="Message" value="Some message 1"/>
                          </record>
                          <record>
                            <data name="Date" value="01/01/2010"/>
                            <data name="Time" value="00:01:00"/>
                            <data name="FileNo" value="4"/>
                            <data name="LineNo" value="2"/>
                            <data name="User" value="user2"/>
                            <data name="Message" value="Some message 2"/>
                          </record>
                          <record>
                            <data name="Date" value="01/01/2010"/>
                            <data name="Time" value="00:02:00"/>
                            <data name="FileNo" value="4"/>
                            <data name="LineNo" value="3"/>
                            <data name="User" value="user3"/>
                            <data name="Message" value="Some message 3"/>
                          </record>
                        </records>""")
                .toByteArray();

        Files.write(
                Path.of("/tmp/3.xml"),
                bytes,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
    }


    // --------------------------------------------------------------------------------


    private static class MyByteBuffer extends ByteArrayOutputStream {

        int count = 0;

        Byte getByte(final int index) {
            return index < super.count
                    ? buf[index]
                    : null;
        }
    }
}
