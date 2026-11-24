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

import stroom.query.language.functions.ref.KryoDataReader;
import stroom.query.language.functions.ref.KryoDataWriter;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HexFormat;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestHyperLogLogPlusPlus {

    private static final long SEED = 3502;
    private static final int DENSE_HASH = -1917866931;

    @Test
    void testEmpty() {
        final HyperLogLogPlusPlus sketch = new HyperLogLogPlusPlus(14);
        assertThat(sketch.estimate()).isZero();
        assertThat(sketch.isSparse()).isTrue();
        assertThat(sketch.getPrecision()).isEqualTo(14);
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 9, 17, Integer.MAX_VALUE})
    void testBadPrecision(final int precision) {
        assertThatThrownBy(() -> new HyperLogLogPlusPlus(precision))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Precision must be from 10 to 16 but was " + precision);
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 14, 16})
    void testError(final int precision) {
        final double standardError = standardError(precision);
        final HyperLogLogPlusPlus sketch = new HyperLogLogPlusPlus(precision);
        final SplittableRandom random = new SplittableRandom(SEED);
        int added = 0;
        for (int n = 1; n <= 10_000_000; n *= 10) {
            while (added < n) {
                sketch.addHash(random.nextLong());
                added++;
            }
            assertThat(relativeError(sketch, n))
                    .as("n=%s", n)
                    .isBetween(-3 * standardError, 3 * standardError);
        }
        assertThat(sketch.isSparse()).isFalse();
    }

    // A single sketch can be close by luck, so look at the spread and the bias of many.
    @ParameterizedTest
    @CsvSource({
            // Just dense, the small to mid range that needed bias tables in the original, and the raw range.
            "10,400",
            "10,1000",
            "10,2500",
            "10,5000",
            "10,50000",
            "14,8000",
            "14,16000",
            "14,40000",
            "14,80000",
            "14,400000"})
    void testErrorSpreadAndBias(final int precision, final int n) {
        final int trials = 200;
        final SplittableRandom random = new SplittableRandom(SEED);
        double sum = 0;
        double sumOfSquares = 0;
        for (int trial = 0; trial < trials; trial++) {
            final HyperLogLogPlusPlus sketch = new HyperLogLogPlusPlus(precision);
            for (int i = 0; i < n; i++) {
                sketch.addHash(random.nextLong());
            }
            assertThat(sketch.isSparse()).isFalse();
            final double error = relativeError(sketch, n);
            sum += error;
            sumOfSquares += error * error;
        }

        final double standardError = standardError(precision);
        final double bias = sum / trials;
        final double rootMeanSquare = Math.sqrt(sumOfSquares / trials);
        // The mean of many trials is far closer to 0 than one trial, if there is no bias.
        assertThat(Math.abs(bias)).isLessThan(3 * standardError / Math.sqrt(trials));
        // The standard deviation of a measured standard deviation is about 5% for 200 trials.
        assertThat(rootMeanSquare).isLessThan(1.15 * standardError);
    }

    @ParameterizedTest
    @ValueSource(ints = {14, 15, 16})
    void testExactWhileSparse(final int precision) {
        final HyperLogLogPlusPlus sketch = new HyperLogLogPlusPlus(precision);
        final SplittableRandom random = new SplittableRandom(SEED);
        for (int n = 1; n <= 1000; n++) {
            sketch.addHash(random.nextLong());
            assertThat(sketch.estimate()).isEqualTo(n);
        }
        assertThat(sketch.isSparse()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 100_000})
    void testRepeatsAreNotCounted(final int n) {
        final long[] hashes = hashes(n);
        final HyperLogLogPlusPlus sketch = sketch(14, hashes);
        final byte[] before = write(sketch);
        for (final long hash : hashes) {
            sketch.addHash(hash);
        }
        assertThat(write(sketch)).isEqualTo(before);
    }

    // Hashes where the rank comes from the end of the index, the start of the tail, or nothing at all.
    @ParameterizedTest
    @ValueSource(ints = {10, 11, 12, 13, 14, 15, 16})
    void testSparseAndDenseAgreeOnExtremeHashes(final int precision) {
        final long[] extremes = new long[2 * Long.SIZE + 2];
        for (int bit = 0; bit < Long.SIZE; bit++) {
            extremes[2 * bit] = 1L << bit;
            extremes[2 * bit + 1] = -1L << bit;
        }
        extremes[2 * Long.SIZE] = 0;
        extremes[2 * Long.SIZE + 1] = Long.MAX_VALUE;
        final long[] others = hashes(50_000);

        // All the extremes go through the sparse form.
        final HyperLogLogPlusPlus sparseFirst = sketch(precision, extremes);
        assertThat(sparseFirst.isSparse()).isTrue();
        assertRoundTrip(sparseFirst);
        final HyperLogLogPlusPlus denseFirst = sketch(precision, others);
        assertThat(denseFirst.isSparse()).isFalse();

        final HyperLogLogPlusPlus merged = read(write(denseFirst));
        merged.merge(sparseFirst);
        for (final long hash : others) {
            sparseFirst.addHash(hash);
        }
        // All the extremes go straight into the registers.
        for (final long hash : extremes) {
            denseFirst.addHash(hash);
        }

        assertThat(write(sparseFirst)).isEqualTo(write(denseFirst));
        assertThat(write(merged)).isEqualTo(write(denseFirst));
        assertRoundTrip(denseFirst);
    }

    // Work out every register the slow way, straight from the definition, and compare.
    @ParameterizedTest
    @ValueSource(ints = {10, 11, 12, 13, 14, 15, 16})
    void testDenseRegisters(final int precision) {
        final long[] hashes = hashes(100_000);
        final int[] expected = new int[1 << precision];
        for (final long hash : hashes) {
            final String bits = String.format("%64s", Long.toBinaryString(hash)).replace(' ', '0');
            final int register = Integer.parseInt(bits.substring(0, precision), 2);
            final int rank = bits.substring(precision).indexOf('1') + 1;
            expected[register] = Math.max(expected[register], rank);
        }

        final byte[] bytes = write(sketch(precision, hashes));
        final BitSet registers = BitSet.valueOf(Arrays.copyOfRange(bytes, 7, bytes.length));
        final int[] actual = new int[1 << precision];
        for (int register = 0; register < actual.length; register++) {
            for (int bit = 0; bit < 6; bit++) {
                if (registers.get(register * 6 + bit)) {
                    actual[register] |= 1 << bit;
                }
            }
        }
        assertThat(actual).isEqualTo(expected);
    }

    // Give each register the rank it would be expected to have after adding n hashes, so there is no random error.
    // The greatest counts are too many to add, and have registers that can go no higher.
    @ParameterizedTest
    @ValueSource(ints = {17, 20, 30, 40, 50, 60, 61, 62})
    void testEstimateOfExpectedRegisters(final int log2) {
        final int precision = 14;
        final int registerCount = 1 << precision;
        final int tailBits = Long.SIZE - precision;
        final double n = Math.pow(2, log2);

        final HyperLogLogPlusPlus sketch = new HyperLogLogPlusPlus(precision);
        for (int register = 0; register < registerCount; register++) {
            final double quantile = (register + 0.5) / registerCount;
            int rank = 0;
            // The chance that a register has no more than this rank.
            while (rank <= tailBits && Math.exp(-n / registerCount * Math.pow(2, -rank)) < quantile) {
                rank++;
            }
            if (rank > 0) {
                final long tail = rank > tailBits
                        ? 0
                        : 1L << (tailBits - rank);
                sketch.addHash(((long) register << tailBits) | tail);
            }
        }

        assertThat(sketch.isSparse()).isFalse();
        assertThat(sketch.estimate() / n).isBetween(0.999, 1.001);
    }

    // The index is the same but the first 1 bit of the tail is not, so the greater rank must be kept.
    @Test
    void testSparseEntriesWithSameIndex() {
        final long highRank = 1L << 2;
        final long lowRank = 1L << 30;
        final byte[] expected = write(sketch(14, new long[]{highRank}));

        assertThat(write(sketch(14, new long[]{highRank, lowRank}))).isEqualTo(expected);
        assertThat(write(sketch(14, new long[]{lowRank, highRank}))).isEqualTo(expected);

        final HyperLogLogPlusPlus highFirst = sketch(14, new long[]{highRank});
        highFirst.merge(sketch(14, new long[]{lowRank}));
        assertThat(write(highFirst)).isEqualTo(expected);

        final HyperLogLogPlusPlus lowFirst = sketch(14, new long[]{lowRank});
        lowFirst.merge(sketch(14, new long[]{highRank}));
        assertThat(write(lowFirst)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 14, 16})
    void testOrderOfAddingMakesNoDifference(final int precision) {
        for (final int n : new int[]{0, 1, 2, 250, 5_000, 20_000, 100_000}) {
            final long[] hashes = hashes(n);
            final long[] sorted = hashes.clone();
            Arrays.sort(sorted);
            final long[] reversed = new long[n];
            for (int i = 0; i < n; i++) {
                reversed[i] = hashes[n - 1 - i];
            }

            final byte[] expected = write(sketch(precision, hashes));
            assertThat(write(sketch(precision, sorted))).isEqualTo(expected);
            assertThat(write(sketch(precision, reversed))).isEqualTo(expected);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "10,20,30,SPARSE,SPARSE,SPARSE",
            "14,0,0,SPARSE,SPARSE,SPARSE",
            "14,0,1,SPARSE,SPARSE,SPARSE",
            "14,1000,1000,SPARSE,SPARSE,SPARSE",
            // One much smaller than the other.
            "14,1000,10,SPARSE,SPARSE,SPARSE",
            "14,1600,100,SPARSE,SPARSE,SPARSE",
            "14,1616,100,SPARSE,SPARSE,SPARSE",
            "10,200,200,SPARSE,SPARSE,DENSE",
            "14,4000,4000,SPARSE,SPARSE,DENSE",
            "16,15000,15000,SPARSE,SPARSE,DENSE",
            "10,10,10000,SPARSE,DENSE,DENSE",
            "14,0,100000,SPARSE,DENSE,DENSE",
            "14,1000,100000,SPARSE,DENSE,DENSE",
            "10,10000,10000,DENSE,DENSE,DENSE",
            "14,100000,200000,DENSE,DENSE,DENSE",
            "16,100000,200000,DENSE,DENSE,DENSE"})
    void testMerge(final int precision,
                   final int leftCount,
                   final int rightCount,
                   final Form leftForm,
                   final Form rightForm,
                   final Form mergedForm) {
        // Have some hashes in both.
        final int shared = Math.min(leftCount, rightCount) / 4;
        final long[] hashes = hashes(leftCount + rightCount - shared);
        final long[] left = Arrays.copyOfRange(hashes, 0, leftCount);
        final long[] right = Arrays.copyOfRange(hashes, leftCount - shared, hashes.length);
        final HyperLogLogPlusPlus expected = sketch(precision, hashes);

        // Make sure this still tests the forms it is meant to.
        assertThat(form(sketch(precision, left))).isEqualTo(leftForm);
        assertThat(form(sketch(precision, right))).isEqualTo(rightForm);
        assertThat(form(expected)).isEqualTo(mergedForm);

        assertMerge(precision, left, right, write(expected));
        assertMerge(precision, right, left, write(expected));
    }

    // A small sketch is added one entry at a time, which must also be able to make the sketch dense.
    @Test
    void testSmallMergeGoesDense() {
        final long[] hashes = hashes(20_000);
        final HyperLogLogPlusPlus sketch = new HyperLogLogPlusPlus(14);
        int n = 0;
        while (sketch.isSparse()) {
            final long[] next = Arrays.copyOfRange(hashes, n, n + 10);
            sketch.merge(sketch(14, next));
            n += next.length;
            assertThat(write(sketch)).isEqualTo(write(sketch(14, Arrays.copyOf(hashes, n))));
        }
        assertThat(n).isGreaterThan(1000);
    }

    private void assertMerge(final int precision,
                             final long[] left,
                             final long[] right,
                             final byte[] expected) {
        final HyperLogLogPlusPlus sketch = sketch(precision, left);
        final HyperLogLogPlusPlus other = sketch(precision, right);
        final byte[] otherBefore = write(other);

        sketch.merge(other);
        assertThat(write(sketch)).isEqualTo(expected);

        // Changing one must not change the other.
        assertThat(write(other)).isEqualTo(otherBefore);
        for (final long hash : new SplittableRandom(1).longs(1000).toArray()) {
            sketch.addHash(hash);
        }
        assertThat(write(other)).isEqualTo(otherBefore);
    }

    @ParameterizedTest
    @CsvSource({"10,5000,20", "14,20000,100", "14,200000,30", "16,60000,50"})
    void testGroupingOfMergesMakesNoDifference(final int precision, final int n, final int partCount) {
        final long[] hashes = hashes(n);
        final byte[] expected = write(sketch(precision, hashes));

        // Merge one at a time.
        final HyperLogLogPlusPlus[] parts = parts(precision, hashes, partCount);
        final HyperLogLogPlusPlus chain = new HyperLogLogPlusPlus(precision);
        for (final HyperLogLogPlusPlus part : parts) {
            chain.merge(part);
        }
        assertThat(write(chain)).isEqualTo(expected);

        // Merge in pairs, then pairs of pairs.
        for (int step = 1; step < partCount; step *= 2) {
            for (int i = 0; i + step < partCount; i += 2 * step) {
                parts[i].merge(parts[i + step]);
            }
        }
        assertThat(write(parts[0])).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 100_000})
    void testMergeWithSelf(final int n) {
        final HyperLogLogPlusPlus sketch = sketch(14, hashes(n));
        final byte[] before = write(sketch);
        sketch.merge(sketch);
        assertThat(write(sketch)).isEqualTo(before);
    }

    @Test
    void testMergeOfDifferentPrecisions() {
        final HyperLogLogPlusPlus sketch = new HyperLogLogPlusPlus(14);
        assertThatThrownBy(() -> sketch.merge(new HyperLogLogPlusPlus(12)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unable to merge a sketch of precision 12 into one of precision 14");
    }

    @Test
    void testMergeOfNull() {
        assertThatThrownBy(() -> new HyperLogLogPlusPlus(14).merge(null))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @CsvSource({"10,768", "12,3072", "14,12288", "16,49152"})
    void testTurnsDenseOnceSmaller(final int precision, final int denseByteCount) {
        final int headerSize = 3 + Integer.BYTES;
        final HyperLogLogPlusPlus sketch = new HyperLogLogPlusPlus(precision);
        final SplittableRandom random = new SplittableRandom(SEED);
        int lastSparseSize = 0;
        while (sketch.isSparse()) {
            lastSparseSize = write(sketch).length - headerSize;
            assertThat(lastSparseSize).isLessThanOrEqualTo(denseByteCount);
            sketch.addHash(random.nextLong());
        }
        // One more entry can add a few bytes at most.
        assertThat(lastSparseSize).isGreaterThan(denseByteCount - 8);
        assertThat(write(sketch)).hasSize(headerSize + denseByteCount);
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 11, 12, 13, 14, 15, 16})
    void testRoundTrip(final int precision) {
        for (final int n : new int[]{0, 1, 2, 200, 1_000, 5_000, 20_000, 100_000}) {
            assertRoundTrip(sketch(precision, hashes(n)));
        }
    }

    // Rows are stored in this form, so a failure here means the format version must be changed.
    @Test
    void testSerialisedForm() {
        assertThat(HexFormat.of().formatHex(write(new HyperLogLogPlusPlus(14))))
                .isEqualTo("01" + "0e" + "00" + "00000000");

        final HyperLogLogPlusPlus sparse = new HyperLogLogPlusPlus(14);
        // Index 0, with no 1 bit in the tail, so a rank of 40.
        sparse.addHash(0);
        // Index 1, which gives the rank.
        sparse.addHash(1L << 39);
        // Index 2^24, with the first 1 bit of the tail set, so a rank of 1.
        sparse.addHash((1L << 63) | (1L << 38));
        assertThat(HexFormat.of().formatHex(write(sparse)))
                .isEqualTo("01" + "0e" + "00" + "08000000"
                           + "01" + "28"
                           + "02"
                           + "ffffff0f" + "01");

        final byte[] dense = write(sketch(10, hashes(10_000)));
        assertThat(dense).startsWith(1, 10, 1, 0, 3, 0, 0);
        assertThat(Arrays.hashCode(dense)).isEqualTo(DENSE_HASH);
    }

    @ParameterizedTest
    @CsvSource({
            // Header
            "00 0e 00 00000000,Unknown sketch format version 0",
            "02 0e 00 00000000,Unknown sketch format version 2",
            "01 09 00 00000000,Precision must be from 10 to 16 but was 9",
            "01 11 00 00000000,Precision must be from 10 to 16 but was 17",
            "01 ff 00 00000000,Precision must be from 10 to 16 but was -1",
            "01 0e 02 00000000,unknown type 2",
            // Sparse, where the index is cut short, too long, too big, or repeated
            "01 0e 00 01000000 80,bad sparse index",
            "01 0e 00 05000000 8080808001,bad sparse index",
            "01 0e 00 05000000 8180802001,bad sparse index",
            "01 0e 00 02000000 0200,bad sparse index",
            // Sparse, where the rank is missing, 0, too big, not wanted, or wanted
            "01 0e 00 01000000 01,missing sparse rank",
            "01 0e 00 02000000 0100,bad sparse rank",
            "01 0e 00 02000000 0129,bad sparse rank",
            "01 0e 00 02000000 01ff,bad sparse rank",
            "01 0e 00 02000000 0301,sparse rank does not fit its index",
            "01 0e 00 01000000 00,sparse rank does not fit its index",
            // Sparse, where the index takes more bytes than it needs
            "01 0e 00 02000000 8200,unexpected sparse length",
            // Dense
            "01 0e 01 00000000,unexpected dense length",
            "01 0e 01 01000000 00,unexpected dense length"})
    void testReadInvalid(final String hex, final String message) {
        final byte[] bytes = HexFormat.of().parseHex(hex.replace(" ", ""));
        assertThatThrownBy(() -> read(bytes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(message);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Cut short in the header, the length, the sparse form and the dense form
            "",
            "01",
            "01 0e",
            "01 0e 00",
            "01 0e 00 0100",
            "01 0e 00 02000000 02",
            "01 0e 01 00300000 00",
            // Negative length
            "01 0e 00 ffffffff"})
    void testReadCutShort(final String hex) {
        final byte[] bytes = HexFormat.of().parseHex(hex.replace(" ", ""));
        assertThatThrownBy(() -> read(bytes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid sketch: unable to read it")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void testReadSparseLargerThanDense() {
        final byte[] bytes = new byte[7 + 769];
        // Precision 10, sparse, and a length of 769.
        System.arraycopy(new byte[]{1, 10, 0, 1, 3, 0, 0}, 0, bytes, 0, 7);
        assertThatThrownBy(() -> read(bytes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sparse form is larger than the dense form");
    }

    @Test
    void testReadBadRegister() {
        final byte[] bytes = write(sketch(10, hashes(10_000)));
        // The highest rank at precision 10 is 55, so 56 is too high.
        bytes[7] = (byte) ((bytes[7] & 0xC0) | 55);
        assertThat(read(bytes).isSparse()).isFalse();
        bytes[7] = (byte) ((bytes[7] & 0xC0) | 56);
        assertThatThrownBy(() -> read(bytes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bad register");
    }

    private static Form form(final HyperLogLogPlusPlus sketch) {
        return sketch.isSparse()
                ? Form.SPARSE
                : Form.DENSE;
    }

    private static double standardError(final int precision) {
        return 1.04 / Math.sqrt(1 << precision);
    }

    private static double relativeError(final HyperLogLogPlusPlus sketch, final long n) {
        return (double) (sketch.estimate() - n) / n;
    }

    // SplittableRandom never repeats a long within one stream, so these are all distinct.
    private static long[] hashes(final int n) {
        return new SplittableRandom(SEED).longs(n).toArray();
    }

    private static HyperLogLogPlusPlus sketch(final int precision, final long[] hashes) {
        final HyperLogLogPlusPlus sketch = new HyperLogLogPlusPlus(precision);
        for (final long hash : hashes) {
            sketch.addHash(hash);
        }
        return sketch;
    }

    private static HyperLogLogPlusPlus[] parts(final int precision, final long[] hashes, final int partCount) {
        final HyperLogLogPlusPlus[] parts = new HyperLogLogPlusPlus[partCount];
        for (int i = 0; i < partCount; i++) {
            parts[i] = new HyperLogLogPlusPlus(precision);
        }
        for (int i = 0; i < hashes.length; i++) {
            parts[i % partCount].addHash(hashes[i]);
        }
        return parts;
    }

    private static void assertRoundTrip(final HyperLogLogPlusPlus sketch) {
        final byte[] bytes = write(sketch);
        final HyperLogLogPlusPlus copy = read(bytes);
        assertThat(write(copy)).isEqualTo(bytes);
        assertThat(copy.estimate()).isEqualTo(sketch.estimate());
        assertThat(copy.getPrecision()).isEqualTo(sketch.getPrecision());
        assertThat(copy.isSparse()).isEqualTo(sketch.isSparse());

        // Make sure the copy can still be added to.
        final HyperLogLogPlusPlus original = sketch(sketch.getPrecision(), new long[0]);
        original.merge(sketch);
        for (final long hash : new SplittableRandom(2).longs(1000).toArray()) {
            original.addHash(hash);
            copy.addHash(hash);
        }
        assertThat(write(copy)).isEqualTo(write(original));
    }

    private static byte[] write(final HyperLogLogPlusPlus sketch) {
        final Output output = new Output(16, -1);
        sketch.write(new KryoDataWriter(output));
        return output.toBytes();
    }

    private static HyperLogLogPlusPlus read(final byte[] bytes) {
        return HyperLogLogPlusPlus.read(new KryoDataReader(new Input(bytes)));
    }


    // --------------------------------------------------------------------------------


    private enum Form {
        SPARSE,
        DENSE
    }
}
