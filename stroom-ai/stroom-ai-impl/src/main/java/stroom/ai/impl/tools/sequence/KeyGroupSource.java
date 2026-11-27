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

package stroom.ai.impl.tools.sequence;

import stroom.ai.api.ElasticKeyStreams;
import stroom.ai.api.ElasticKeyStreams.KeyCount;
import stroom.ai.api.ElasticKeyStreams.KeyPage;
import stroom.ai.api.ElasticKeyStreams.KeyRequest;
import stroom.ai.api.ElasticKeyStreams.NextPage;
import stroom.ai.api.ElasticKeyStreams.Row;
import stroom.ai.api.ElasticKeyStreams.Scan;
import stroom.ai.api.ElasticKeyStreams.ScanPage;
import stroom.ai.api.ElasticKeyStreams.ScanRequest;
import stroom.ai.api.ElasticKeyStreams.Side;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;

/// One side of a streaming join, read a key at a time in key order: each key with its events (a scan), or with its
/// count alone (keys). A side over several chunks (a key range cut into batches of pushed-down terms, each a range
/// of its own in key order) reads them one after another, so its keys stay in order.
public interface KeyGroupSource extends AutoCloseable {

    /// The most events one key may hold in memory; past it the rest of the key's events are counted, not kept.
    int MAX_KEY_ROWS = 100_000;

    /// @return The next key with its events, or null at the end (or once the join's budget ends reading).
    KeyGroup next();

    /// @return How many keys had more than [#MAX_KEY_ROWS] events, so not all of them were kept.
    long cutKeys();

    /// Closes what the side holds open (a scan's point in time).
    @Override
    void close();

    /// The events of a side, a key at a time: a point-in-time scan per chunk, paged.
    ///
    /// @param streams    The key streams.
    /// @param chunks     The side over each chunk, in key order.
    /// @param fields     The fields to fetch.
    /// @param pageSize   The events a page.
    /// @param comparator The order of keys.
    /// @param budget     What the join may read.
    /// @return The side.
    static KeyGroupSource scan(final ElasticKeyStreams streams,
                               final List<Side> chunks,
                               final List<String> fields,
                               final int pageSize,
                               final Comparator<List<Object>> comparator,
                               final JoinBudget budget) {
        return new Scanned(streams, chunks, fields, pageSize, comparator, budget);
    }

    /// The keys of a side, each with its count: a composite aggregation per chunk, paged.
    ///
    /// @param streams  The key streams.
    /// @param chunks   The side over each chunk, in key order.
    /// @param pageSize The keys a page.
    /// @param budget   What the join may read.
    /// @return The side.
    static KeyGroupSource keys(final ElasticKeyStreams streams,
                               final List<Side> chunks,
                               final int pageSize,
                               final JoinBudget budget) {
        return new Keyed(streams, chunks, pageSize, budget);
    }

    /// Keys already read (a pushed-down side), in key order.
    ///
    /// @param keys The keys.
    /// @return The side.
    static KeyGroupSource listed(final List<KeyCount> keys) {
        return new Listed(keys);
    }


    // --------------------------------------------------------------------------------


    /// One key of a side.
    ///
    /// @param key   Its value per join field.
    /// @param count Its events (all of them, even past [#MAX_KEY_ROWS]).
    /// @param rows  Its events in time order (none when the side is read as keys).
    record KeyGroup(List<Object> key, long count, List<Row> rows) {

