/*
 * Copyright 2026 Crown Copyright
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

package stroom.planb.impl.dao.trace;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.otel.trace.TraceRoot;

import java.nio.file.Path;
import java.util.function.BiConsumer;

/**
 * Reads a {@link QueueItem} back. Open one, walk its traces, close it.
 *
 * <p>Walking starts from the stored roots rather than from the spans, so a consumer is handed the
 * operation name, timings and span count before it decides to pull the trace apart — and the roots
 * table is keyed by trace id alone, so no index is needed to iterate it.
 *
 * <p>Refuses an item whose layout it was not written against, rather than reading it wrongly. The
 * span and lookup encoding is checked separately, by {@link TraceDb} itself as the environment opens.
 */
public class QueueItemReader implements AutoCloseable {

    private final TraceDb item;
    private final long orderKey;
    private final String source;

    /**
     * @param itemDir a finished item's directory, as returned by {@link QueueItemWriter#write}.
     * @throws IllegalStateException where the item was written by an incompatible producer.
     */
    public QueueItemReader(final Path itemDir,
                           final ByteBuffers byteBuffers,
                           final ByteBufferFactory byteBufferFactory) {
        this.item = TraceDb.create(
                itemDir,
                byteBuffers,
                byteBufferFactory,
                QueueItem.doc(),
                true,
                QueueItem.HAS_SECONDARY_INDEXES);
        try {
            final int formatVersion = QueueItem.readFormatVersion(item.getEnv());
            if (formatVersion != QueueItem.FORMAT_VERSION) {
                throw new IllegalStateException(
                        "Queue item " + itemDir.getFileName() + " has format version " + formatVersion
                        + ", expected " + QueueItem.FORMAT_VERSION);
            }
            this.orderKey = QueueItem.readOrderKey(item.getEnv());
            this.source = QueueItem.readSource(item.getEnv());
        } catch (final RuntimeException e) {
            item.close();
            throw e;
        }
    }

    /**
     * When the producer released these traces, as epoch milliseconds. The same value leads the item's
     * directory name; this is the stored one.
     */
    public long getOrderKey() {
        return orderKey;
    }

    /**
     * The trace store these traces came from, or null where the producer did not say. What is learnt
     * from them is attributed to this rather than to whatever the configuration names now, so that
     * pointing a store at a different pathways document leaves what it already taught where it is.
     */
    public String getSource() {
        return source;
    }

    /** How many traces the item holds. */
    public long getTraceCount() {
        final long[] count = {0L};
        item.forEachRoot((traceId, root) -> count[0]++);
        return count[0];
    }

    /**
     * Hands every trace in the item to the consumer, in trace id order, with its stored root.
     *
     * <p>Each trace is assembled as it is reached rather than up front, so the whole item is never
     * held in memory at once.
     */
    public void forEachTrace(final BiConsumer<TraceRoot, Trace> consumer) {
        item.forEachRoot((traceIdBytes, root) ->
                item.findTrace(traceIdBytes).ifPresent(trace -> consumer.accept(root, trace)));
    }

    @Override
    public void close() {
        item.close();
    }
}
