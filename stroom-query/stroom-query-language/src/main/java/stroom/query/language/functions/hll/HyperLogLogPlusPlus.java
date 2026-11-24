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
import stroom.util.logging.LogUtil;

import java.util.Arrays;
import java.util.Objects;

/// A HyperLogLog++ sketch, which estimates how many distinct values it has been given without storing them.
/// Values are given as 64-bit hashes, which must be evenly spread over all 64 bits.
///
/// A sketch starts sparse, as a sorted list of the hash prefixes it has seen, and is all but exact. Once the
/// list would take more bytes to store than the dense form, it becomes dense, as 2^precision registers of 6 bits
/// each. The limit is on stored bytes, so a sparse sketch can take a few times more memory than a dense one.
/// The standard error of the dense form is 1.04 / sqrt(2^precision).
///
/// The state depends only on the set of hashes added and not on the order of adding or merging, so the same
/// set of hashes always gives the same estimate and the same serialised bytes.
///
/// This class is not thread safe.
public class HyperLogLogPlusPlus {

    /// The lowest precision a sketch can have.
    public static final int MIN_PRECISION = 10;
    /// The highest precision a sketch can have.
    public static final int MAX_PRECISION = 16;
    /// The precision to use unless another is asked for.
    public static final int DEFAULT_PRECISION = 14;

    // Change this if the serialised form changes.
    private static final byte FORMAT_VERSION = 1;
    private static final byte TYPE_SPARSE = 0;
    private static final byte TYPE_DENSE = 1;

    // The number of leading hash bits that pick a sparse index, and the number of bits left after them.
    private static final int SPARSE_PRECISION = 25;
    private static final int SPARSE_INDEX_COUNT = 1 << SPARSE_PRECISION;
    private static final int SPARSE_TAIL_BITS = Long.SIZE - SPARSE_PRECISION;

    // A sparse entry is a sparse index followed by this many bits for a rank.
    private static final int RANK_BITS = 6;
    private static final int RANK_MASK = (1 << RANK_BITS) - 1;

    private static final int INITIAL_SPARSE_CAPACITY = 4;
    private static final int[] NO_ENTRIES = new int[0];

    // A sketch with fewer entries than another by this factor is added to it one entry at a time.
    private static final int SMALL_MERGE_FACTOR = 16;

    // StrictMath gives the same estimate on every node.
    private static final double ALPHA = 1 / (2 * StrictMath.log(2));

    private final int precision;
    private final int registerCount;
    private final int denseByteCount;

    // The sparse form, ordered by index with one entry per index, and the bytes it takes when serialised.
    // A rank of 0 means the rank can be worked out from the index, see toSparseEntry().
    private int[] sparseEntries = NO_ENTRIES;
    private int sparseCount;
    private int sparseByteCount;

    // The dense form, or null if this sketch is still sparse.
    private byte[] registers;

    /// Create an empty sketch.
    ///
    /// @param precision The precision, from [#MIN_PRECISION] to [#MAX_PRECISION]. Each step up doubles the size
    ///                  of the dense form and divides the standard error by the square root of 2.
    /// @throws IllegalArgumentException If the precision is out of range.
    public HyperLogLogPlusPlus(final int precision) {
        if (precision < MIN_PRECISION || precision > MAX_PRECISION) {
            throw new IllegalArgumentException(LogUtil.message(
                    "Precision must be from {} to {} but was {}", MIN_PRECISION, MAX_PRECISION, precision));
        }
        this.precision = precision;
        this.registerCount = 1 << precision;
        this.denseByteCount = registerCount * RANK_BITS / Byte.SIZE;
    }

    /// Get the precision of this sketch.
    ///
    /// @return The precision, from [#MIN_PRECISION] to [#MAX_PRECISION].
    public int getPrecision() {
        return precision;
    }

    /// Find out which form this sketch is in.
    ///
    /// @return True if this sketch is sparse, false if it is dense.
    boolean isSparse() {
        return registers == null;
    }

