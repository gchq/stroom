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

package stroom.data.store.impl;

import stroom.bytebuffer.ByteArrayUtils;
import stroom.pipeline.reader.ByteStreamDecoder.DecodedChar;

import io.vavr.Tuple;
import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestCharReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestCharReader.class);

    @Test
    void name() throws IOException {

        final Charset utf8 = StandardCharsets.UTF_8;
        final String text = "this is my test string😀Я.";
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(text.getBytes(utf8));

        final CharReader charReader = new CharReader(inputStream, false, utf8.name());

        final StringBuilder stringBuilder = new StringBuilder();

        while (true) {
            final Optional<DecodedChar> optDecodedChar = charReader.read();
            if (optDecodedChar.isEmpty()) {
                break;
            } else {
                final DecodedChar decodedChar = optDecodedChar.get();
                LOGGER.info("char: {}, charOffset: {}, byteOffset: {}",
                        decodedChar.getAsString(),
                        charReader.getLastCharOffsetRead(),
                        charReader.getLastByteOffsetRead());
                stringBuilder.append(decodedChar.getAsString());
            }
        }

        LOGGER.info("Output: {}", stringBuilder.toString());
    }

    @Test
    void testMultibyte() throws IOException {
        final Charset utf8 = StandardCharsets.UTF_8;
        final String text = "ЯR.ЯR";
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(text.getBytes(utf8));

        final CharReader charReader = new CharReader(inputStream, false, utf8.name());

        int byteOffset = 0;
        int charOffset = 0;
        readAndAssert(charReader, "Я", byteOffset++, charOffset++);
        byteOffset++;
        readAndAssert(charReader, "R", byteOffset++, charOffset++);
        readAndAssert(charReader, ".", byteOffset++, charOffset++);
        readAndAssert(charReader, "Я", byteOffset++, charOffset++);
        byteOffset++;
        readAndAssert(charReader, "R", byteOffset, charOffset);
    }

    @Test
    void testReadTillChar() throws IOException {
        final Charset utf8 = StandardCharsets.UTF_8;
        final String text = "this is my string";
        //                   01234567890123456789
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(text.getBytes(utf8));

        final CharReader charReader = new CharReader(inputStream, false, utf8.name());

        readNTimesAndAssert(
                charReader,
                2,
                "h",
                1,
                1);
    }

    @Test
    void testReadTillChar2() throws IOException {
        final Charset utf8 = StandardCharsets.UTF_8;
        final String text = "this is my string";
        //                   01234567890123456789
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(text.getBytes(utf8));

        final CharReader charReader = new CharReader(inputStream, false, utf8.name());

        readNTimesAndAssert(
                charReader,
                15,
                "i",
                14,
                14);
    }

    @TestFactory
    Stream<DynamicTest> testDifferentCharsets() {

        final String inputStr = "This is a line\n"
                + "and so is this 😀.\n"
                + "This is a \t tab";

        // The LE/BE charsets should NOT have a bom according to the standard but add them anyway to
        // check that we can cope with them being there. The bom in utf8 is optional.
        final Map<String, List<ByteOrderMark>> BOM_MAP = Map.of(
                StandardCharsets.UTF_8.name(), List.of(ByteOrderMark.UTF_8), // add optional bom
                StandardCharsets.UTF_16.name(), Collections.emptyList(), // java adds a bom
                Charset.forName("UTF-32").name(), Collections.emptyList(), // java adds a bom
                StandardCharsets.UTF_16BE.name(), List.of(ByteOrderMark.UTF_16BE),
                StandardCharsets.UTF_16LE.name(), List.of(ByteOrderMark.UTF_16LE),
                Charset.forName("UTF-32LE").name(), List.of(ByteOrderMark.UTF_32LE),
                Charset.forName("UTF-32BE").name(), List.of(ByteOrderMark.UTF_32BE),
                StandardCharsets.US_ASCII.name(), Collections.emptyList(), // no concept of BOM
                StandardCharsets.ISO_8859_1.name(), Collections.emptyList()); // no concept of BOM

        return Stream.of(
                StandardCharsets.UTF_8,
                StandardCharsets.UTF_16,
                Charset.forName("UTF-32"),
                StandardCharsets.UTF_16BE,
                StandardCharsets.UTF_16LE,
                StandardCharsets.US_ASCII,
                StandardCharsets.ISO_8859_1)
                .flatMap(charset -> {
                    final List<ByteOrderMark> list = BOM_MAP.get(charset.name());
                    if (list.isEmpty()) {
                        return Stream.of(Tuple.of(charset, (ByteOrderMark) null));
                    } else {
                        return list
                                .stream()
                                .map(byteOrderMark -> Tuple.of(charset, byteOrderMark));
                    }
                })
                .map(tuple2 -> {
                    final Charset charset = tuple2._1();
                    final ByteOrderMark byteOrderMark = tuple2._2();
                    final String testName = charset.displayName()
                            + "__"
                            + (byteOrderMark != null
                            ? byteOrderMark.toString()
                            : "NO-BOM");

                    return DynamicTest.dynamicTest(testName, () -> {
                        LOGGER.info("Charset {}, byteOrderMark {}",
                                charset.name(),
                                byteOrderMark != null
                                        ? ByteArrayUtils.byteArrayToHex(byteOrderMark.getBytes())
                                        : "null");

                        byte[] bytes = inputStr.getBytes(charset);

                        LOGGER.info("String len {}, bytes len {}, bytes:\n{}\nstr: \n{}",
                                inputStr.length(),
                                bytes.length,
                                ByteArrayUtils.byteArrayToHex(bytes),
                                new String(bytes, charset));

                        if (byteOrderMark != null) {
                            // Test requires us to manually add a bom at the beginning to ensure we can decode
                            // with the bom
                            final byte[] newArr = new byte[bytes.length + byteOrderMark.length()];
                            System.arraycopy(byteOrderMark.getBytes(), 0, newArr, 0, byteOrderMark.length());
                            System.arraycopy(bytes, 0, newArr, byteOrderMark.length(), bytes.length);

                            bytes = newArr;
                            LOGGER.info("String len {}, bytes len {}, bytes:\n{}\nstr: \n{}",
                                    inputStr.length(),
                                    bytes.length,
                                    ByteArrayUtils.byteArrayToHex(bytes),
                                    new String(bytes, charset));
                        }

                        final ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
                        final CharReader charReader = new CharReader(
                                inputStream,
                                false,
                                charset.name());
                        final List<DecodedChar> decodedChars = new ArrayList<>();

                        // Read all chars and verify expected count
                        while (true) {
                            final Optional<DecodedChar> optDecodedChar = charReader.read();
                            if (optDecodedChar.isEmpty()) {
                                break;
                            } else {
                                decodedChars.add(optDecodedChar.get());
                            }
                        }

                        // Check first char is right.
                        Assertions.assertThat(decodedChars.get(0).getAsString())
                                .isEqualTo(String.valueOf(inputStr.charAt(0)));

                        // Check right number of 'characters'
                        Assertions.assertThat(decodedChars)
                                .hasSize(inputStr.length() - 1); // -1 to account for 2 char emoji
                    });
                });
    }

    @Disabled // Was verifying behaviour of commons.io BOMInputStream
    @Test
    void testBomInputStream() throws IOException {
        final String inputStr = "Hello";
        final byte[] strBytes = inputStr.getBytes(StandardCharsets.UTF_16LE);
        final byte[] bytes = new byte[strBytes.length + 2];

        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xFE;
        System.arraycopy(strBytes, 0, bytes, 2, strBytes.length);

        LOGGER.info("{}", ByteArrayUtils.byteArrayToHex(bytes));

        final StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            stringBuilder.append(bytes[i])
                    .append(" ");
        }
        LOGGER.info(stringBuilder.toString());

        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        final BOMInputStream bomInputStream = new BOMInputStream(
                byteArrayInputStream,
                false,
                ByteOrderMark.UTF_16LE,
                ByteOrderMark.UTF_16BE);

        final byte[] singleByteArr = new byte[1];
        int cnt;

        do {
            cnt = bomInputStream.read(singleByteArr);
            LOGGER.info("{}", ByteArrayUtils.byteArrayToHex(singleByteArr));
        } while (cnt > 0);
    }

    @Disabled // for manual testing
    @Test
    void testBitmapReference() throws IOException {

        // This file is UTF16-LE with a BOM
        final byte[] bytes = FileUtils.readFileToByteArray(Paths.get(
                "../../stroom-core/src/test/resources/samples/input/BITMAP-REFERENCE~1.in").toFile());
        LOGGER.info("\n{}", ByteArrayUtils.byteArrayToAllForms(bytes));

        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);

        final CharReader charReader = new CharReader(
                byteArrayInputStream,
                false,
                StandardCharsets.UTF_16LE.name());
        final List<DecodedChar> decodedChars = new ArrayList<>();

        // Read all chars and verify expected count
        final StringBuilder stringBuilder = new StringBuilder();
        while (true) {
            final Optional<DecodedChar> optDecodedChar = charReader.read();
            if (optDecodedChar.isEmpty()) {
                break;
            } else {

                decodedChars.add(optDecodedChar.get());
                stringBuilder.append(optDecodedChar.get().getAsString());
            }
        }
        LOGGER.info("\n{}", stringBuilder.toString());
    }

    private void readNTimesAndAssert(final CharReader charReader,
                                     final int readCount,
                                     final String expectedString,
                                     final long expectedByteOffset,
                                     final long expectedCharOffset) throws IOException {

        if (readCount > 1) {
            for (int i = 1; i <= readCount - 1; i++) {
                charReader.read();
            }
        }
        // now the last read
        readAndAssert(charReader, expectedString, expectedByteOffset, expectedCharOffset);
    }

    private void readAndAssert(final CharReader charReader,
                               final String expectedStr,
                               final long expectedByteOffset,
                               final long expectedCharOffset) throws IOException {

        final Optional<DecodedChar> optDecodeChar = charReader.read();
        if (expectedStr == null) {
            assertThat(optDecodeChar).isEmpty();
        } else {
            assertThat(optDecodeChar).isPresent();
            final DecodedChar decodedChar = optDecodeChar.get();

            assertThat(decodedChar.getAsString()).isEqualTo(expectedStr);
            assertThat(charReader.getLastByteOffsetRead()).hasValue(expectedByteOffset);
            assertThat(charReader.getLastCharOffsetRead()).hasValue(expectedCharOffset);
        }
    }

    private static class Dummy extends ByteOrderMark {

        public Dummy() {
            super("x", 0);
        }
    }
}
