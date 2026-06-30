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

package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.meta.shared.Meta;
import stroom.planb.impl.data.RangeState;
import stroom.planb.impl.data.Session;
import stroom.planb.impl.data.SpanKV;
import stroom.planb.impl.data.State;
import stroom.planb.impl.data.TemporalRangeState;
import stroom.planb.impl.data.TemporalState;
import stroom.planb.impl.data.TemporalValue;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.PlanBDocument;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Manages all open LMDB write environments for a single pipeline stream execution.
 *
 * <p>One {@code PlanBStreamWriter} is created per stream (per {@link Meta}). As rows
 * arrive, {@link #getWriter} lazily opens one LMDB environment per
 * {@code (doc, shardIndex)} pair. The {@link PartDestination} for each writer
 * is chosen once at creation time based on the document configuration.
 *
 * <p>At stream end, {@link #close()} flushes all transactions via {@link #drain()},
 * then delegates publish and cleanup to the injected {@link BatchDestination}.
 *
 * <p><strong>Threading:</strong> {@code PlanBStreamWriter} is not thread-safe and must
 * be used from a single thread. Because {@link #close()} calls LMDB via JNI
 * (which pins virtual thread carriers), pipeline streams must not be executed on
 * virtual threads until LMDB is replaced with a non-JNI implementation.
 */
public class PlanBStreamWriter implements AutoCloseable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PlanBStreamWriter.class);

    /** Sentinel value meaning this doc is not sharded — one LMDB env covers all keys. */
    static final int UNSHARDED = -1;

    private final ByteBuffers byteBuffers;
    private final ByteBufferFactory byteBufferFactory;
    private final BatchDestination batchDestination;
    private final PartDestination sharedFsDestination;
    private final PartDestination restDestination;
    private final Path dir;
    private final Meta meta;

    private final Map<WriterKey, WriterInstance> writers = new HashMap<>();

    PlanBStreamWriter(final ByteBuffers byteBuffers,
                      final ByteBufferFactory byteBufferFactory,
                      final BatchDestination batchDestination,
                      final PartDestination sharedFsDestination,
                      final PartDestination restDestination,
                      final Path dir,
                      final Meta meta) {
        this.byteBuffers = byteBuffers;
        this.byteBufferFactory = byteBufferFactory;
        this.batchDestination = batchDestination;
        this.sharedFsDestination = sharedFsDestination;
        this.restDestination = restDestination;
        this.dir = dir;
        this.meta = meta;
    }

    public void addState(final PlanBDocument doc, final State state) {
        Objects.requireNonNull(doc,   "doc must not be null");
        Objects.requireNonNull(state, "state must not be null");
        final int shardIndex = getShardIndex(doc, state.key().getVal().toString());
        getWriter(doc, shardIndex).addState(state);
    }

    public void addTemporalState(final PlanBDocument doc, final TemporalState temporalState) {
        Objects.requireNonNull(doc,           "doc must not be null");
        Objects.requireNonNull(temporalState, "temporalState must not be null");
        final int shardIndex = getShardIndex(doc, temporalState.key().getPrefix().getVal().toString());
        getWriter(doc, shardIndex).addTemporalState(temporalState);
    }

    public void addRangeState(final PlanBDocument doc, final RangeState rangeState) {
        Objects.requireNonNull(doc,        "doc must not be null");
        Objects.requireNonNull(rangeState, "rangeState must not be null");
        final int shardIndex = getShardIndex(doc, rangeState.key().getKeyStart());
        getWriter(doc, shardIndex).addRangeState(rangeState);
    }

    public void addTemporalRangeState(final PlanBDocument doc,
                                      final TemporalRangeState temporalRangeState) {
        Objects.requireNonNull(doc,                "doc must not be null");
        Objects.requireNonNull(temporalRangeState, "temporalRangeState must not be null");
        final int shardIndex = getShardIndex(doc, temporalRangeState.key().getKeyStart());
        getWriter(doc, shardIndex).addTemporalRangeState(temporalRangeState);
    }

    public void addSession(final PlanBDocument doc, final Session session) {
        Objects.requireNonNull(doc,     "doc must not be null");
        Objects.requireNonNull(session, "session must not be null");
        final int shardIndex = getShardIndex(doc, session.getPrefix().getVal().toString());
        getWriter(doc, shardIndex).addSession(session);
    }

    public void addHistogramValue(final PlanBDocument doc, final TemporalValue temporalValue) {
        Objects.requireNonNull(doc,           "doc must not be null");
        Objects.requireNonNull(temporalValue, "temporalValue must not be null");
        final int shardIndex = getShardIndex(doc, temporalValue.key().getPrefix().getVal().toString());
        getWriter(doc, shardIndex).addHistogramValue(temporalValue);
    }

    public void addMetricValue(final PlanBDocument doc, final TemporalValue temporalValue) {
        Objects.requireNonNull(doc,           "doc must not be null");
        Objects.requireNonNull(temporalValue, "temporalValue must not be null");
        final int shardIndex = getShardIndex(doc, temporalValue.key().getPrefix().getVal().toString());
        getWriter(doc, shardIndex).addMetricValue(temporalValue);
    }

    public void addSpanValue(final PlanBDocument doc, final SpanKV spanKV) {
        Objects.requireNonNull(doc,    "doc must not be null");
        Objects.requireNonNull(spanKV, "spanKV must not be null");
        final int shardIndex = getShardIndex(doc, spanKV.key().getTraceId());
        getWriter(doc, shardIndex).addSpanValue(spanKV);
    }

    private int getShardIndex(final PlanBDocument doc, final String key) {
        final int shardCount = doc.getShardCount();
        return shardCount <= 0 ? UNSHARDED : ShardKeyRouter.computeShardIndex(key, shardCount);
    }

    private int getShardIndex(final PlanBDocument doc, final long value) {
        final int shardCount = doc.getShardCount();
        return shardCount <= 0 ? UNSHARDED : ShardKeyRouter.computeShardIndex(value, shardCount);
    }

    private WriterInstance getWriter(final PlanBDocument doc, final int shardIndex) {
        return writers.computeIfAbsent(
                new WriterKey(doc, shardIndex),
                k -> createWriterInstance(doc, shardIndex));
    }

    /**
     * Opens a new LMDB environment and wraps it in a {@link WriterInstance}.
     * All values that could throw are resolved before the native resource is
     * allocated, so that if anything fails before the constructor call,
     * no LMDB environment is leaked. {@link WriterInstance}'s own constructor
     * handles the case where {@code createWriter()} subsequently fails.
     */
    private WriterInstance createWriterInstance(final PlanBDocument doc, final int shardIndex) {
        final boolean synchroniseMerge = NullSafe.getOrElse(
                doc,
                PlanBDocument::getSettings,
                AbstractPlanBSettings::getSynchroniseMerge,
                false);
        final PartDestination destination = isSharedStoreDestination(doc)
                ? sharedFsDestination
                : restDestination;
        final Path localWriterDir = getLmdbEnvDir(doc, shardIndex);
        // Open the native LMDB environment last; WriterInstance closes it
        // immediately if the subsequent createWriter() call fails.
        final Db<?, ?> lmdb = PlanBDb.open(doc, localWriterDir, byteBuffers, byteBufferFactory, false);
        return new WriterInstance(lmdb, synchroniseMerge, localWriterDir, destination);
    }

    private Path getLmdbEnvDir(final PlanBDocument doc, final int shardIndex) {
        try {
            final Path path = shardIndex == UNSHARDED
                    ? dir.resolve(doc.getUuid())
                    : dir.resolve(doc.getUuid() + "_" + shardIndex);
            Files.createDirectories(path);
            return path;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns true when the doc is configured for direct shared-filesystem
     * delivery: it must have a positive shard count AND a non-blank shared path.
     * Either condition missing means the batch must fall back to the REST path.
     */
    private boolean isSharedStoreDestination(final PlanBDocument doc) {
        final String sharedPath = doc.getSharedPath();
        return doc.getShardCount() > 0
                && sharedPath != null
                && !sharedPath.isBlank();
    }

    /**
     * Commits all open LMDB write transactions and returns a description of the
     * resulting writer directories on disk.
     *
     * <p>If any transaction fails to commit, a composite RuntimeException is thrown
     * and the writer directory is left on disk for operator inspection.
     */
    WrittenBatch drain() {
        flushAllWriters();
        return buildWrittenBatch();
    }

    private WrittenBatch buildWrittenBatch() {
        final List<WrittenPart> parts = writers.entrySet().stream()
                .map(e -> new WrittenPart(
                        e.getValue().localWriterDir,
                        e.getKey().doc,
                        e.getKey().shardIndex,
                        e.getValue().isSynchroniseMerge(),
                        e.getValue().destination))
                .toList();
        return new WrittenBatch(dir, meta, parts);
    }

    /**
     * Commits and closes every open LMDB write transaction.
     * Each writer is attempted regardless of earlier failures so that no native
     * file handle or memory-mapped region is left open. All failures are collected
     * and re-thrown together as suppressed exceptions on a single composite.
     */
    private void flushAllWriters() {
        final List<Exception> closeErrors = new ArrayList<>();
        for (final WriterInstance wi : writers.values()) {
            try {
                wi.close();
            } catch (final Exception e) {
                closeErrors.add(e);
                LOGGER.error(() -> LogUtil.message(
                        "Failed to close WriterInstance for meta {}", meta.getId()), e);
            }
        }
        if (!closeErrors.isEmpty()) {
            final RuntimeException composite = new RuntimeException(
                    "One or more writers failed to close for meta " + meta.getId());
            closeErrors.forEach(composite::addSuppressed);
            throw composite;
        }
    }

    /**
     * Flushes all LMDB write transactions via {@link #drain()}, then delegates
     * publish and cleanup to the {@link BatchDestination}.
     *
     * <p>If {@link #drain()} throws (flush failure), the writer directory is retained
     * on disk for operator inspection — {@link BatchDestination#publish} is not called.
     */
    @Override
    public void close() {
        LOGGER.info(() -> LogUtil.message(
                "Plan B finished processing for meta {}", meta.getId()));
        final WrittenBatch batch = drain();
        try {
            batchDestination.publish(batch);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
