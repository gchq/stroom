package stroom.data.store.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.Optional;

@NotThreadSafe
public class CharReader2 implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(CharReader2.class);

    private final LocationAwareInputStream locationAwareInputStream;
    private final Charset charset;
    private final int bufferSize;
    private final Reader reader;
    private char[] charBuffer = null;
    private int currOffsetInBuffer = -1; // zero based
    private boolean isStreamComplete = false;
    private long byteOffsetForFirstCharInBuffer = -1; // zero based
    private int charsInBuffer = 0;
    private long currCharOffset = -1; // zero based

    public CharReader2(final InputStream inputStream,
                       final String encoding,
                       final int bufferSize) {
        this.locationAwareInputStream = new LocationAwareInputStream(inputStream);
        this.charset = Charset.forName(encoding);
        this.bufferSize = bufferSize;
        this.reader = new InputStreamReader(locationAwareInputStream);
        this.charBuffer = new char[bufferSize];
        CharsetDecoder charsetDecoder = charset.newDecoder();
    }

    public Optional<Character> read() throws IOException {
        if (isStreamComplete) {
            return Optional.empty();
        } else {
            if (currOffsetInBuffer == -1
                    || currOffsetInBuffer >= bufferSize - 1
                    || currOffsetInBuffer == charsInBuffer - 1) {
                currOffsetInBuffer = -1;
                charsInBuffer = reader.read(charBuffer);

                if (charsInBuffer < 0) {
                    isStreamComplete = true;
                    byteOffsetForFirstCharInBuffer = -1;
                    return Optional.empty();
                } else {
                    byteOffsetForFirstCharInBuffer = locationAwareInputStream.getFirstOffsetInLastRead();
                    LOGGER.info("Setting byteOffsetForFirstCharInBuffer to {}", byteOffsetForFirstCharInBuffer);
                }
            }
            // buffer should be usable now

            currCharOffset++;
            currOffsetInBuffer++;
            return Optional.of(charBuffer[currOffsetInBuffer]);
        }
    }

    public Optional<Long> getLastByteOffsetRead() {
        if (currOffsetInBuffer == -1) {
            throw new RuntimeException("Read has not been called yet");
        }
        if (currOffsetInBuffer == 0) {
            // We are at the first char in the buffer and we know the byte offset for this
            return Optional.of(byteOffsetForFirstCharInBuffer);
        } else {
            // We need to scan over each char in the buffer to find out how many bytes
            // each char has so determine the byte offset of our current position.
            long byteOffset = byteOffsetForFirstCharInBuffer;
            for (int i = 0; i <= currOffsetInBuffer -1; i++) {
                char chr = charBuffer[i];
                final byte[] bytes = String.valueOf(chr).getBytes(charset);
                byteOffset += bytes.length;
            }
            return Optional.of(byteOffset);
        }
    }

    public Optional<Long> getLastCharOffsetRead() {
        if (currCharOffset == -1) {
            return Optional.empty();
        } else {
            return Optional.of(currCharOffset);
        }
    }

    @Override
    public void close() throws Exception {
        reader.close();
    }
}
