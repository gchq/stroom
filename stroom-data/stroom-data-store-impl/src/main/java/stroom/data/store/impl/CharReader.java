package stroom.data.store.impl;

import stroom.pipeline.reader.ByteStreamDecoder;
import stroom.pipeline.reader.ByteStreamDecoder.DecodedChar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

@NotThreadSafe
public class CharReader implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(CharReader.class);

    private final ByteStreamDecoder byteStreamDecoder;
    private final Charset charset;
    private final Supplier<Byte> byteSupplier;

    // Track the byte and char offsets as we read through the stream.
    private long currCharOffset = -1; // zero based
    private long currByteOffset = -1; // zero based
    private DecodedChar lastCharDecoded = null;

    public CharReader(final InputStream inputStream,
                      final String encoding) {
        Objects.requireNonNull(inputStream);
        Objects.requireNonNull(encoding);

        this.charset = Charset.forName(encoding);
        this.byteStreamDecoder = new ByteStreamDecoder(charset);

        byteSupplier = () -> {
            try {
                return (byte) inputStream.read();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    public Optional<DecodedChar> read() throws IOException {

        final DecodedChar decodedChar = byteStreamDecoder.decodeNextChar(byteSupplier);

        if (decodedChar == null) {
            return Optional.empty();
        } else {
            currCharOffset++;
            if (lastCharDecoded != null) {
                currByteOffset += lastCharDecoded.getByteCount();
            } else {
                currByteOffset = 0;
            }
            LOGGER.trace("Read [{}], currByteOffset: {}, currCharOffset {}",
                    decodedChar, currByteOffset, currCharOffset);
            lastCharDecoded = decodedChar;
            return Optional.of(decodedChar);
        }
    }

    public Optional<Long> getLastByteOffsetRead() {
        final long offset = currByteOffset;
        if (offset == -1) {
            return Optional.empty();
        } else {
            return Optional.of(offset);
        }
    }

    /**
     * @return The visible 'character' offset, zero based.
     * A visible character may be represented by multiple java char primitives.
     * 😀 would be one visible character. The GB flag emoji (🇬🇧) would be treated
     * as two visible characters.
     */
    public Optional<Long> getLastCharOffsetRead() {
        if (currCharOffset == -1) {
            return Optional.empty();
        } else {
            return Optional.of(currCharOffset);
        }
    }

    @Override
    public void close() throws Exception {
    }
}
