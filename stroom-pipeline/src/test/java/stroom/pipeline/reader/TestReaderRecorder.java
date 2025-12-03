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
import stroom.util.logging.LogUtil;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;

class TestReaderRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestReaderRecorder.class);

    /**
     * Test to verify the byte by byte decoding of a {@link ByteArrayOutputStream}
     */
    @Test
    void test() throws IOException {
        LOGGER.info("Start");
        final Charset charset = StandardCharsets.UTF_8;

        final String input = "hello↵bye"; // ↵ is multi byte
        final MyByteBuffer myByteBuffer = new MyByteBuffer();
        myByteBuffer.write(input.getBytes(charset));

        final StringBuilder outputStringBuilder = new StringBuilder();

        final CharsetDecoder charsetDecoder = charset.newDecoder();

        final int byteBufferSize = 10; // Surely we can't have a char that takes more than 10 bytes
        final ByteBuffer inputBuffer = ByteBuffer.allocate(byteBufferSize);
        LOGGER.info("ByteBuffer size {}", byteBufferSize);

        final java.nio.CharBuffer outputBuffer = java.nio.CharBuffer.allocate(1);

        int byteOffset = 0;

        // Advance over the input
        while (byteOffset < input.getBytes(charset).length) {
            boolean charDecoded = false;
            int loopCnt = 0;
            int byteCnt = 0;
            inputBuffer.clear();

            while (!charDecoded && loopCnt++ < 10) {
                final byte b = myByteBuffer.getByte(byteOffset++);
                LOGGER.info("byteOffset: {}, byte: {}",
                        byteOffset,
                        ByteArrayUtils.byteArrayToString(new byte[]{b}));

                LOGGER.info("Putting");
                inputBuffer.put(b);
                inputBuffer.flip();

                LOGGER.info("in pos: {}, in limit: {}", inputBuffer.position(), inputBuffer.limit());

                final CoderResult coderResult = charsetDecoder.decode(inputBuffer, outputBuffer, true);

                LOGGER.info("coderResult: {}, out size: {}, out pos: {}",
                        coderResult, outputBuffer.length(), outputBuffer.position());

                byteCnt++;

                // We may have only one byte of a multibyte char so a malformed result is likely
                // for any non ascii chars.
                if (!coderResult.isMalformed()) {
                    charDecoded = true;
                    final char decodedChar = outputBuffer.array()[0];
                    outputStringBuilder.append(decodedChar);

                    final int decodedCharByteSize = String.valueOf(decodedChar).getBytes(charset).length;

                    LOGGER.info("Char {} ({}), byteCnt: {}",
                            decodedChar,
                            decodedCharByteSize,
                            byteCnt);

                    Assertions.assertThat(decodedCharByteSize)
                            .isEqualTo(byteCnt);

                    // Clear input buffer ready for a new char's bytes
                    inputBuffer.clear();
                    outputBuffer.clear();
                } else {
                    // Malformed so go round again
                    // make sure the in buffer is after the last byte tried
                    inputBuffer.limit(byteCnt + 1);
                    inputBuffer.position(byteCnt);
                }
            }
            if (!charDecoded) {
                throw new RuntimeException(LogUtil.message("Failed to decode char after {} iterations.", loopCnt));
            }
        }

        LOGGER.info("outputStringBuilder: {}", outputStringBuilder.toString());

        Assertions.assertThat(outputStringBuilder.toString())
                .isEqualTo(input);
    }


    // --------------------------------------------------------------------------------


    private static class MyByteBuffer extends ByteArrayOutputStream {

        Byte getByte(final int index) {
            return index < super.count
                    ? buf[index]
                    : null;
        }
    }
}
