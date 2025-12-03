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

package stroom.util.string;

import stroom.util.shared.NullSafe;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * A bit like {@link StringBuilder}, but constructs a byte[] by appending strings
 * (that are encoded using the supplied/default charset), single bytes, byte arrays,
 * chars or unicode points. Useful if you need to make streams of character data
 * with un-decodable parts or just from a mixture of input.
 */
public class ByteArrayBuilder {

    private final Charset charset;
    private final ByteArrayOutputStream byteArrayOutputStream;

    public ByteArrayBuilder() {
        this(null);
    }

    /**
     * @param charset The charset to use for string encoding
     */
    public ByteArrayBuilder(final Charset charset) {
        this.charset = Objects.requireNonNullElse(charset, StandardCharsets.UTF_8);
        this.byteArrayOutputStream = new ByteArrayOutputStream();
    }

    public ByteArrayBuilder append(final String str) {
        if (!NullSafe.isEmptyString(str)) {
            try {
                byteArrayOutputStream.write(str.getBytes(charset));
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return this;
    }

    public ByteArrayBuilder append(final char chr) {
        try {
            byteArrayOutputStream.write(String.valueOf(chr).getBytes(charset));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    public ByteArrayBuilder appendUnicode(final int... unicodePoints) {
        if (unicodePoints != null && unicodePoints.length > 0) {
            final String str = new String(unicodePoints, 0, unicodePoints.length);
            try {
                byteArrayOutputStream.write(str.getBytes(charset));
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return this;
    }

    public ByteArrayBuilder append(final byte b) {
        byteArrayOutputStream.write(b);
        return this;
    }

    /**
     * Append the specified byte
     */
    public ByteArrayBuilder append(final int b) {
        byteArrayOutputStream.write(b);
        return this;
    }

    public ByteArrayBuilder append(final byte[] bytes) {
        try {
            byteArrayOutputStream.write(bytes);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    public ByteArrayBuilder append(final byte[] bytes, final int offset, final int len) {
        byteArrayOutputStream.write(bytes, offset, len);
        return this;
    }

    public byte[] toByteArray() {
        return byteArrayOutputStream.toByteArray();
    }
}
