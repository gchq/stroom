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

package stroom.mcp.impl.e2e;

import java.util.ArrayList;
import java.util.List;

/// Python's `random.Random(seed)` for a small non-negative int seed: the same Mersenne Twister, seeded and drawn
/// from as CPython draws, so the evaluation set's generated samples ([EvalSamples]) are the same bytes every run and
/// match the SHA-256 the set pins for each (`generated-sha256.json`).
final class SeededRandom {

    private static final int N = 624;
    private static final int M = 397;
    private static final int MATRIX_A = 0x9908b0df;
    private static final int UPPER_MASK = 0x80000000;
    private static final int LOWER_MASK = 0x7fffffff;

    private final int[] mt = new int[N];
    private int mti;

    /// @param seed The seed, as `random.Random(seed)` takes it (0 to 2^32 - 1).
    SeededRandom(final long seed) {
        // CPython's random_seed: the seed's 32-bit words, least significant first (one word for these seeds).
        initByArray(new int[]{(int) seed});
    }

    /// @param a The least.
    /// @param b The most.
    /// @return `randint(a, b)`.
    int randint(final int a, final int b) {
        return a + randBelow(b - a + 1);
    }

    /// @param items The items.
    /// @param <T>   Their type.
    /// @return `choice(items)`.
    <T> T choice(final List<T> items) {
        return items.get(randBelow(items.size()));
    }

    /// @return `random()`: in [0, 1), from 53 random bits.
    double random() {
        final long a = Integer.toUnsignedLong(next()) >>> 5;
        final long b = Integer.toUnsignedLong(next()) >>> 6;
        return (a * 67108864.0 + b) * (1.0 / 9007199254740992.0);
    }

    /// `sample(population, k)` for a population no larger than CPython's set size (21 for k up to 5): the pool
    /// method.
    ///
    /// @param population The population.
    /// @param k          How many to choose.
    /// @param <T>        Their type.
    /// @return The sample, in the order chosen.
    <T> List<T> sample(final List<T> population, final int k) {
        final int n = population.size();
        if (k > 5 || n > 21) {
            throw new IllegalArgumentException("Only small samples are as CPython draws them");
        }
        final List<T> pool = new ArrayList<>(population);
        final List<T> result = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            final int j = randBelow(n - i);
            result.add(pool.get(j));
            // The last of the pool takes the chosen one's place.
            pool.set(j, pool.get(n - i - 1));
        }
        return result;
    }

    // CPython's _randbelow_with_getrandbits: as many bits as n has, drawn again until below it.
    private int randBelow(final int n) {
        final int k = 32 - Integer.numberOfLeadingZeros(n);
        long r = getRandBits(k);
        while (r >= n) {
            r = getRandBits(k);
        }
        return (int) r;
    }

    private long getRandBits(final int k) {
        return Integer.toUnsignedLong(next()) >>> (32 - k);
    }

    private void initGenrand(final int seed) {
        mt[0] = seed;
        for (mti = 1; mti < N; mti++) {
            mt[mti] = 1812433253 * (mt[mti - 1] ^ (mt[mti - 1] >>> 30)) + mti;
        }
    }

    private void initByArray(final int[] key) {
        initGenrand(19650218);
        int i = 1;
        int j = 0;
        for (int k = Math.max(N, key.length); k > 0; k--) {
            mt[i] = (mt[i] ^ ((mt[i - 1] ^ (mt[i - 1] >>> 30)) * 1664525)) + key[j] + j;
            i++;
            j++;
            if (i >= N) {
                mt[0] = mt[N - 1];
                i = 1;
            }
            if (j >= key.length) {
                j = 0;
            }
        }
        for (int k = N - 1; k > 0; k--) {
            mt[i] = (mt[i] ^ ((mt[i - 1] ^ (mt[i - 1] >>> 30)) * 1566083941)) - i;
            i++;
            if (i >= N) {
                mt[0] = mt[N - 1];
                i = 1;
            }
        }
        mt[0] = 0x80000000;
    }

    private int next() {
        if (mti >= N) {
            int kk;
            for (kk = 0; kk < N - M; kk++) {
                final int y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + M] ^ (y >>> 1) ^ ((y & 1) != 0
                        ? MATRIX_A
                        : 0);
            }
            for (; kk < N - 1; kk++) {
                final int y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + (M - N)] ^ (y >>> 1) ^ ((y & 1) != 0
                        ? MATRIX_A
                        : 0);
            }
            final int y = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
            mt[N - 1] = mt[M - 1] ^ (y >>> 1) ^ ((y & 1) != 0
                    ? MATRIX_A
                    : 0);
            mti = 0;
        }
        int y = mt[mti++];
        y ^= y >>> 11;
        y ^= (y << 7) & 0x9d2c5680;
        y ^= (y << 15) & 0xefc60000;
        y ^= y >>> 18;
        return y;
    }
}
