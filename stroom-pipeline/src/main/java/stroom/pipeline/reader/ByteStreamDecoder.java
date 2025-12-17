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
import stroom.bytebuffer.ByteBufferUtils;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.StringUtil;
import stroom.util.string.HexDumpUtil;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Class to decode a byte stream one 'char' at a time. A 'char' may consist of one or more bytes
 * in the stream.
 * Not thread safe.
 */
public class ByteStreamDecoder {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ByteStreamDecoder.class);

    private final CharsetDecoder charsetDecoder;
    private final CharsetDecoder lenientCharsetDecoder;
    private final Mode mode;
    private final Deque<Byte> unConsumedBytes = new ArrayDeque<>();

    // Don't call this directly, use getNextByte()
    private final Supplier<Byte> byteSupplier;

    private int lastSuppliedByteOffset = -1;
    private boolean isEndOfStream = false;

    private static final int MAX_BYTES_PER_CHAR = 10;

    // Buffer used to feed the charsetDecoder, filled with one byte at a time till we have something
    // that will decode ok.
    private final ByteBuffer inputBuffer = ByteBuffer.allocate(MAX_BYTES_PER_CHAR);
    private final ByteBuffer largeInputBuffer = ByteBuffer.allocate(MAX_BYTES_PER_CHAR * 2);
    // The Buffer to output our decode char into, only needs to be length 1 as we are only dealing
    // in one char at time.
    private final java.nio.CharBuffer outputBuffer = java.nio.CharBuffer.allocate(MAX_BYTES_PER_CHAR * 2);
    private final MalformedBytesReport malformedBytesReport = new MalformedBytesReport();

    /**
     * @param encoding The charset to use
     */
    public ByteStreamDecoder(final String encoding, final Supplier<Byte> byteSupplier) {
        this(Objects.requireNonNull(Charset.forName(encoding)), byteSupplier);
    }

    /**
     * @param encoding The charset to use
     */
    public ByteStreamDecoder(final String encoding, final Mode mode, final Supplier<Byte> byteSupplier) {
        this(Objects.requireNonNull(Charset.forName(encoding)), mode, byteSupplier);
    }

    public ByteStreamDecoder(final Charset charset, final Supplier<Byte> byteSupplier) {
        this(charset, Mode.STRICT, byteSupplier);
    }

    /**
     * @param charset
     * @param mode
     * @param byteSupplier A supplier of consecutive bytes, e.g. from a byte stream. This will be called
     *                     one or more times (up to a maximum of MAX_BYTES_PER_CHAR) until a 'char' can be
     *                     decoded from the bytes supplied.
     */
    public ByteStreamDecoder(final Charset charset,
                             final Mode mode,
                             final Supplier<Byte> byteSupplier) {
        this.byteSupplier = byteSupplier;
        this.charsetDecoder = Objects.requireNonNull(charset)
                .newDecoder();

        this.lenientCharsetDecoder = switch (mode) {
            case STRICT -> null;
            case LENIENT -> Objects.requireNonNull(charset)
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .replaceWith(DecodedChar.UNKNOWN_CHAR_REPLACEMENT_STRING);
        };
        this.mode = mode;
    }

    private Byte getNextByte() {
        final Byte suppliedByte;
        if (unConsumedBytes.isEmpty()) {
            suppliedByte = byteSupplier.get();
        } else {
            // We have some unconsumed bytes from a previous call to decodeNextChar so
            // use them first
            suppliedByte = unConsumedBytes.pop();
        }

        if (suppliedByte == null) {
            // end of stream
            isEndOfStream = true;
        } else {
            isEndOfStream = false;
            lastSuppliedByteOffset++;
        }
        return suppliedByte;
    }

    /**
     * @return A {@link DecodedChar} representing the character decoded from n bytes supplied by byteSupplier or
     * null if byteSupplier has no bytes to supply.
     * @throws DecoderException If it fails to decode a char after consuming MAX_BYTES_PER_CHAR bytes
     *                          from byteSupplier
     */
    public DecodedChar decodeNextChar() {
        // Clear the buffers ready for a new char's bytes
        inputBuffer.clear();
        outputBuffer.clear();
        outputBuffer.put((char) 0);
        outputBuffer.put((char) 0);
        outputBuffer.clear();

        DecodedChar decodedChar = null;
        // Count of the number of bytes we have consumed for this char
        int byteCnt = 0;

        try {
            while (decodedChar == null && byteCnt < MAX_BYTES_PER_CHAR) {
                byte b = 0;
                try {
                    final Byte suppliedByte = getNextByte();
                    if (suppliedByte == null) {
                        break;
                    } else {
                        byteCnt++;
                    }
                    b = suppliedByte;
                } catch (final Exception e) {
                    throw new RuntimeException("Error getting next byte: " + e.getMessage(), e);
                }

                // Add the byte to our input buffer and get it ready for reading
                inputBuffer.put(b);
                inputBuffer.flip();

                // Attempt to decode the content of out input buffer
                outputBuffer.clear();
                final CoderResult coderResult = charsetDecoder.decode(
                        inputBuffer,
                        outputBuffer,
                        true);
                outputBuffer.flip();

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("coderResult: {}, byteCnt: {}, inPos: {}, inLimit: {}, " +
                                    "inBytes: [{}], outPos:{}, outLimit: {}, outputBuffer: [{}]",
                            coderResult,
                            byteCnt,
                            inputBuffer.position(),
                            inputBuffer.limit(),
                            ByteBufferUtils.byteBufferToHex(inputBuffer),
                            outputBuffer.position(),
                            outputBuffer.limit(),
                            outputBuffer);
                }

                // We may have only one byte of a multibyte char so a malformed result is likely
                // for any non ascii chars.
                if (!coderResult.isMalformed()) {
                    // We have decoded something so output it
                    final String decodedStr;
                    if (outputBuffer.remaining() == 2) {
//                    if (outputBuffer.array()[0] != 0 && outputBuffer.array()[1] != 0) {
                        final int codePoint = Character.toCodePoint(outputBuffer.get(), outputBuffer.get());
                        decodedStr = new String(new int[]{codePoint}, 0, 1);

                        LOGGER.trace("Multi-char character found with codePoint: [{}], decodedStr: [{}]",
                                codePoint, decodedStr);
                    } else {
                        decodedStr = String.valueOf(outputBuffer.get());
                    }

                    LOGGER.trace("Decoded char {}, with byte count {}", decodedStr, byteCnt);
                    decodedChar = new DecodedChar(decodedStr, byteCnt);
                } else {
                    LOGGER.trace(() -> LogUtil.message("inputBuffer: {}, coderResult: {}, len: {}",
                            ByteBufferUtils.byteBufferInfo(inputBuffer), coderResult, coderResult.length()));
                    // Malformed so go round again as we obvs don't have enough bytes to form the char
                    // Update the input buffer to take the next byte
                    if (byteCnt < MAX_BYTES_PER_CHAR) {
                        charsetDecoder.reset();
                        inputBuffer.limit(byteCnt + 1);
                        inputBuffer.position(byteCnt);
                    } else {
                        inputBuffer.limit(MAX_BYTES_PER_CHAR);
                        inputBuffer.position(0);
                    }
                }
            }

            if (decodedChar == null && byteCnt > 0 && mode == Mode.LENIENT) {
                decodedChar = getAllMalformedBytes(byteCnt);
            }

            // Only throw if we have actually consumed something and couldn't decode it
            if (decodedChar == null && byteCnt > 0) {
                throw createDecoderException(byteCnt);
            }
        } catch (final DecoderException e) {
            throw e;
        } catch (final RuntimeException e) {
            throw new RuntimeException(LogUtil.message("Error decoding bytes after {} iterations: {}",
                    byteCnt, e.getMessage()), e);
        }

        return decodedChar;
    }

    private DecoderException createDecoderException(final int byteCnt) {
        final byte[] malformedBytes = new byte[inputBuffer.remaining()];
        inputBuffer.get(malformedBytes);
        final long offsetOfBadBytes = lastSuppliedByteOffset - (byteCnt - 1);
        return new DecoderException(charsetDecoder.charset(), malformedBytes, offsetOfBadBytes);
    }

    private DecodedChar getAllMalformedBytes(final int bytesInBufferCount) {
        int goodCharByteOffset = -1;

        // As we need to go looking for some good chars, we need more bytes to work with
        largeInputBuffer.clear();
        for (int i = 0; i < bytesInBufferCount; i++) {
            largeInputBuffer.put(inputBuffer.get(i));
        }
        // Now try to fill it up using bytes from the supplier
        while (largeInputBuffer.hasRemaining()) {
            final Byte suppliedByte = getNextByte();
            if (suppliedByte == null) {
                break;
            }
            largeInputBuffer.put(suppliedByte);
        }

        largeInputBuffer.flip();
        final int byteCount = largeInputBuffer.remaining();

        // Assume that at least the first byte is bad, so keep slicing the bytebuffer
        // to cut off the first n bytes, then decode the remainder (replacing malformed bytes
        // with a replacement char) to find a valid char. We need to do this so we can establish how
        // big the block of bad bytes is.
        for (int i = 1; i < byteCount; i++) {
            final ByteBuffer slicedInputBuffer = largeInputBuffer.slice(i, byteCount - i);
//            LOGGER.trace("slicedInputBuffer: {}", ByteBufferUtils.byteBufferInfo(slicedInputBuffer));
            outputBuffer.clear();
            final CoderResult result = lenientCharsetDecoder.decode(
                    slicedInputBuffer, outputBuffer, true);
            outputBuffer.flip();
            if (outputBuffer.get(0) != DecodedChar.UNKNOWN_CHAR_REPLACEMENT) {
                goodCharByteOffset = i;
                break;
            }
        }

        if (goodCharByteOffset == -1) {
            if (isEndOfStream) {
                final byte[] malformedBytes = new byte[byteCount];
                inputBuffer.get(0, malformedBytes, 0, byteCount);
                return DecodedChar.unknownChar(malformedBytes);
            } else {
                // If we couldn't find a valid char in 20 bytes then just give up
                final long offsetOfBadBytes = lastSuppliedByteOffset - (byteCount - 1);
                throw new DecoderException(
                        charsetDecoder.charset(),
                        Arrays.copyOf(largeInputBuffer.array(), largeInputBuffer.array().length),
                        offsetOfBadBytes);
            }
        } else {
            // Queue any unused bytes, so they can be decoded on next call to decodeNextChar
            for (int i = goodCharByteOffset; i < byteCount; i++) {
                final byte unusedByte = largeInputBuffer.get(i);
                unConsumedBytes.add(unusedByte);
                // Need to return the offset back to the last of the malformed bytes
                lastSuppliedByteOffset--;
            }
            //noinspection UnnecessaryLocalVariable // Added for clarity
            final int malformedBytesCount = goodCharByteOffset;

            final byte[] malformedBytes = new byte[malformedBytesCount];
            inputBuffer.get(0, malformedBytes, 0, malformedBytesCount);

            // Keep a record of some of the nasty bits we have found
            final int offsetOfFirstBadByte = lastSuppliedByteOffset - malformedBytesCount - 1;
            malformedBytesReport.putMalformedBytes(
                    malformedBytes,
                    offsetOfFirstBadByte);

            return DecodedChar.unknownChar(malformedBytes);
        }
    }

    public int getLastSuppliedByteOffset() {
        return lastSuppliedByteOffset;
    }

    public long getBytesConsumedCount() {
        return lastSuppliedByteOffset + 1;
    }

    public int getMalformedBytesInstanceCount() {
        return malformedBytesReport.putCount;
    }

    public String getMalformedBytesReport() {
        return malformedBytesReport.toString();
    }

    public boolean foundMalformedBytes() {
        return malformedBytesReport.putCount > 0;
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    public static class DecoderException extends RuntimeException {

        private final byte[] malformedBytes;
        private final Charset charset;

        public DecoderException(final Charset charset,
                                final byte[] malformedBytes,
                                final long offset) {
            super(buildErrorMessage(charset, malformedBytes, offset));

            this.malformedBytes = malformedBytes;
            this.charset = charset;
        }

        private static String buildErrorMessage(final Charset charset,
                                                final byte[] malformedBytes,
                                                final long offset) {
            final String printableStr = HexDumpUtil.decodeAsPrintableChars(
                    malformedBytes,
                    charset,
                    DecodedChar.UNKNOWN_CHAR_REPLACEMENT);

            return "Unable to decode a "
                    + charset.displayName()
                    + " character starting at byte offset " + ModelStringUtil.formatCsv(offset)
                    + ". Showing " + LogUtil.namedCount("byte", malformedBytes.length) + " at this offset: " +
                    "[" + ByteArrayUtils.byteArrayToHex(malformedBytes) + "] as hex, " +
                    "[" + printableStr + "] as characters.";
        }

        public byte[] getMalformedBytes() {
            return malformedBytes;
        }

        public Charset getCharset() {
            return charset;
        }
    }


    // --------------------------------------------------------------------------------


    public enum Mode {
        /**
         * Throw an {@link DecoderException} when it is unable to decode a character
         * after {@link DecodedChar#MAX_BYTES_PER_CHAR}.
         */
        STRICT,
        /**
         * If it is unable to decode a character after {@link DecodedChar#MAX_BYTES_PER_CHAR},
         */
        LENIENT
    }


    // --------------------------------------------------------------------------------


    /**
     * Holds a single 'character' (which may be represented as two char primitives)
     * along with the number of bytes used to represent that char.
     */
    public static class DecodedChar {

        /**
         * Char to use represent 1 or more bytes that can't be decoded
         */
        public static final char UNKNOWN_CHAR_REPLACEMENT = '�';
        public static final String UNKNOWN_CHAR_REPLACEMENT_STRING = String.valueOf(UNKNOWN_CHAR_REPLACEMENT);

        private final String str;
        private final int byteCount;
        private final boolean isUnknown;
        private final byte[] malFormedBytes;
        private static final char BYTE_ORDER_MARK = '\ufeff';

        public DecodedChar(final String str, final int byteCount) {
            this(str, byteCount, false, null);
        }

        private DecodedChar(final String str,
                            final int byteCount,
                            final boolean isUnknown) {
            this(str, byteCount, isUnknown, null);
        }

        private DecodedChar(final String str,
                            final int byteCount,
                            final boolean isUnknown,
                            final byte[] malFormedBytes) {
            this.str = str;
            this.byteCount = byteCount;
            this.isUnknown = isUnknown;
            this.malFormedBytes = malFormedBytes;
        }

        public static DecodedChar unknownChar(final byte[] malFormedBytes) {
            Objects.requireNonNull(malFormedBytes);
            return new DecodedChar(
                    UNKNOWN_CHAR_REPLACEMENT_STRING,
                    malFormedBytes.length,
                    true,
                    malFormedBytes);
        }

        public String getAsString() {
            return str;
        }

        public int getByteCount() {
            return byteCount;
        }

        public int getCharCount() {
            return str.length();
        }

        public boolean isByteOrderMark() {
            return str.length() == 1 && str.charAt(0) == BYTE_ORDER_MARK;
        }

        public boolean isLineBreak() {
            return str.length() == 1 && str.charAt(0) == '\n';
        }

        public byte[] getMalFormedBytes() {
            return malFormedBytes;
        }

        public boolean isNonVisibleCharacter() {
            return str.codePoints()
                    .anyMatch(chr -> {
                        switch (Character.getType(chr)) {
                            case Character.CONTROL:
                            case Character.FORMAT:
                            case Character.PRIVATE_USE:
                            case Character.SURROGATE:
                            case Character.UNASSIGNED:
                                return true;
                            default:
                                return false;
                        }
                    });
        }

        /**
         * @return True if the bytes could not be decoded using the {@link Charset} of
         * this {@link ByteStreamDecoder}
         */
        public boolean isUnknown() {
            return isUnknown;
        }

        @Override
        public String toString() {
            return "DecodedChar{" +
                    "str='" + str + '\'' +
                    ", byteCount=" + byteCount +
                    ", charCount=" + getCharCount() +
                    ", isLineBreak=" + isLineBreak() +
                    ", isNonVisibleCharacter=" + isNonVisibleCharacter() +
                    ", isByteOrderMark=" + isByteOrderMark() +
                    ", isUnknown=" + isUnknown() +
                    '}';
        }
    }


    // --------------------------------------------------------------------------------


    private static class MalformedBytesReport {

        private final Map<byte[], Value> map = new HashMap<>();
        private int putCount = 0;
        private int uniqueBytesCount = 0;
        private static final int MAX_UNIQUE_BYTES = 50;
        private static final int MAX_OFFSETS = 50;
        private boolean wasTruncated = false;

        void putMalformedBytes(final byte[] malformedBytes, final int offsetOfFirstByte) {
            if (putCount < MAX_UNIQUE_BYTES) {
                if (!map.containsKey(malformedBytes)) {
                    uniqueBytesCount++;
                }
                final boolean exceededMaxOffsets = map.computeIfAbsent(malformedBytes, k -> new Value())
                        .addOffset(offsetOfFirstByte);
                if (exceededMaxOffsets) {
                    wasTruncated = true;
                }
            }
            putCount++;
        }

        public String toString() {
            if (putCount == 0) {
                return "";
            } else {
                final StringBuilder sb = new StringBuilder();
                final String detail = map.entrySet()
                        .stream()
                        .sorted(Comparator.nullsFirst(Comparator.comparing(valueEntry ->
                                valueEntry.getValue().getFirstOffset())))
                        .map(valueEntry -> {
                            final byte[] bytes = valueEntry.getKey();
                            final String hexBytes = ByteArrayUtils.byteArrayToHex(bytes);
                            final int instances = valueEntry.getValue().count;
                            final int offsetsCount = valueEntry.getValue().getOffsetCount();
                            final String offsets = valueEntry.getValue().offsets
                                    .stream()
                                    .map(i -> Integer.toString(i))
                                    .collect(Collectors.joining(", "));

                            return sb.append(instances)
                                    .append(StringUtil.plural(" instance", instances))
                                    .append(" of ")
                                    .append(StringUtil.plural("byte", bytes.length))
                                    .append(" [")
                                    .append(hexBytes)
                                    .append("] at ")
                                    .append(StringUtil.plural("offset", offsetsCount))
                                    .append("[")
                                    .append(offsets)
                                    .append("]")
                                    .toString();
                        })
                        .collect(Collectors.joining("\n"));

                final StringBuilder sb2 = new StringBuilder()
                        .append("Found ")
                        .append(putCount)
                        .append(StringUtil.plural("instance", "instances", putCount))
                        .append(" of un-decodable bytes:")
                        .append("\n")
                        .append(detail);
                if (wasTruncated()) {
                    sb2.append("\nSome instances have been omitted.");
                }
                return sb2.toString();
            }
        }

        public boolean wasTruncated() {
            return wasTruncated || uniqueBytesCount > MAX_UNIQUE_BYTES;
        }


        // --------------------------------------------------------------------------------


        private static class Value {

            private final List<Integer> offsets;
            private int count = 0;

            private Value() {
                offsets = new ArrayList<>();
            }

            private boolean addOffset(final int offset) {
                count++;
                if (offsets.size() < MAX_OFFSETS) {
                    offsets.add(offset);
                    return true;
                } else {
                    return false;
                }
            }

            private int getOffsetCount() {
                return offsets.size();
            }

            private int getCount() {
                return count;
            }

            private Integer getFirstOffset() {
                return count == 0
                        ? null
                        : offsets.get(0);
            }
        }
    }
}
