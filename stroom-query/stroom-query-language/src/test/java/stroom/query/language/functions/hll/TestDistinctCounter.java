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

import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ref.KryoDataReader;
import stroom.query.language.functions.ref.KryoDataWriter;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDistinctCounter {

    // Compares random adds, merges, reads and writes with a set and a sketch.
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 7, 64, 1_000})
    void testSameAsSetOrSketch(final int limit) {
        final Random random = new Random(limit);
        for (int run = 0; run < 100; run++) {
            DistinctCounter counter = new DistinctCounter(limit);
            final Set<Long> expected = new HashSet<>();
            for (int step = 0; step < 20; step++) {
                final DistinctCounter other = new DistinctCounter(limit);
                final int count = random.nextInt(limit / 4 + 2);
                for (int i = 0; i < count; i++) {
                    final long hash = random.nextInt(2 * limit) * 0x9E3779B97F4A7C15L;
                    expected.add(hash);
                    if (step % 2 == 0) {
                        counter.add(hash);
                    } else {
                        other.add(hash);
                    }
                }
                if (step % 4 == 1) {
                    counter.merge(other);
                } else {
                    other.merge(counter);
                    counter = other;
                }
                assertCount(counter, expected, limit);
                counter = read(write(counter), limit);
                assertCount(counter, expected, limit);
            }
        }
    }

    private static void assertCount(final DistinctCounter counter, final Set<Long> expected, final int limit) {
        if (expected.size() <= limit) {
            assertThat(counter.count()).isEqualTo(expected.size());
        } else {
            final HyperLogLogPlusPlus sketch = new HyperLogLogPlusPlus(14);
            expected.forEach(sketch::addHash);
            assertThat(counter.count()).isEqualTo(sketch.estimate());

            // It is no different from one that was always a sketch.
            final Output output = new Output(64, -1);
            final KryoDataWriter writer = new KryoDataWriter(output);
            writer.writeVarInt(0);
            sketch.write(writer);
            assertThat(write(counter)).isEqualTo(output.toBytes());
        }
    }

    @Test
    void testExtremes() {
        final DistinctCounter counter = new DistinctCounter(10);
        for (final long hash : new long[]{Long.MAX_VALUE, 0, Long.MIN_VALUE, -1, 1, Long.MAX_VALUE, Long.MIN_VALUE}) {
            counter.add(hash);
        }
        assertThat(counter.count()).isEqualTo(5);
        final DistinctCounter copy = read(write(counter), 10);
        copy.merge(counter);
        assertThat(copy.count()).isEqualTo(5);
        assertThat(write(copy)).isEqualTo(write(counter));
    }

    // As when the rows of two nodes are both over the limit.
    @Test
    void testMergeBothOverLimit() {
        final DistinctCounter counter = new DistinctCounter(10);
        final DistinctCounter other = new DistinctCounter(10);
        final DistinctCounter expected = new DistinctCounter(10);
        addAll(counter, 0, 20_000);
        addAll(other, 10_000, 40_000);
        addAll(expected, 0, 40_000);
        counter.merge(other);
        assertThat(counter.count()).isEqualTo(expected.count());
        assertThat(write(counter)).isEqualTo(write(expected));
    }

    @Test
    void testMergeDoesNotChangeTheOther() {
        for (final int count1 : new int[]{0, 1, 5, 20}) {
            for (final int count2 : new int[]{0, 1, 5, 20}) {
                final DistinctCounter counter = new DistinctCounter(8);
                final DistinctCounter other = new DistinctCounter(8);
                addAll(counter, 100, 100 + count1);
                addAll(other, 0, count2);
                final byte[] before = write(other);
                counter.merge(other);
                addAll(counter, 1_000, 1_050);
                assertThat(write(other)).isEqualTo(before);
            }
        }
    }

    // Stored rows and the rows sent between nodes must not change.
    @Test
    void testStoredForm() {
        final DistinctCounter counter = new DistinctCounter(2);
        assertThat(write(counter)).containsExactly(1);
        counter.add(2);
        counter.add(1);
        assertThat(write(counter)).containsExactly(3, 1, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0);
        counter.add(3);
        assertThat(write(counter)).startsWith(0);
    }

    // What follows must still be read from the right place.
    @ParameterizedTest
    @ValueSource(ints = {1, 9, 10, 11})
    void testReadWithAnotherLimit(final int limit) {
        for (final int count : new int[]{10, 11, 20_000}) {
            final DistinctCounter counter = new DistinctCounter(10);
            addAll(counter, 0, count);
            final Output output = new Output(64, -1);
            final KryoDataWriter writer = new KryoDataWriter(output);
            counter.write(writer);
            writer.writeInt(123);
            final KryoDataReader reader = new KryoDataReader(new Input(output.toBytes()));
            assertThat(DistinctCounter.read(reader, limit).count()).isEqualTo(counter.count());
            assertThat(reader.readInt()).isEqualTo(123);
        }
    }

    @Test
    void testBadData() {
        assertThatThrownBy(() -> read(new byte[]{3, 2, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0}, 10))
                .hasMessageContaining("not in order");
        assertThatThrownBy(() -> read(new byte[]{3, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0}, 10))
                .hasMessageContaining("not in order");
        assertThatThrownBy(() -> read(new byte[]{-1, -1, -1, -1, 15}, 10))
                .hasMessageContaining("Invalid number of hashes -1");
        // Cut short, including with far more to come than there could be.
        assertThatThrownBy(() -> read(new byte[]{3, 0, 0, 0}, 10)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> read(new byte[]{-1, -1, -1, -1, 7, 1, 0, 0, 0, 0, 0, 0, 0}, 10))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> read(new byte[]{0, 5}, 10)).isInstanceOf(RuntimeException.class);
    }

    // Counts must only go up, or be an estimate, while another thread adds to the counter.
    @Test
    void testCountWhileAdding() throws InterruptedException {
        final DistinctCounter counter = new DistinctCounter(1_000);
        final AtomicBoolean done = new AtomicBoolean();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Thread thread = new Thread(() -> {
            try {
                long highest = 0;
                while (!done.get()) {
                    final long count = counter.count();
                    assertThat(count).isBetween((long) (highest * 0.9), 33_000L);
                    highest = Math.max(highest, count);
                }
            } catch (final Throwable e) {
                failure.set(e);
            }
        });
        thread.start();
        try {
            addAll(counter, 0, 30_000);
        } finally {
            done.set(true);
            thread.join();
        }
        assertThat(failure.get()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void testBadLimit(final int limit) {
        assertThatThrownBy(() -> new DistinctCounter(limit)).isInstanceOf(IllegalArgumentException.class);
    }

    private static void addAll(final DistinctCounter counter, final int from, final int to) {
        for (int i = from; i < to; i++) {
            counter.add(UniqueValHasher.hash(ValLong.create(i)));
        }
    }

    private static byte[] write(final DistinctCounter counter) {
        final Output output = new Output(64, -1);
        counter.write(new KryoDataWriter(output));
        return output.toBytes();
    }

    private static DistinctCounter read(final byte[] bytes, final int limit) {
        return DistinctCounter.read(new KryoDataReader(new Input(bytes)), limit);
    }
}
