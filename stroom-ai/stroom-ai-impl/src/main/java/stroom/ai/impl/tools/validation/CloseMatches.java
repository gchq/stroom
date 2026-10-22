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

package stroom.ai.impl.tools.validation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/// The names most like one given, as Python's `difflib.get_close_matches` finds them: the Stroom MCP server's
/// "did you mean" suggestions (a stroom: function, an event-logging element) are worded around them.
public final class CloseMatches {

    /// difflib's default number of matches.
    public static final int DEFAULT_COUNT = 3;
    /// difflib's default cutoff.
    public static final double DEFAULT_CUTOFF = 0.6;

    private CloseMatches() {
        // Static only.
    }

    /// @param word          The word given.
    /// @param possibilities The words there are.
    /// @param count         The most to give.
    /// @param cutoff        The least [#ratio(String, String)] a word must have to be given, from 0 to 1.
    /// @return The words most like it, the most alike first (of two alike, the one sorting last first, as difflib
    /// gives them).
    public static List<String> closeMatches(final String word,
                                            final Collection<String> possibilities,
                                            final int count,
                                            final double cutoff) {
        final List<Scored> scored = new ArrayList<>();
        for (final String possibility : possibilities) {
            final double score = ratio(possibility, word);
            if (score >= cutoff) {
                scored.add(new Scored(score, possibility));
            }
        }
        return scored.stream()
                .sorted(Comparator.comparingDouble(Scored::score).thenComparing(Scored::word).reversed())
                .limit(count)
                .map(Scored::word)
                .toList();
    }

    /// How alike two strings are, as Python's `difflib.SequenceMatcher.ratio()` measures it (without its junk
    /// heuristic, which only applies to strings of 200 or more characters): twice the characters in matching
    /// blocks over the total length.
    ///
    /// @param a A string.
    /// @param b Another.
    /// @return From 0 (nothing alike) to 1 (the same).
    public static double ratio(final String a, final String b) {
        final int total = a.length() + b.length();
        return total == 0
                ? 1.0
                : 2.0 * matches(a, 0, a.length(), b, 0, b.length()) / total;
    }

    // The characters in the matching blocks of a[alo:ahi] and b[blo:bhi]: the longest match (earliest in a, then
    // in b), and those either side of it.
    private static int matches(final String a, final int alo, final int ahi,
                               final String b, final int blo, final int bhi) {
        int bestI = alo;
        int bestJ = blo;
        int bestSize = 0;
        int[] previous = new int[bhi - blo + 1];
        for (int i = alo; i < ahi; i++) {
            final int[] current = new int[bhi - blo + 1];
            for (int j = blo; j < bhi; j++) {
                if (a.charAt(i) == b.charAt(j)) {
                    final int k = previous[j - blo] + 1;
                    current[j - blo + 1] = k;
                    if (k > bestSize) {
                        bestI = i - k + 1;
                        bestJ = j - k + 1;
                        bestSize = k;
                    }
                }
            }
            previous = current;
        }
        if (bestSize == 0) {
            return 0;
        }
        return bestSize
               + matches(a, alo, bestI, b, blo, bestJ)
               + matches(a, bestI + bestSize, ahi, b, bestJ + bestSize, bhi);
    }


    // --------------------------------------------------------------------------------


    private record Scored(double score, String word) {

    }
}
