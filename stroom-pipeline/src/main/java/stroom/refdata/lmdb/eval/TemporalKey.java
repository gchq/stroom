/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.refdata.lmdb.eval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class TemporalKey {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemporalKey.class);

    private final String key;
    private final long timeMs;

    private static final byte DELIMETER = 0x00;

    public TemporalKey(String key) {
        this.key = key;
        this.timeMs = 0;
    }

    public TemporalKey(String key, long timeMs) {
        this.key = key;
        this.timeMs = timeMs;
    }

    public ByteBuffer toDbKey() {

        byte[] strBytes = key.getBytes(StandardCharsets.UTF_8);
        final ByteBuffer keyBuffer = ByteBuffer.allocateDirect(strBytes.length + 1 + Long.BYTES);

        keyBuffer
                .put(strBytes)
                .put(DELIMETER)
                .putLong(timeMs)
                .flip();
        return keyBuffer;
    }

    public static TemporalKey fromDbKey(final ByteBuffer keyBuffer) {

        int limit = keyBuffer.limit();
        int timePos = limit - Long.BYTES;
        int keyLength = timePos - 1;

//        LOGGER.info("limit {}, timePos {}, keyLength {}", limit, timePos, keyLength);

        ByteBuffer keyPartBuf = keyBuffer.duplicate();
        keyPartBuf.limit(keyLength);

        String key = StandardCharsets.UTF_8.decode(keyPartBuf).toString();

        long timeMs = keyBuffer.getLong(timePos);
        return new TemporalKey(key, timeMs);
    }

    public long getTimeMs() {
        return timeMs;
    }

    public Instant getTime() {
        return Instant.ofEpochMilli(timeMs);
    }

    public String getKey() {
        return key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TemporalKey that = (TemporalKey) o;

        if (timeMs != that.timeMs) return false;
        return key.equals(that.key);
    }

    @Override
    public int hashCode() {
        int result = key.hashCode();
        result = 31 * result + (int) (timeMs ^ (timeMs >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return key + " " + getTime().toString();
    }

    private static void logByteBuffer(ByteBuffer byteBuffer) {
        LOGGER.info("Pos {}, limit {}, capacity {}",
                byteBuffer.position(),
                byteBuffer.limit(),
                byteBuffer.capacity());
    }
}
