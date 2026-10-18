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

import java.util.List;

/// The Myers O(ND) difference algorithm, in its linear space form (Myers 1986, section 4b), over
/// sequences of ints. Callers map their elements (lines or characters) to ints so that equal elements
/// have equal ints.
///
/// The middle snake is found by running the forward and reverse searches together until they overlap,
/// then each half is diffed in turn. Common prefixes and suffixes are stripped first, which is cheap
/// and makes the usual case of a few small edits fast.
///
/// This is plain Java so that it can be compiled by GWT and used in the browser.
final class MyersDiff {

    private MyersDiff() {
        // Utility class.
    }

    /// Diffs the whole of `a` against the whole of `b`.
    ///
    /// @param a   The old sequence.
    /// @param b   The new sequence.
    /// @param out The list to add one operation to for each element consumed, in order. Each is
    ///            [DiffType#EQUAL] (consumes one of each), [DiffType#DELETE] (consumes one of `a`) or
    ///            [DiffType#INSERT] (consumes one of `b`).
    static void diff(final int[] a, final int[] b, final List<DiffType> out) {
        diff(a, 0, a.length, b, 0, b.length, out);
    }

    private static void diff(final int[] a,
                             final int aStart,
                             final int aEnd,
                             final int[] b,
                             final int bStart,
                             final int bEnd,
                             final List<DiffType> out) {
        int prefix = 0;
        while (aStart + prefix < aEnd
               && bStart + prefix < bEnd
               && a[aStart + prefix] == b[bStart + prefix]) {
            prefix++;
        }
        int suffix = 0;
        while (aEnd - suffix > aStart + prefix
               && bEnd - suffix > bStart + prefix
               && a[aEnd - 1 - suffix] == b[bEnd - 1 - suffix]) {
            suffix++;
        }

        add(out, DiffType.EQUAL, prefix);

        final int aFrom = aStart + prefix;
        final int aTo = aEnd - suffix;
        final int bFrom = bStart + prefix;
        final int bTo = bEnd - suffix;
        if (aFrom == aTo) {
            add(out, DiffType.INSERT, bTo - bFrom);
        } else if (bFrom == bTo) {
            add(out, DiffType.DELETE, aTo - aFrom);
        } else {
            bisect(a, aFrom, aTo, b, bFrom, bTo, out);
        }

        add(out, DiffType.EQUAL, suffix);
    }

    // Finds the middle snake of the (non-empty) sub-sequences and diffs either side of it.
    // Both sub-sequences are non-empty and differ in their first and last elements.
    private static void bisect(final int[] a,
                               final int aStart,
                               final int aEnd,
                               final int[] b,
                               final int bStart,
                               final int bEnd,
                               final List<DiffType> out) {
        final int n = aEnd - aStart;
        final int m = bEnd - bStart;
        final int maxD = (n + m + 1) / 2;
        final int offset = maxD;
        final int length = 2 * maxD + 2;
        final int[] forward = new int[length];
        final int[] reverse = new int[length];
        for (int i = 0; i < length; i++) {
            forward[i] = -1;
            reverse[i] = -1;
        }
        forward[offset + 1] = 0;
        reverse[offset + 1] = 0;
        final int delta = n - m;
        // If the total number of elements is odd then the front path will collide with the reverse path.
        final boolean front = delta % 2 != 0;
        // Offsets for the start and end of the k loops, preventing mapping of space beyond the grid.
        int k1Start = 0;
        int k1End = 0;
        int k2Start = 0;
        int k2End = 0;

        for (int d = 0; d < maxD; d++) {
            // Walk the forward path one step.
            for (int k1 = -d + k1Start; k1 <= d - k1End; k1 += 2) {
                final int k1Offset = offset + k1;
                int x1;
                if (k1 == -d || (k1 != d && forward[k1Offset - 1] < forward[k1Offset + 1])) {
                    x1 = forward[k1Offset + 1];
                } else {
                    x1 = forward[k1Offset - 1] + 1;
                }
                int y1 = x1 - k1;
                while (x1 < n && y1 < m && a[aStart + x1] == b[bStart + y1]) {
                    x1++;
                    y1++;
                }
                forward[k1Offset] = x1;
                if (x1 > n) {
                    // Ran off the right of the grid.
                    k1End += 2;
                } else if (y1 > m) {
                    // Ran off the bottom of the grid.
                    k1Start += 2;
                } else if (front) {
                    final int k2Offset = offset + delta - k1;
                    if (k2Offset >= 0 && k2Offset < length && reverse[k2Offset] != -1) {
                        // Mirror x2 onto the top-left coordinate system.
                        final int x2 = n - reverse[k2Offset];
                        if (x1 >= x2) {
                            split(a, aStart, aEnd, b, bStart, bEnd, x1, y1, out);
                            return;
                        }
                    }
                }
            }

            // Walk the reverse path one step.
            for (int k2 = -d + k2Start; k2 <= d - k2End; k2 += 2) {
                final int k2Offset = offset + k2;
                int x2;
                if (k2 == -d || (k2 != d && reverse[k2Offset - 1] < reverse[k2Offset + 1])) {
                    x2 = reverse[k2Offset + 1];
                } else {
                    x2 = reverse[k2Offset - 1] + 1;
                }
                int y2 = x2 - k2;
                while (x2 < n && y2 < m && a[aEnd - x2 - 1] == b[bEnd - y2 - 1]) {
                    x2++;
                    y2++;
                }
                reverse[k2Offset] = x2;
                if (x2 > n) {
                    k2End += 2;
                } else if (y2 > m) {
                    k2Start += 2;
                } else if (!front) {
                    final int k1Offset = offset + delta - k2;
                    if (k1Offset >= 0 && k1Offset < length && forward[k1Offset] != -1) {
                        final int x1 = forward[k1Offset];
                        final int y1 = offset + x1 - k1Offset;
                        // Mirror x2 onto the top-left coordinate system.
                        if (x1 >= n - x2) {
                            split(a, aStart, aEnd, b, bStart, bEnd, x1, y1, out);
                            return;
                        }
                    }
                }
            }
        }

        // No common elements at all.
        add(out, DiffType.DELETE, n);
        add(out, DiffType.INSERT, m);
    }

    private static void split(final int[] a,
                              final int aStart,
                              final int aEnd,
                              final int[] b,
                              final int bStart,
                              final int bEnd,
                              final int x,
                              final int y,
                              final List<DiffType> out) {
        diff(a, aStart, aStart + x, b, bStart, bStart + y, out);
        diff(a, aStart + x, aEnd, b, bStart + y, bEnd, out);
    }

    private static void add(final List<DiffType> out, final DiffType type, final int count) {
        for (int i = 0; i < count; i++) {
            out.add(type);
        }
    }
}