    /// Add the hash of a value. Adding a hash that has already been added does nothing.
    ///
    /// @param hash The 64-bit hash of the value.
    public void addHash(final long hash) {
        if (isSparse()) {
            addSparseEntry(toSparseEntry(hash));
            makeDenseIfSmaller();
        } else {
            final int index = (int) (hash >>> (Long.SIZE - precision));
            raiseRegister(index, rank(hash << precision, Long.SIZE - precision));
        }
    }

    /// Add everything that has been added to another sketch. The other sketch is not changed.
    ///
    /// @param other The sketch to add, which must have the same precision as this one.
    /// @throws IllegalArgumentException If the precisions differ.
    public void merge(final HyperLogLogPlusPlus other) {
        Objects.requireNonNull(other, "Null sketch");
        if (other.precision != precision) {
            throw new IllegalArgumentException(LogUtil.message(
                    "Unable to merge a sketch of precision {} into one of precision {}",
                    other.precision, precision));
        }

        if (isSparse() && other.isSparse()) {
            if (other.sparseCount < sparseCount / SMALL_MERGE_FACTOR) {
                for (int i = 0; i < other.sparseCount; i++) {
                    addSparseEntry(other.sparseEntries[i]);
                }
            } else if (other.sparseCount > 0) {
                mergeSparse(other);
            }
            makeDenseIfSmaller();
        } else {
            if (isSparse()) {
                makeDense();
            }
            if (other.isSparse()) {
                raiseRegisters(other.sparseEntries, other.sparseCount);
            } else {
                for (int i = 0; i < registerCount; i++) {
                    raiseRegister(i, other.getRegister(i));
                }
            }
        }
    }

    /// Estimate how many distinct hashes have been added.
    ///
    /// @return The estimate, which is 0 for an empty sketch.
    public long estimate() {
        return isSparse()
                ? estimateSparse()
                : estimateDense();
    }

    /// Write this sketch so that it can be read by [#read(DataReader)].
    ///
    /// @param writer The writer to write to.
    public void write(final DataWriter writer) {
        writer.writeByte(FORMAT_VERSION);
        writer.writeByte((byte) precision);
        if (isSparse()) {
            writer.writeByte(TYPE_SPARSE);
            writer.writeBytes(encodeSparse());
        } else {
            writer.writeByte(TYPE_DENSE);
            writer.writeBytes(registers);
        }
    }

    /// Read a sketch that was written by [#write(DataWriter)].
    ///
    /// @param reader The reader to read from.
    /// @return The sketch.
    /// @throws IllegalArgumentException If the sketch has an unknown format version, is cut short or is not valid.
    ///                                  The reader allocates the stored length before it can be checked, so a
    ///                                  corrupt length can also run out of memory.
    public static HyperLogLogPlusPlus read(final DataReader reader) {
        try {
            return readSketch(reader);
        } catch (final IllegalArgumentException e) {
            throw e;
        } catch (final RuntimeException e) {
            throw new IllegalArgumentException("Invalid sketch: unable to read it", e);
        }
    }

    private static HyperLogLogPlusPlus readSketch(final DataReader reader) {
        final byte version = reader.readByte();
        if (version != FORMAT_VERSION) {
            throw new IllegalArgumentException(LogUtil.message(
                    "Unknown sketch format version {}, expected {}", version, FORMAT_VERSION));
        }

        final HyperLogLogPlusPlus sketch = new HyperLogLogPlusPlus(reader.readByte());
        final byte type = reader.readByte();
        switch (type) {
            case TYPE_SPARSE -> sketch.decodeSparse(reader.readBytes());
            case TYPE_DENSE -> sketch.decodeDense(reader.readBytes());
            default -> throw invalid(LogUtil.message("unknown type {}", type));
        }
        return sketch;
    }

    // --------------------------------------------------------------------------------
    // Sparse form
    // --------------------------------------------------------------------------------

