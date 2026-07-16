/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.planb.impl.db.trace;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferPoolOutput;
import stroom.pathways.shared.otel.trace.NanoTime;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * Serde for the fixed-shape {@link TraceStats} value, mirroring {@link TraceRootValueSerde}.
 */
public class TraceStatsSerde {

    private final ByteBufferFactory byteBufferFactory;
    private int bufferSize = 64;

    public TraceStatsSerde(final ByteBufferFactory byteBufferFactory) {
        this.byteBufferFactory = byteBufferFactory;
    }

    public void write(final TraceStats stats, final Consumer<ByteBuffer> consumer) {
        try (final ByteBufferPoolOutput output =
                new ByteBufferPoolOutput(byteBufferFactory, bufferSize, -1)) {
            output.writeLong(stats.spanCount());
            output.writeInt(stats.serviceCount());
            writeNanoTime(output, stats.maxEnd());
            output.writeLong(stats.lastActivityMs());
            output.writeInt(stats.depth());
            output.writeLong(stats.spanCountAtLastDepth());
            final ByteBuffer byteBuffer = output.getByteBuffer();
            byteBuffer.flip();
            consumer.accept(byteBuffer);
            bufferSize = Math.max(bufferSize, byteBuffer.capacity());
        }
    }

    public TraceStats read(final ByteBuffer byteBuffer) {
        try (final Input input = new ByteBufferInput(byteBuffer)) {
            final long spanCount = input.readLong();
            final int serviceCount = input.readInt();
            final NanoTime maxEnd = readNanoTime(input);
            final long lastActivityMs = input.readLong();
            final int depth = input.readInt();
            final long spanCountAtLastDepth = input.readLong();
            return new TraceStats(spanCount, serviceCount, maxEnd, lastActivityMs, depth,
                    spanCountAtLastDepth);
        }
    }

    private NanoTime readNanoTime(final Input input) {
        return new NanoTime(input.readLong(), input.readInt());
    }

    private void writeNanoTime(final Output output, final NanoTime nanoTime) {
        final NanoTime value = nanoTime == null ? NanoTime.ZERO : nanoTime;
        output.writeLong(value.getSeconds());
        output.writeInt(value.getNanos());
    }
}
