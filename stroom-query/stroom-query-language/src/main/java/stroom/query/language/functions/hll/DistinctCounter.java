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

package stroom.query.language.functions.hll;

import stroom.query.language.functions.ref.DataReader;
import stroom.query.language.functions.ref.DataWriter;

import java.util.Arrays;

/// Counts the unique values of a group by their hashes. The count is exact up to a limit, and is estimated by a sketch
/// once there are more than that.
///
/// The sketch is made from all of the hashes, so it is the same as if it had been used from the start. A counter that
/// has made a sketch keeps it, even if the limit is raised.
///
/// This class is not thread safe, except that [#count()] can be called while another thread adds to the counter.
public final class DistinctCounter {

    private static final int PRECISION = HyperLogLogPlusPlus.DEFAULT_PRECISION;
    // This is written in place of one more than the number of hashes.
    private static final int SKETCH = 0;
    private static final long[] NONE = new long[0];

    private final int limit;
    // In ascending order.
    private long[] hashes = NONE;
    private volatile int hashCount;
    // Used in place of the hashes if it isn't null. It is only set once it holds everything.
    private volatile HyperLogLogPlusPlus sketch;

    /// Create a counter that has counted nothing.
    ///
    /// @param limit The most unique values that are counted exactly.
    public DistinctCounter(final int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be at least 1 but was " + limit);
        }
        this.limit = limit;
    }

    /// Gets the number of unique hashes.
    ///
    /// @return The number, which is an estimate if there have been more than the limit.
    public long count() {
        final HyperLogLogPlusPlus current = sketch;
        return current == null
                ? hashCount
                : current.estimate();
    }

    /// Adds a hash, which does nothing if it has been added before.
    ///
    /// @param hash The hash.
    public void add(final long hash) {
        if (sketch != null) {
            sketch.addHash(hash);
            return;
        }

        final int found = Arrays.binarySearch(hashes, 0, hashCount, hash);
        if (found < 0) {
            if (hashCount < limit) {
                insert(-found - 1, hash);
            } else {
                final HyperLogLogPlusPlus newSketch = createSketch();
                newSketch.addHash(hash);
                setSketch(newSketch);
            }
        }
    }

    private void insert(final int index, final long hash) {
        final int count = hashCount;
        if (count == hashes.length) {
            hashes = Arrays.copyOf(hashes, Math.clamp(count * 2L, 1, limit));
        }
        System.arraycopy(hashes, index, hashes, index + 1, count - index);
        hashes[index] = hash;
        hashCount = count + 1;
    }

    /// Adds everything that another counter has counted. The other counter is not changed.
    ///
    /// @param other The other counter.
    public void merge(final DistinctCounter other) {
        if (sketch == null && other.sketch == null) {
            if (other.hashCount == 1) {
                // As when each event is added to its group.
                add(other.hashes[0]);
            } else if (other.hashCount > 1) {
                mergeHashes(other);
            }
        } else if (sketch == null) {
            final HyperLogLogPlusPlus newSketch = createSketch();
            newSketch.merge(other.sketch);
            setSketch(newSketch);
        } else if (other.sketch == null) {
            other.addHashesTo(sketch);
        } else {
            sketch.merge(other.sketch);
        }
    }

    private void mergeHashes(final DistinctCounter other) {
        final int count1 = hashCount;
        final int count2 = other.hashCount;
        final long[] merged = new long[Math.addExact(count1, count2)];
        int count = 0;
        int i = 0;
        int j = 0;
        while (i < count1 || j < count2) {
            if (j == count2 || (i < count1 && hashes[i] < other.hashes[j])) {
                merged[count++] = hashes[i++];
            } else {
                if (i < count1 && hashes[i] == other.hashes[j]) {
                    i++;
                }
                merged[count++] = other.hashes[j++];
            }
        }

        if (count <= limit) {
            hashes = merged;
            hashCount = count;
        } else {
            final HyperLogLogPlusPlus newSketch = createSketch();
            other.addHashesTo(newSketch);
            setSketch(newSketch);
        }
    }

    private HyperLogLogPlusPlus createSketch() {
        final HyperLogLogPlusPlus newSketch = new HyperLogLogPlusPlus(PRECISION);
        addHashesTo(newSketch);
        return newSketch;
    }

    private void addHashesTo(final HyperLogLogPlusPlus target) {
        for (int i = 0; i < hashCount; i++) {
            target.addHash(hashes[i]);
        }
    }

    // The number of hashes is left as it is, as another thread could be about to read it.
    private void setSketch(final HyperLogLogPlusPlus newSketch) {
        sketch = newSketch;
        hashes = NONE;
    }

    /// Reads a counter written by [#write(DataWriter)].
    ///
    /// @param reader The reader to read from.
    /// @param limit  The most unique values that are counted exactly, which may have changed since it was written.
    /// @return The counter.
    public static DistinctCounter read(final DataReader reader, final int limit) {
        final DistinctCounter counter = new DistinctCounter(limit);
        final int length = reader.readVarInt();
        if (length == SKETCH) {
            counter.sketch = HyperLogLogPlusPlus.read(reader, PRECISION);
        } else if (length < 0) {
            throw new IllegalArgumentException("Invalid number of hashes " + length);
        } else {
            counter.readHashes(reader, length - 1);
        }
        return counter;
    }

    private void readHashes(final DataReader reader, final int count) {
        if (count <= limit) {
            hashes = new long[count];
        }
        long previous = 0;
        for (int i = 0; i < count; i++) {
            final long hash = reader.readLong();
            if (i > 0 && hash <= previous) {
                throw new IllegalArgumentException("Hashes are not in order");
            }
            previous = hash;
            if (count <= limit) {
                hashes[i] = hash;
            } else {
                // There is no need to hold them all first.
                if (sketch == null) {
                    sketch = new HyperLogLogPlusPlus(PRECISION);
                }
                sketch.addHash(hash);
            }
        }
        hashCount = count <= limit
                ? count
                : 0;
    }

    /// Writes this counter so that it can be read by [#read(DataReader, int)].
    ///
    /// @param writer The writer to write to.
    public void write(final DataWriter writer) {
        if (sketch != null) {
            writer.writeVarInt(SKETCH);
            sketch.write(writer);
        } else {
            writer.writeVarInt(hashCount + 1);
            for (int i = 0; i < hashCount; i++) {
                writer.writeLong(hashes[i]);
            }
        }
    }
}