    // The rank of a hash in the dense form is the position of the first 1 bit after the register index. If that
    // bit is inside the sparse index then the index alone gives the rank, so no rank is kept.
    private int toSparseEntry(final long hash) {
        final int index = (int) (hash >>> SPARSE_TAIL_BITS);
        final int rank = indexGivesRank(index)
                ? 0
                : rank(hash << SPARSE_PRECISION, SPARSE_TAIL_BITS);
        return (index << RANK_BITS) | rank;
    }

    private boolean indexGivesRank(final int sparseIndex) {
        return lowIndexBits(sparseIndex) != 0;
    }

    // The bits of a sparse index that come after the bits that pick a register.
    private int lowIndexBits(final int sparseIndex) {
        return sparseIndex & ((1 << (SPARSE_PRECISION - precision)) - 1);
    }

    private static int sparseIndex(final int entry) {
        return entry >>> RANK_BITS;
    }

    private static int sparseRank(final int entry) {
        return entry & RANK_MASK;
    }

    private void addSparseEntry(final int entry) {
        final int index = sparseIndex(entry);
        // Look for the lowest entry this index could have, so we get the position of any entry for the index.
        final int found = Arrays.binarySearch(sparseEntries, 0, sparseCount, index << RANK_BITS);
        final int position = found >= 0
                ? found
                : -found - 1;

        if (position < sparseCount && sparseIndex(sparseEntries[position]) == index) {
            // Entries for the same index only differ by rank, so the greater entry has the greater rank.
            sparseEntries[position] = Math.max(sparseEntries[position], entry);
        } else {
            insertSparseEntry(position, entry);
        }
    }

    private void insertSparseEntry(final int position, final int entry) {
        final int previousIndex = position == 0
                ? 0
                : sparseIndex(sparseEntries[position - 1]);

        if (position < sparseCount) {
            // The entry after this one will now be encoded relative to this one.
            final int nextEntry = sparseEntries[position];
            sparseByteCount -= encodedSize(previousIndex, nextEntry);
            sparseByteCount += encodedSize(sparseIndex(entry), nextEntry);
        }
        sparseByteCount += encodedSize(previousIndex, entry);

        if (sparseCount == sparseEntries.length) {
            sparseEntries = Arrays.copyOf(
                    sparseEntries,
                    Math.max(INITIAL_SPARSE_CAPACITY, sparseCount + (sparseCount >> 1)));
        }
        System.arraycopy(sparseEntries, position, sparseEntries, position + 1, sparseCount - position);
        sparseEntries[position] = entry;
        sparseCount++;
    }

    private void appendSparseEntry(final int entry) {
        insertSparseEntry(sparseCount, entry);
    }

    private void mergeSparse(final HyperLogLogPlusPlus other) {
        final int[] left = sparseEntries;
        final int leftCount = sparseCount;
        final int[] right = other.sparseEntries;
        final int rightCount = other.sparseCount;

        sparseEntries = new int[leftCount + rightCount];
        sparseCount = 0;
        sparseByteCount = 0;

        int leftPosition = 0;
        int rightPosition = 0;
        while (leftPosition < leftCount && rightPosition < rightCount) {
            final int leftEntry = left[leftPosition];
            final int rightEntry = right[rightPosition];
            final int leftIndex = sparseIndex(leftEntry);
            final int rightIndex = sparseIndex(rightEntry);

            if (leftIndex == rightIndex) {
                appendSparseEntry(Math.max(leftEntry, rightEntry));
                leftPosition++;
                rightPosition++;
            } else if (leftIndex < rightIndex) {
                appendSparseEntry(leftEntry);
                leftPosition++;
            } else {
                appendSparseEntry(rightEntry);
                rightPosition++;
            }
        }
        while (leftPosition < leftCount) {
            appendSparseEntry(left[leftPosition++]);
        }
        while (rightPosition < rightCount) {
            appendSparseEntry(right[rightPosition++]);
        }
        sparseEntries = Arrays.copyOf(sparseEntries, sparseCount);
    }

