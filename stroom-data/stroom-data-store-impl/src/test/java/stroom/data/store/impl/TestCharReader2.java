package stroom.data.store.impl;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestCharReader2 {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestCharReader2.class);

    @Test
    void name() throws IOException {

        Charset utf8 = StandardCharsets.UTF_8;
        final String text = "this is my test string😀Я.";
//        final String text = "ЯR.ЯR";
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(text.getBytes(utf8));

        CharReader2 charReader = new CharReader2(inputStream, utf8.name(), 10);

        StringBuilder stringBuilder = new StringBuilder();

        while (true) {
            final Optional<Character> optChar = charReader.read();
            if (optChar.isEmpty()) {
                break;
            } else {
                Character character = optChar.get();
                LOGGER.info("char: {}, charOffset: {}, byteOffset: {}",
                        character,
                        charReader.getLastCharOffsetRead(),
                        charReader.getLastByteOffsetRead());
                stringBuilder.append(character);
            }
        }

        LOGGER.info("Output: {}", stringBuilder.toString());
    }

    @Test
    void testMultibyte() throws IOException {
        Charset utf8 = StandardCharsets.UTF_8;
        final String text = "ЯR.ЯR";
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(text.getBytes(utf8));

        final CharReader2 charReader = new CharReader2(inputStream, utf8.name(), 10);

        int byteOffset = 0;
        int charOffset = 0;
        readAndAssert(charReader, 'Я', byteOffset++, charOffset++);
        byteOffset++;
        readAndAssert(charReader, 'R', byteOffset++, charOffset++);
        readAndAssert(charReader, '.', byteOffset++, charOffset++);
        readAndAssert(charReader, 'Я', byteOffset++, charOffset++);
        byteOffset++;
        readAndAssert(charReader, 'R', byteOffset++, charOffset++);
    }

    @Test
    void testStringBiggerThanBuffer() throws IOException {
        Charset utf8 = StandardCharsets.UTF_8;
        final String text = "this is my string";
        //                   01234567890123456789
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(text.getBytes(utf8));

        CharReader2 charReader = new CharReader2(inputStream, utf8.name(), 3);

        readNTimesAndAssert(
                charReader,
                2,
                'h',
                1,
                1);
    }

    @Test
    void testStringBiggerThanBuffer2() throws IOException {
        Charset utf8 = StandardCharsets.UTF_8;
        final String text = "this is my string";
        //                   01234567890123456789
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(text.getBytes(utf8));

        CharReader2 charReader = new CharReader2(inputStream, utf8.name(), 3);

        readNTimesAndAssert(
                charReader,
                15,
                'i',
                14,
                14);
    }

    private void readNTimesAndAssert(final CharReader2 charReader,
                                     final int readCount,
                                     final Character expectedChar,
                                     final long expectedByteOffset,
                                     final long expectedCharOffset) throws IOException {

        if (readCount > 1) {
            for (int i = 1; i <= readCount - 1; i++) {
                charReader.read();
            }
        }
        // now the last read
        readAndAssert(charReader,expectedChar, expectedByteOffset, expectedCharOffset);
    }

    private void readAndAssert(final CharReader2 charReader,
                               final Character expectedChar,
                               final long expectedByteOffset,
                               final long expectedCharOffset) throws IOException {

        final Optional<Character> optChar = charReader.read();
        if (expectedChar == null) {
            assertThat(optChar).isEmpty();
        } else {
            assertThat(optChar).isPresent();
            char chr = optChar.get();
            assertThat(chr).isEqualTo(expectedChar);
            assertThat(charReader.getLastByteOffsetRead()).hasValue(expectedByteOffset);
            assertThat(charReader.getLastCharOffsetRead()).hasValue(expectedCharOffset);
        }
    }
}