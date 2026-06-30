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

import stroom.planb.impl.data.RangeState;
import stroom.planb.impl.data.Session;
import stroom.planb.impl.data.SpanKV;
import stroom.planb.impl.data.State;
import stroom.planb.impl.data.TemporalRangeState;
import stroom.planb.impl.data.TemporalState;
import stroom.planb.impl.data.TemporalValue;
import stroom.planb.impl.db.histogram.HistogramDb;
import stroom.planb.impl.db.metric.MetricDb;
import stroom.planb.impl.db.rangestate.RangeStateDb;
import stroom.planb.impl.db.session.SessionDb;
import stroom.planb.impl.db.state.StateDb;
import stroom.planb.impl.db.temporalrangestate.TemporalRangeStateDb;
import stroom.planb.impl.db.temporalstate.TemporalStateDb;
import stroom.planb.impl.db.trace.TraceDb;

import java.nio.file.Path;

/**
 * Wraps a single open LMDB environment and its active write transaction.
 * Holds the environment open for the lifetime of a pipeline stream so that
 * all rows for one (doc, shardIndex) pair are committed in a single transaction.
 *
 * <p>Close order matters: the write transaction (LmdbWriter) must be
 * closed before the environment (Db). The finally block in close()
 * guarantees this even if the transaction close throws.
 *
 * <p>If the write transaction cannot be created in the constructor, the
 * LMDB environment is closed immediately to prevent a native resource leak.
 */
class WriterInstance implements AutoCloseable {

    /** The local LMDB writer directory for this (doc, shardIndex) pair. */
    final Path localWriterDir;
    /** The destination this part's data should be transferred to. */
    final PartDestination destination;
    private final Db<?, ?> lmdb;
    private final LmdbWriter writer;
    private final boolean synchroniseMerge;

    WriterInstance(final Db<?, ?> lmdb,
                   final boolean synchroniseMerge,
                   final Path localWriterDir,
                   final PartDestination destination) {
        this.localWriterDir = localWriterDir;
        this.destination = destination;
        this.lmdb = lmdb;
        // If createWriter() throws, close the native LMDB environment immediately
        // to prevent a file-handle and memory-mapped region leak.
        final LmdbWriter w;
        try {
            w = lmdb.createWriter();
        } catch (final Exception e) {
            lmdb.close();
            throw e;
        }
        this.writer = w;
        this.synchroniseMerge = synchroniseMerge;
    }

    void addState(final State state) {
        final StateDb db = (StateDb) lmdb;
        db.insert(writer, state);
    }

    void addTemporalState(final TemporalState temporalState) {
        final TemporalStateDb db = (TemporalStateDb) lmdb;
        db.insert(writer, temporalState);
    }

    void addRangeState(final RangeState rangeState) {
        final RangeStateDb db = (RangeStateDb) lmdb;
        db.insert(writer, rangeState);
    }

    void addTemporalRangeState(final TemporalRangeState temporalRangeState) {
        final TemporalRangeStateDb db = (TemporalRangeStateDb) lmdb;
        db.insert(writer, temporalRangeState);
    }

    void addSession(final Session session) {
        final SessionDb db = (SessionDb) lmdb;
        db.insert(writer, session);
    }

    void addHistogramValue(final TemporalValue temporalValue) {
        final HistogramDb db = (HistogramDb) lmdb;
        db.insert(writer, temporalValue);
    }

    void addMetricValue(final TemporalValue temporalValue) {
        final MetricDb db = (MetricDb) lmdb;
        db.insert(writer, temporalValue);
    }

    void addSpanValue(final SpanKV spanKV) {
        final TraceDb db = (TraceDb) lmdb;
        db.insert(writer, spanKV);
    }

    boolean isSynchroniseMerge() {
        return synchroniseMerge;
    }

    @Override
    public void close() {
        try {
            writer.close();
        } finally {
            lmdb.close();
        }
    }
}
