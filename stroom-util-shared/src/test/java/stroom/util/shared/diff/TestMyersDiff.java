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

package stroom.util.shared.diff;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class TestMyersDiff {

    @Test
    void testEmpty() {
        final List<DiffType> ops = new ArrayList<>();
        MyersDiff.diff(new int[0], new int[0], ops);
        assertThat(ops).isEmpty();
    }

    @Test
    void testAllInserted() {
        final List<DiffType> ops = new ArrayList<>();
        MyersDiff.diff(new int[0], new int[]{1, 2}, ops);
        assertThat(ops).containsExactly(DiffType.INSERT, DiffType.INSERT);
    }

    @Test
    void testAllDeleted() {
        final List<DiffType> ops = new ArrayList<>();
        MyersDiff.diff(new int[]{1, 2}, new int[0], ops);
        assertThat(ops).containsExactly(DiffType.DELETE, DiffType.DELETE);
    }

    @Test
    void testNothingInCommon() {
        final List<DiffType> ops = new ArrayList<>();
        MyersDiff.diff(new int[]{1, 2}, new int[]{3}, ops);
        assertThat(ops).filteredOn(op -> op == DiffType.EQUAL).isEmpty();
        assertThat(ops).filteredOn(op -> op == DiffType.DELETE).hasSize(2);
        assertThat(ops).filteredOn(op -> op == DiffType.INSERT).hasSize(1);
    }

    @Test
    void testClassicExample() {
        // The example from Myers' paper: ABCABBA -> CBABAC has an edit distance of 5.
        final int[] a = {'A', 'B', 'C', 'A', 'B', 'B', 'A'};
        final int[] b = {'C', 'B', 'A', 'B', 'A', 'C'};
        final List<DiffType> ops = new ArrayList<>();
        MyersDiff.diff(a, b, ops);
        assertValidScript(a, b, ops);
        assertThat(editCount(ops)).isEqualTo(5);
    }

    @Test
    void testRandomAgainstLcs() {
        // The edit script must be valid and minimal: its edit count must equal
        // n + m - 2 * LCS, computed here by dynamic programming.
        final Random random = new Random(42);
        for (int run = 0; run < 2000; run++) {
            final int[] a = randomSequence(random, random.nextInt(25), 1 + random.nextInt(4));
            final int[] b = randomSequence(random, random.nextInt(25), 1 + random.nextInt(4));
            final List<DiffType> ops = new ArrayList<>();
            MyersDiff.diff(a, b, ops);
            assertValidScript(a, b, ops);
            assertThat(editCount(ops))
                    .as("run %s", run)
                    .isEqualTo(a.length + b.length - 2 * lcsLength(a, b));
        }
    }

    @Test
    void testLargeInputIsQuick() {
        // Linear space, so a large, very different input must not run out of memory.
        final Random random = new Random(7);
        final int[] a = randomSequence(random, 3000, 50);
        final int[] b = randomSequence(random, 3000, 50);
        final List<DiffType> ops = new ArrayList<>();
        MyersDiff.diff(a, b, ops);
        assertValidScript(a, b, ops);
    }

    private static int[] randomSequence(final Random random, final int length, final int alphabet) {
        final int[] result = new int[length];
        for (int i = 0; i < length; i++) {
            result[i] = random.nextInt(alphabet);
        }
        return result;
    }

    private static void assertValidScript(final int[] a, final int[] b, final List<DiffType> ops) {
        int i = 0;
        int j = 0;
        for (final DiffType op : ops) {
            if (op == DiffType.EQUAL) {
                assertThat(a[i]).isEqualTo(b[j]);
                i++;
                j++;
            } else if (op == DiffType.DELETE) {
                i++;
            } else {
                assertThat(op).isEqualTo(DiffType.INSERT);
                j++;
            }
        }
        assertThat(i).isEqualTo(a.length);
        assertThat(j).isEqualTo(b.length);
    }

    private static int editCount(final List<DiffType> ops) {
        int count = 0;
        for (final DiffType op : ops) {
            if (op != DiffType.EQUAL) {
                count++;
            }
        }
        return count;
    }

    private static int lcsLength(final int[] a, final int[] b) {
        final int[][] table = new int[a.length + 1][b.length + 1];
        for (int i = a.length - 1; i >= 0; i--) {
            for (int j = b.length - 1; j >= 0; j--) {
                if (a[i] == b[j]) {
                    table[i][j] = table[i + 1][j + 1] + 1;
                } else {
                    table[i][j] = Math.max(table[i + 1][j], table[i][j + 1]);
                }
            }
        }
        return table[0][0];
    }
}
