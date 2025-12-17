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

import stroom.pipeline.reader.ByteStreamDecoder;
import stroom.pipeline.reader.ByteStreamDecoder.DecodedChar;
import stroom.pipeline.reader.ByteStreamDecoder.Mode;

import jakarta.validation.constraints.NotNull;
import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.input.BOMInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Reads the bytes in an {@link InputStream} and decodes them into {@link DecodedChar} instances
 * using the passed character set encoding name.
 * A {@link DecodedChar} may be decoded from 1 to many bytes and may consist of 1 to many java char primitives.
 * This reader also tracks its progress through the {@link InputStream} in terms of bytes and visible characters.
 * If the file contains a byte order mark that indicates an encoding different to the one supplied
 * then the one from the bom will be used.
 */
public class CharReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(CharReader.class);

    private final ByteStreamDecoder byteStreamDecoder;
    private final Charset charset;
    private final Supplier<Byte> byteSupplier;
    private final BOMInputStream bomInputStream;

    // Track the byte and char offsets as we read through the stream.
    private long currCharOffset = -1; // zero based
    private long currByteOffset = -1; // zero based
    private DecodedChar lastCharDecoded = null;

    /**
     * @param inputStream          The stream to read from. This reader will not close the stream.
     * @param includeByteOrderMark If true, any BOM in the stream will be included.
     * @param encoding             The character encoding name of the stream.
     */
    public CharReader(final InputStream inputStream,
                      final boolean includeByteOrderMark,
                      final String encoding) {
        Objects.requireNonNull(inputStream);
        Objects.requireNonNull(encoding);

        bomInputStream = buildBomInputStream(
                inputStream,
                includeByteOrderMark,
                encoding);

        // This will read a few bytes from the delegate input stream and hold onto them
        // to be passed on to the consumer, unless they are BOM bytes that are being skipped
        this.charset = determineCharset(encoding, bomInputStream);

        final byte[] arr = new byte[1];
//        final AtomicInteger counter = new AtomicInteger(0);
        byteSupplier = () -> {
            try {
                // Read into an array to avoid any
                // confusion over conversion between (un)signed ints and bytes
//                final int cnt = bomInputStream.read(arr);
                final int cnt = bomInputStream.read(arr);
                if (cnt > 0) {
//                    if (LOGGER.isTraceEnabled()) {
//                        final int i = counter.getAndIncrement();
//                        if (i < 10) {
//                            LOGGER.info("Byte {} {} {}", i, ByteArrayUtils.byteArrayToHex(arr), arr[0]);
//                        }
//                    }
                    return arr[0];
                } else {
                    return null;
                }
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        };
        // Consider using LENIENT mode, see https://github.com/gchq/stroom/issues/4080
        this.byteStreamDecoder = new ByteStreamDecoder(charset, Mode.STRICT, byteSupplier);
    }

    @NotNull
    private BOMInputStream buildBomInputStream(final InputStream inputStream,
                                               final boolean includeByteOrderMark,
                                               final String encoding) {
        // Set up the BOMs that the input stream should look for
        final ByteOrderMark[] byteOrderMarks;
        if (encoding.equalsIgnoreCase(StandardCharsets.UTF_8.name())) {
            byteOrderMarks = new ByteOrderMark[]{ByteOrderMark.UTF_8};
        } else if (encoding.equalsIgnoreCase(StandardCharsets.UTF_16LE.name())) {
            byteOrderMarks = new ByteOrderMark[]{ByteOrderMark.UTF_16LE};
        } else if (encoding.equalsIgnoreCase(StandardCharsets.UTF_16BE.name())) {
            byteOrderMarks = new ByteOrderMark[]{ByteOrderMark.UTF_16BE};
        } else if (encoding.toUpperCase().startsWith("UTF-16")) {
            byteOrderMarks = new ByteOrderMark[]{ByteOrderMark.UTF_16LE, ByteOrderMark.UTF_16BE};
        } else if (encoding.toUpperCase().startsWith("UTF-32")) {
            byteOrderMarks = new ByteOrderMark[]{ByteOrderMark.UTF_32LE, ByteOrderMark.UTF_32BE};
        } else {
            byteOrderMarks = null;
        }

        if (byteOrderMarks != null) {
            return new BOMInputStream(
                    inputStream,
                    includeByteOrderMark,
                    byteOrderMarks);
        } else {
            return new BOMInputStream(inputStream, includeByteOrderMark);
        }
    }

    private Charset determineCharset(final String encoding, final BOMInputStream bomInputStream) {
        String bomCharsetName = null;
        try {
            bomCharsetName = bomInputStream.getBOMCharsetName();
            if (bomCharsetName != null && !encoding.equals(bomCharsetName)) {
                LOGGER.info("BOM charset [{}] differs from encoding [{}], using [{}]",
                        bomCharsetName, encoding, bomCharsetName);
            }
        } catch (final IOException e) {
            LOGGER.warn("Error getting charset from BOM, {}", e.getMessage(), e);
        }
        return Charset.forName(Objects.requireNonNullElse(bomCharsetName, encoding));
    }

    public Optional<DecodedChar> read() throws IOException {

        final DecodedChar decodedChar = byteStreamDecoder.decodeNextChar();

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
     * as two visible characters. Other compound emoji would also currently be treated as
     * multiple visible characters, e.g. https://emojipedia.org/family-man-woman-girl-boy/
     */
    public Optional<Long> getLastCharOffsetRead() {
        if (currCharOffset == -1) {
            return Optional.empty();
        } else {
            return Optional.of(currCharOffset);
        }
    }

    /**
     * @return The charset used. This may differ to the one provided e.g. if the provided charset was
     * UTF-16 then it may get changed to UTF-16BE when the BOM is detected.
     */
    public Charset getCharset() {
        return charset;
    }

    public Optional<ByteOrderMark> getByteOrderMark() {
        try {
            return Optional.ofNullable(bomInputStream.getBOM());
        } catch (final IOException e) {
            throw new RuntimeException("Error determining if input stream has a BOM: " + e.getMessage(), e);
        }
    }
}
