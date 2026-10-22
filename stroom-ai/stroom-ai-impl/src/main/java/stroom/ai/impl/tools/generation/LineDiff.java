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

package stroom.ai.impl.tools.generation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// The lines one list has that another hasn't, and the reverse, as Python's `difflib.unified_diff(a, b, n=0)` gives
/// them (its `SequenceMatcher`, with its heuristic that treats the lines more than 1% of a long list are as
/// unmatchable): for each change in order, the lines taken out (`-`) then those put in (`+`).
final class LineDiff {

    private static final int AUTOJUNK_LENGTH = 200;

    private final List<String> a;
    private final List<String> b;
    private final Map<String, List<Integer>> b2j = new HashMap<>();

    private LineDiff(final List<String> a, final List<String> b) {
        this.a = a;
        this.b = b;
        for (int j = 0; j < b.size(); j++) {
            b2j.computeIfAbsent(b.get(j), ignored -> new ArrayList<>()).add(j);
        }
        if (b.size() >= AUTOJUNK_LENGTH) {
            final int test = b.size() / 100 + 1;
            b2j.values().removeIf(indices -> indices.size() > test);
        }
    }

    /// @param a The lines before.
    /// @param b The lines after.
    /// @return The lines changed, each with `-` (in a only) or `+` (in b only) before it.
    static List<String> changes(final List<String> a, final List<String> b) {
        final LineDiff diff = new LineDiff(a, b);
        final List<String> out = new ArrayList<>();
        int i = 0;
        int j = 0;
        for (final int[] block : diff.matchingBlocks()) {
            final int ai = block[0];
            final int bj = block[1];
            final int size = block[2];
            for (int k = i; k < ai; k++) {
                out.add("-" + a.get(k));
            }
            for (int k = j; k < bj; k++) {
                out.add("+" + b.get(k));
            }
            i = ai + size;
            j = bj + size;
        }
        return out;
    }

    private int[] longestMatch(final int alo, final int ahi, final int blo, final int bhi) {
        int bestI = alo;
        int bestJ = blo;
        int bestSize = 0;
        Map<Integer, Integer> lengths = new HashMap<>();
        for (int i = alo; i < ahi; i++) {
            final Map<Integer, Integer> next = new HashMap<>();
            for (final int j : b2j.getOrDefault(a.get(i), List.of())) {
                if (j < blo) {
                    continue;
                }
                if (j >= bhi) {
                    break;
                }
                final int k = lengths.getOrDefault(j - 1, 0) + 1;
                next.put(j, k);
                if (k > bestSize) {
                    bestI = i - k + 1;
                    bestJ = j - k + 1;
                    bestSize = k;
                }
            }
            lengths = next;
        }
        // Extended by equal lines either side, the popular ones too (they aren't junk, only unindexed).
        while (bestI > alo && bestJ > blo && a.get(bestI - 1).equals(b.get(bestJ - 1))) {
            bestI--;
            bestJ--;
            bestSize++;
        }
        while (bestI + bestSize < ahi && bestJ + bestSize < bhi && a.get(bestI + bestSize).equals(b.get(bestJ
                                                                                                         + bestSize))) {
            bestSize++;
        }
        return new int[]{bestI, bestJ, bestSize};
    }

    private List<int[]> matchingBlocks() {
        final Deque<int[]> queue = new ArrayDeque<>();
        queue.push(new int[]{0, a.size(), 0, b.size()});
        final List<int[]> blocks = new ArrayList<>();
        while (!queue.isEmpty()) {
            final int[] range = queue.pop();
            final int[] match = longestMatch(range[0], range[1], range[2], range[3]);
            final int i = match[0];
            final int j = match[1];
            final int k = match[2];
            if (k > 0) {
                blocks.add(match);
                if (range[0] < i && range[2] < j) {
                    queue.push(new int[]{range[0], i, range[2], j});
                }
                if (i + k < range[1] && j + k < range[3]) {
                    queue.push(new int[]{i + k, range[1], j + k, range[3]});
                }
            }
        }
        blocks.sort(Comparator.<int[]>comparingInt(block -> block[0]).thenComparingInt(block -> block[1])
                .thenComparingInt(block -> block[2]));
        final List<int[]> joined = new ArrayList<>();
        int i1 = 0;
        int j1 = 0;
        int k1 = 0;
        for (final int[] block : blocks) {
            if (i1 + k1 == block[0] && j1 + k1 == block[1]) {
                k1 += block[2];
            } else {
                if (k1 > 0) {
                    joined.add(new int[]{i1, j1, k1});
                }
                i1 = block[0];
                j1 = block[1];
                k1 = block[2];
            }
        }
        if (k1 > 0) {
            joined.add(new int[]{i1, j1, k1});
        }
        joined.add(new int[]{a.size(), b.size(), 0});
        return joined;
    }
}
