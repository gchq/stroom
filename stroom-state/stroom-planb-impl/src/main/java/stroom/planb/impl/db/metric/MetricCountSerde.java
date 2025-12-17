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

package stroom.planb.impl.db.metric;

import stroom.lmdb.serde.UnsignedBytes;
import stroom.planb.impl.serde.count.CountSerde;

import java.nio.ByteBuffer;

public class MetricCountSerde implements CountSerde<Metric> {

    private final UnsignedBytes unsignedBytes;
    private final boolean storeLatestValue;
    private final boolean storeMin;
    private final boolean storeMax;
    private final boolean storeCount;
    private final boolean storeSum;
    private final int length;

    public MetricCountSerde(final UnsignedBytes unsignedBytes,
                            final boolean storeLatestValue,
                            final boolean storeMin,
                            final boolean storeMax,
                            final boolean storeCount,
                            final boolean storeSum) {
        this.unsignedBytes = unsignedBytes;
        this.storeLatestValue = storeLatestValue;
        this.storeMin = storeMin;
        this.storeMax = storeMax;
        this.storeCount = storeCount;
        this.storeSum = storeSum;

        int length = 0;
        if (storeLatestValue) {
            length += unsignedBytes.length();
        }
        if (storeMin) {
            length += unsignedBytes.length();
        }
        if (storeMax) {
            length += unsignedBytes.length();
        }
        if (storeCount) {
            length += unsignedBytes.length();
        }
        if (storeSum) {
            length += unsignedBytes.length();
        }
        this.length = length;
    }

    private void put(final ByteBuffer byteBuffer, final long value) {
        unsignedBytes.put(byteBuffer, Math.min(unsignedBytes.maxValue(), value));
    }

    @Override
    public void put(final ByteBuffer byteBuffer, final int position, final long value) {
        final Metric metric = new Metric(
                value,
                value,
                value,
                1,
                value);
        byteBuffer.position(position);
        writeMetric(byteBuffer, metric);
    }

    @Override
    public void add(final ByteBuffer byteBuffer, final int position, final long value) {
        byteBuffer.position(position);
        final Metric metric = readMetric(byteBuffer);
        final Metric combined = new Metric(
                value,
                Math.min(value, metric.min()),
                Math.max(value, metric.max()),
                metric.count() + 1,
                metric.sum() + value);
        byteBuffer.position(position);
        writeMetric(byteBuffer, combined);
    }

    private Metric readMetric(final ByteBuffer byteBuffer) {
        long value = 0;
        long min = Long.MAX_VALUE;
        long max = 0;
        long count = 0;
        long sum = 0;

        if (storeLatestValue) {
            value = unsignedBytes.get(byteBuffer);
        }
        if (storeMin) {
            min = unsignedBytes.get(byteBuffer);
        }
        if (storeMax) {
            max = unsignedBytes.get(byteBuffer);
        }
        if (storeCount) {
            count = unsignedBytes.get(byteBuffer);
        }
        if (storeSum) {
            sum = unsignedBytes.get(byteBuffer);
        }
        return new Metric(value, min, max, count, sum);
    }

    private void writeMetric(final ByteBuffer byteBuffer, final Metric metric) {
        if (storeLatestValue) {
            put(byteBuffer, metric.value());
        }
        if (storeMin) {
            put(byteBuffer, metric.min());
        }
        if (storeMax) {
            put(byteBuffer, metric.max());
        }
        if (storeCount) {
            put(byteBuffer, metric.count());
        }
        if (storeSum) {
            put(byteBuffer, metric.sum());
        }
    }

    @Override
    public long getVal(final ByteBuffer byteBuffer) {
        return get(byteBuffer).value();
    }

    @Override
    public Metric get(final ByteBuffer byteBuffer) {
        return readMetric(byteBuffer);
    }

    public void merge(final ByteBuffer buffer1,
                      final ByteBuffer buffer2,
                      final ByteBuffer output) {
        final Metric value1 = readMetric(buffer1);
        final Metric value2 = readMetric(buffer2);
        final Metric combined = new Metric(
                value2.value(),
                Math.min(value1.min(), value2.min()),
                Math.max(value1.max(), value2.max()),
                value1.count() + value2.count(),
                value1.sum() + value2.sum());
        writeMetric(output, combined);
    }

    @Override
    public int length() {
        return length;
    }
}
