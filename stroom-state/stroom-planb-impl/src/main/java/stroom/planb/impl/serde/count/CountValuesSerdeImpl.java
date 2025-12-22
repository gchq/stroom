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

package stroom.planb.impl.serde.count;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.serde.temporalkey.TemporalKey;
import stroom.planb.impl.serde.valtime.InsertTimeSerde;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.Values;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Consumer;

public final class CountValuesSerdeImpl<T> implements CountValuesSerde<T> {

    private final ByteBuffers byteBuffers;
    private final CountSerde<T> countSerde;
    private final InsertTimeSerde insertTimeSerde;
    final ZoneId zoneId;
    private final TemporalIndex temporalIndex;
    private final int bufferLength;
    private final byte[] emptyBytes;

    public CountValuesSerdeImpl(final ByteBuffers byteBuffers,
                                final CountSerde<T> countSerde,
                                final InsertTimeSerde insertTimeSerde,
                                final ZoneId zoneId,
                                final TemporalIndex temporalIndex) {
        this.byteBuffers = byteBuffers;
        this.countSerde = countSerde;
        this.insertTimeSerde = insertTimeSerde;
        this.zoneId = zoneId;
        this.temporalIndex = temporalIndex;
        bufferLength = (temporalIndex.getEntries() * countSerde.length()) + insertTimeSerde.getSize();
        emptyBytes = new byte[bufferLength];
    }

    private void newByteBuffer(final Consumer<ByteBuffer> consumer) {
        byteBuffers.use(bufferLength, byteBuffer -> {
            consumer.accept(byteBuffer);
            return null;
        });
    }

    private void zeroByteBuffer(final ByteBuffer byteBuffer) {
        byteBuffer.put(emptyBytes);
        byteBuffer.flip();
    }

    @Override
    public void newSingleValue(final Instant instant, final long value, final Consumer<ByteBuffer> consumer) {
        newByteBuffer(valueByteBuffer -> {
            zeroByteBuffer(valueByteBuffer);
            final int position = getPosition(instant);
            countSerde.put(valueByteBuffer, position, value);
            writeInsertTime(valueByteBuffer);
            valueByteBuffer.position(0);
            consumer.accept(valueByteBuffer);
        });
    }

    @Override
    public void addSingleValue(final ByteBuffer byteBuffer,
                               final Instant instant,
                               final long value,
                               final Consumer<ByteBuffer> consumer) {
        newByteBuffer(valueByteBuffer -> {
            valueByteBuffer.put(byteBuffer);
            valueByteBuffer.flip();
            final int position = getPosition(instant);
            countSerde.add(valueByteBuffer, position, value);
            writeInsertTime(valueByteBuffer);
            valueByteBuffer.position(0);
            consumer.accept(valueByteBuffer);
        });
    }

    @Override
    public void merge(final ByteBuffer source,
                      final ByteBuffer destination,
                      final Consumer<ByteBuffer> consumer) {
        newByteBuffer(valueByteBuffer -> {
            for (int entry = 0; entry < temporalIndex.getEntries(); entry++) {
                countSerde.merge(source, destination, valueByteBuffer);
            }
            writeInsertTime(valueByteBuffer);
            valueByteBuffer.flip();
            consumer.accept(valueByteBuffer);
        });
    }

    private void writeInsertTime(final ByteBuffer byteBuffer) {
        byteBuffer.position(byteBuffer.limit() - insertTimeSerde.getSize());
        insertTimeSerde.write(byteBuffer, Instant.now());
    }

    @Override
    public Instant readInsertTime(final ByteBuffer byteBuffer) {
        byteBuffer.position(byteBuffer.limit() - insertTimeSerde.getSize());
        return insertTimeSerde.read(byteBuffer);
    }

    @Override
    public Long getVal(final Instant instant, final ByteBuffer byteBuffer) {
        final int position = getPosition(instant);
        byteBuffer.position(position);
        return countSerde.getVal(byteBuffer);
    }

    private int getPosition(final Instant instant) {
        final int entryIndex = temporalIndex.getEntryIndex(instant, zoneId);
        return entryIndex * countSerde.length();
    }

    @Override
    public void getValues(final TemporalKey key,
                          final ByteBuffer byteBuffer,
                          final List<ValConverter<T>> valConverters,
                          final Consumer<Values> consumer) {
        final ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(key.getTime(), zoneId);
        for (int entry = 0; entry < temporalIndex.getEntries(); entry++) {
            final T value = countSerde.get(byteBuffer);
            final Instant time = zonedDateTime.plus(entry, temporalIndex.getTemporalUnit()).toInstant();
            final TemporalKey temporalKey = new TemporalKey(key.getPrefix(), time);
            final Val[] vals = new Val[valConverters.size()];
            int i = 0;
            for (final ValConverter<T> valConverter : valConverters) {
                vals[i] = valConverter.convert(temporalKey, value);
                i++;
            }
            consumer.accept(Values.of(vals));
        }
    }
}