    // Linear counting over all the possible sparse indexes.
    private long estimateSparse() {
        final double usedFraction = (double) sparseCount / SPARSE_INDEX_COUNT;
        return Math.round(-SPARSE_INDEX_COUNT * StrictMath.log1p(-usedFraction));
    }

    // Each entry is written as the difference between its index and the one before, shifted left to make room for
    // a flag bit. The flag says that a byte follows with the rank.
    private byte[] encodeSparse() {
        final byte[] bytes = new byte[sparseByteCount];
        int position = 0;
        int previousIndex = 0;
        for (int i = 0; i < sparseCount; i++) {
            final int entry = sparseEntries[i];
            int remaining = encodedDelta(previousIndex, entry);
            while (remaining > 0x7F) {
                bytes[position++] = (byte) (remaining | 0x80);
                remaining >>>= 7;
            }
            bytes[position++] = (byte) remaining;
            if (sparseRank(entry) != 0) {
                bytes[position++] = (byte) sparseRank(entry);
            }
            previousIndex = sparseIndex(entry);
        }
        return bytes;
    }

    private static int encodedDelta(final int previousIndex, final int entry) {
        final int flag = sparseRank(entry) == 0
                ? 0
                : 1;
        return ((sparseIndex(entry) - previousIndex) << 1) | flag;
    }

    private static int encodedSize(final int previousIndex, final int entry) {
        final int delta = encodedDelta(previousIndex, entry);
        final int significantBits = Math.max(1, Integer.SIZE - Integer.numberOfLeadingZeros(delta));
        final int rankSize = sparseRank(entry) == 0
                ? 0
                : 1;
        return (significantBits + 6) / 7 + rankSize;
    }

    private void decodeSparse(final byte[] bytes) {
        if (bytes.length > denseByteCount) {
            throw invalid("sparse form is larger than the dense form");
        }

        int position = 0;
        int previousIndex = 0;
        while (position < bytes.length) {
            // Four groups of 7 bits are enough for a sparse index and the flag.
            int delta = 0;
            int shift = 0;
            boolean more = true;
            while (more) {
                if (position == bytes.length || shift > 21) {
                    throw invalid("bad sparse index");
                }
                final byte b = bytes[position++];
                delta |= (b & 0x7F) << shift;
                shift += 7;
                more = b < 0;
            }

            final int index = previousIndex + (delta >>> 1);
            int rank = 0;
            if ((delta & 1) != 0) {
                if (position == bytes.length) {
                    throw invalid("missing sparse rank");
                }
                rank = bytes[position++];
                if (rank < 1 || rank > SPARSE_TAIL_BITS + 1) {
                    throw invalid("bad sparse rank");
                }
            }
            if (index >= SPARSE_INDEX_COUNT || (sparseCount > 0 && index == previousIndex)) {
                throw invalid("bad sparse index");
            }
            if (indexGivesRank(index) != (rank == 0)) {
                throw invalid("sparse rank does not fit its index");
            }

            appendSparseEntry((index << RANK_BITS) | rank);
            previousIndex = index;
        }

        if (sparseByteCount != bytes.length) {
            throw invalid("unexpected sparse length");
        }
    }

    // --------------------------------------------------------------------------------
    // Dense form
    // --------------------------------------------------------------------------------

    // Adding entries never makes the sparse form smaller, so the form a sketch ends up in doesn't depend on the
    // order things were added.
    private void makeDenseIfSmaller() {
        if (sparseByteCount > denseByteCount) {
            makeDense();
        }
    }

    private void makeDense() {
        registers = new byte[denseByteCount];
        raiseRegisters(sparseEntries, sparseCount);
        sparseEntries = NO_ENTRIES;
        sparseCount = 0;
        sparseByteCount = 0;
    }

