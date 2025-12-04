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

package stroom.util.io;

import stroom.util.shared.ModelStringUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;

/**
 * Class to represent a size in bytes. It can be parsed from IEC byte units, e.g. 5Kib, 10MiB, etc.
 */
public class ByteSize {

    private final String valueAsStr;
    private final long bytes;

    public static final ByteSize ZERO = new ByteSize(0L);

    private ByteSize(final String valueAsStr) {
        Objects.requireNonNull(valueAsStr);
        final Long bytes = ModelStringUtil.parseIECByteSizeString(valueAsStr);

        if (bytes == null) {
            throw new IllegalArgumentException("Unable to parse [" + valueAsStr + "] to a ByteSize.");
        }
        this.bytes = bytes;
        if (valueAsStr.equals(ByteSize.ofBytes(bytes).getValueAsStr())) {
            // valueAsStr is same as from ModelStringUtil so no need to store it
            this.valueAsStr = null;
        } else {
            this.valueAsStr = valueAsStr;
        }
    }

    private ByteSize(final long bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("bytes [" + bytes + "] cannot be less than zero");
        }
        this.bytes = bytes;

        final String valueAsStr = formatBytes(bytes);
        if (valueAsStr.isEmpty()) {
            throw new RuntimeException("Something went wrong, valueAsStr should not be empty.");
        }
        // valueAsStr is from ModelStringUtil so no need to store it
        this.valueAsStr = null;
    }

    private String formatBytes(final long bytes) {
        return ModelStringUtil.formatIECByteSizeString(bytes, true);
    }

    @JsonCreator
    public static ByteSize parse(final String value) {
        return new ByteSize(value);
    }

    public static ByteSize ofBytes(final long bytes) {
        return new ByteSize(bytes);
    }

    public static ByteSize ofKibibytes(final long kibibytes) {
        return new ByteSize(ByteSizeUnit.KIBIBYTE.longBytes(kibibytes));
    }

    public static ByteSize ofMebibytes(final long mebibytes) {
        return new ByteSize(ByteSizeUnit.MEBIBYTE.longBytes(mebibytes));
    }

    public static ByteSize ofGibibytes(final long gibibytes) {
        return new ByteSize(ByteSizeUnit.GIBIBYTE.longBytes(gibibytes));
    }

    public static ByteSize ofTebibytes(final long tebibytes) {
        return new ByteSize(ByteSizeUnit.TEBIBYTE.longBytes(tebibytes));
    }

    public static ByteSize ofPebibytes(final long pebibytes) {
        return new ByteSize(ByteSizeUnit.PEBIBYTE.longBytes(pebibytes));
    }

    public long getBytes() {
        return bytes;
    }

    public boolean isZero() {
        return bytes == 0L;
    }

    public boolean isNonZero() {
        return bytes != 0L;
    }

    @Override
    public String toString() {
        return getValueAsStr();
    }

    @JsonValue
    public String getValueAsStr() {
        return valueAsStr == null
                ? formatBytes(bytes)
                : valueAsStr;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ByteSize byteSize = (ByteSize) o;
        return bytes == byteSize.bytes;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bytes);
    }
}
