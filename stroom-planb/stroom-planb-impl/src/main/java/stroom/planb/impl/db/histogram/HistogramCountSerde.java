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

package stroom.planb.impl.db.histogram;

import stroom.lmdb.serde.UnsignedBytes;
import stroom.planb.impl.serde.count.CountSerde;

import java.nio.ByteBuffer;

public class HistogramCountSerde implements CountSerde<Long> {

    private final UnsignedBytes unsignedBytes;

    public HistogramCountSerde(final UnsignedBytes unsignedBytes) {
        this.unsignedBytes = unsignedBytes;
    }

    private void put(final ByteBuffer byteBuffer, final long value) {
        unsignedBytes.put(byteBuffer, Math.min(unsignedBytes.maxValue(), value));
    }

    @Override
    public void put(final ByteBuffer byteBuffer, final int position, final long value) {
        byteBuffer.position(position);
        put(byteBuffer, value);
    }

    @Override
    public void add(final ByteBuffer byteBuffer, final int position, final long value) {
        byteBuffer.position(position);
        final long currentValue = unsignedBytes.get(byteBuffer);
        final long newValue = currentValue + value;
        byteBuffer.position(position);
        put(byteBuffer, newValue);
    }

    @Override
    public long getVal(final ByteBuffer byteBuffer) {
        return get(byteBuffer);
    }

    @Override
    public Long get(final ByteBuffer byteBuffer) {
        return unsignedBytes.get(byteBuffer);
    }

    public void merge(final ByteBuffer buffer1,
                      final ByteBuffer buffer2,
                      final ByteBuffer output) {
        final long value1 = unsignedBytes.get(buffer1);
        final long value2 = unsignedBytes.get(buffer2);
        final long total = value1 + value2;
        put(output, total);
    }

    @Override
    public int length() {
        return unsignedBytes.length();
    }
}
