package stroom.data.store.impl;

import stroom.pipeline.reader.ByteStreamDecoder.DecodedChar;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestCharReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestCharReader.class);

    @Test
    void name() throws IOException {

        Charset utf8 = StandardCharsets.UTF_8;
        final String text = "this is my test string😀Я.";
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(text.getBytes(utf8));

        final CharReader charReader = new CharReader(inputStream, utf8.name());

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
        Charset utf8 = StandardCharsets.UTF_8;
        final String text = "ЯR.ЯR";
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(text.getBytes(utf8));

        final CharReader charReader = new CharReader(inputStream, utf8.name());

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
        Charset utf8 = StandardCharsets.UTF_8;
        final String text = "this is my string";
        //                   01234567890123456789
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(text.getBytes(utf8));

        CharReader charReader = new CharReader(inputStream, utf8.name());

        readNTimesAndAssert(
                charReader,
                2,
                "h",
                1,
                1);
    }

    @Test
    void testReadTillChar2() throws IOException {
        Charset utf8 = StandardCharsets.UTF_8;
        final String text = "this is my string";
        //                   01234567890123456789
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(text.getBytes(utf8));

        CharReader charReader = new CharReader(inputStream, utf8.name());

        readNTimesAndAssert(
                charReader,
                15,
                "i",
                14,
                14);
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
        readAndAssert(charReader,expectedString, expectedByteOffset, expectedCharOffset);
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
            DecodedChar decodedChar = optDecodeChar.get();

            assertThat(decodedChar.getAsString()).isEqualTo(expectedStr);
            assertThat(charReader.getLastByteOffsetRead()).hasValue(expectedByteOffset);
            assertThat(charReader.getLastCharOffsetRead()).hasValue(expectedCharOffset);
        }
    }
}