    private void raiseRegisters(final int[] entries, final int count) {
        final int extraBits = SPARSE_PRECISION - precision;
        for (int i = 0; i < count; i++) {
            final int index = sparseIndex(entries[i]);
            final int rank = indexGivesRank(index)
                    ? rank((long) lowIndexBits(index) << (Long.SIZE - extraBits), extraBits)
                    : extraBits + sparseRank(entries[i]);
            raiseRegister(index >>> extraBits, rank);
        }
    }

    // The position, from 1, of the first 1 bit in the top bits of a value, or one more than the number of bits if
    // they are all 0.
    private static int rank(final long value, final int bits) {
        return Math.min(Long.numberOfLeadingZeros(value), bits) + 1;
    }

    private void raiseRegister(final int index, final int rank) {
        if (rank > getRegister(index)) {
            setRegister(index, rank);
        }
    }

    // Registers are packed from the lowest bit of each byte up, so one can be spread over two bytes.
    private int getRegister(final int index) {
        final int bit = index * RANK_BITS;
        final int position = bit >>> 3;
        final int shift = bit & 7;
        int value = (registers[position] & 0xFF) >>> shift;
        if (shift + RANK_BITS > Byte.SIZE) {
            value |= (registers[position + 1] & 0xFF) << (Byte.SIZE - shift);
        }
        return value & RANK_MASK;
    }

    private void setRegister(final int index, final int rank) {
        final int bit = index * RANK_BITS;
        final int position = bit >>> 3;
        final int shift = bit & 7;
        registers[position] = (byte) ((registers[position] & ~(RANK_MASK << shift)) | (rank << shift));
        if (shift + RANK_BITS > Byte.SIZE) {
            final int nextShift = Byte.SIZE - shift;
            registers[position + 1] = (byte) ((registers[position + 1] & ~(RANK_MASK >>> nextShift))
                                              | (rank >>> nextShift));
        }
    }

    private int maxRank() {
        return Long.SIZE - precision + 1;
    }

    // The improved raw estimator from Ertl, "New cardinality estimation algorithms for HyperLogLog sketches" (2017),
    // which needs no bias tables.
    private long estimateDense() {
        final int maxRank = maxRank();
        final int[] rankCounts = new int[maxRank + 1];
        for (int i = 0; i < registerCount; i++) {
            rankCounts[getRegister(i)]++;
        }

        final double m = registerCount;
        double z = m * tau(1 - rankCounts[maxRank] / m);
        for (int rank = maxRank - 1; rank >= 1; rank--) {
            z = 0.5 * (z + rankCounts[rank]);
        }
        z += m * sigma(rankCounts[0] / m);
        return Math.round(ALPHA * m * m / z);
    }

    private static double sigma(final double value) {
        if (value == 1) {
            return Double.POSITIVE_INFINITY;
        }
        double x = value;
        double y = 1;
        double z = x;
        double previousZ;
        do {
            x *= x;
            previousZ = z;
            z += x * y;
            y += y;
        } while (z != previousZ);
        return z;
    }

    private static double tau(final double value) {
        if (value == 0 || value == 1) {
            return 0;
        }
        double x = value;
        double y = 1;
        double z = 1 - x;
        double previousZ;
        do {
            x = Math.sqrt(x);
            previousZ = z;
            y *= 0.5;
            z -= (1 - x) * (1 - x) * y;
        } while (z != previousZ);
        return z / 3;
    }

    private void decodeDense(final byte[] bytes) {
        if (bytes.length != denseByteCount) {
            throw invalid("unexpected dense length");
        }
        registers = bytes;
        for (int i = 0; i < registerCount; i++) {
            if (getRegister(i) > maxRank()) {
                throw invalid("bad register");
            }
        }
    }

    private static IllegalArgumentException invalid(final String reason) {
        return new IllegalArgumentException("Invalid sketch: " + reason);
    }
}