        /// Copies the lists.
        public KeyGroup {
            key = key == null
                    ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(key));
            rows = rows == null
                    ? List.of()
                    : List.copyOf(rows);
        }
    }


    // --------------------------------------------------------------------------------


    /// A side read by scans.
    final class Scanned implements KeyGroupSource {

        private final ElasticKeyStreams streams;
        private final List<Side> chunks;
        private final List<String> fields;
        private final int pageSize;
        private final Comparator<List<Object>> comparator;
        private final JoinBudget budget;
        private int chunk = -1;
        private Scan scan;
        private boolean chunkDone = true;
        private List<Row> page = List.of();
        private int at;
        private long cutKeys;

        private Scanned(final ElasticKeyStreams streams,
                        final List<Side> chunks,
                        final List<String> fields,
                        final int pageSize,
                        final Comparator<List<Object>> comparator,
                        final JoinBudget budget) {
            this.streams = streams;
            this.chunks = List.copyOf(chunks);
            this.fields = List.copyOf(fields);
            this.pageSize = pageSize;
            this.comparator = comparator;
            this.budget = budget;
        }

        @Override
        public KeyGroup next() {
            final Row first = peek();
            if (first == null) {
                return null;
            }
            final List<Object> key = first.key();
            final List<Row> rows = new ArrayList<>();
            long count = 0;
            Row row = first;
            while (row != null && comparator.compare(key, row.key()) == 0) {
                if (rows.size() < MAX_KEY_ROWS) {
                    rows.add(row);
                }
                count++;
                at++;
                row = peek();
            }
            if (count > MAX_KEY_ROWS) {
                cutKeys++;
            }
            return new KeyGroup(key, count, rows);
        }

        // The next event, reading pages (and chunks) as needed; null at the end.
        private Row peek() {
            while (at >= page.size()) {
                if (!readPage()) {
                    return null;
                }
            }
            return page.get(at);
        }

        // Reads the next page, or the next chunk's first; false at the end.
        private boolean readPage() {
            if (budget.check()) {
                return false;
            }
            if (chunkDone) {
                chunk++;
                if (chunk >= chunks.size()) {
                    return false;
                }
                scan = streams.openScan(new ScanRequest(chunks.get(chunk), fields, pageSize, budget.remaining(),
                        stoppedFlag()));
                chunkDone = false;
            }
            final ScanPage read = streams.scanPage(new NextPage(scan, budget.remaining()));
            budget.readRows(read.rows().size());
            page = read.rows();
            at = 0;
            if (read.next() == null) {
                chunkDone = true;
                scan = null;
            } else {
                scan = read.next();
            }
            return true;
        }

        private BooleanSupplier stoppedFlag() {
            return budget::stoppedByUser;
        }

        @Override
        public long cutKeys() {
            return cutKeys;
        }

        @Override
        public void close() {
            if (scan != null) {
                streams.close(scan);
                scan = null;
            }
        }
    }


    // --------------------------------------------------------------------------------


    /// A side read as keys.
    final class Keyed implements KeyGroupSource {

        private final ElasticKeyStreams streams;
        private final List<Side> chunks;
        private final int pageSize;
        private final JoinBudget budget;
        private int chunk = -1;
        private List<Object> after;
        private boolean chunkDone = true;
        private List<KeyCount> page = List.of();
        private int at;

        private Keyed(final ElasticKeyStreams streams,
                      final List<Side> chunks,
                      final int pageSize,
                      final JoinBudget budget) {
            this.streams = streams;
            this.chunks = List.copyOf(chunks);
            this.pageSize = pageSize;
            this.budget = budget;
        }

        @Override
        public KeyGroup next() {
            while (at >= page.size()) {
                if (!readPage()) {
                    return null;
                }
            }
            final KeyCount key = page.get(at++);
            return new KeyGroup(key.key(), key.count(), List.of());
        }

        private boolean readPage() {
            if (budget.check()) {
                return false;
            }
            if (chunkDone) {
                chunk++;
                if (chunk >= chunks.size()) {
                    return false;
                }
                after = null;
                chunkDone = false;
            }
            final KeyPage read = streams.keyPage(new KeyRequest(chunks.get(chunk), after, pageSize,
                    budget.remaining(), budget::stoppedByUser));
            budget.readRows(read.keys().size());
            page = read.keys();
            at = 0;
            after = read.after();
            if (after == null || read.keys().size() < pageSize) {
                chunkDone = true;
            }
            return true;
        }

        @Override
        public long cutKeys() {
            return 0;
        }

        @Override
        public void close() {
            // Nothing is held open.
        }
    }


    // --------------------------------------------------------------------------------


    /// Keys already read.
    final class Listed implements KeyGroupSource {

        private final List<KeyCount> keys;
        private int at;

        private Listed(final List<KeyCount> keys) {
            this.keys = List.copyOf(keys);
        }

        @Override
        public KeyGroup next() {
            if (at >= keys.size()) {
                return null;
            }
            final KeyCount key = keys.get(at++);
            return new KeyGroup(key.key(), key.count(), List.of());
        }

        @Override
        public long cutKeys() {
            return 0;
        }

        @Override
        public void close() {
            // Nothing is held open.
        }
    }
}
