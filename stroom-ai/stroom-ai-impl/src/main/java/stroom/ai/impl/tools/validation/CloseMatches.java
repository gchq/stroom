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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// The names most like one given, as Python's `difflib.get_close_matches` finds them: the Stroom MCP server's
/// "did you mean" suggestions (a stroom: function, an event-logging element) are worded around them.
public final class CloseMatches {

    /// difflib's default number of matches.
    public static final int DEFAULT_COUNT = 3;
    /// difflib's default cutoff.
    public static final double DEFAULT_CUTOFF = 0.6;
    // The length from which SequenceMatcher's autojunk takes b's popular characters for junk.
    private static final int AUTOJUNK_LENGTH = 200;

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

    /// How alike two strings are, as Python's `difflib.SequenceMatcher(None, a, b).ratio()` measures it: twice the
    /// characters in matching blocks over the total length. As difflib's autojunk has it, when b is 200 or more
    /// characters long, a character in more than 1% of it (and one more) starts no match, though a match may grow
    /// over it.
    ///
    /// @param a A string.
    /// @param b Another.
    /// @return From 0 (nothing alike) to 1 (the same).
    public static double ratio(final String a, final String b) {
        final int total = a.length() + b.length();
        return total == 0
                ? 1.0
                : 2.0 * matches(a, 0, a.length(), b, 0, b.length(), popular(b)) / total;
    }

    // The characters autojunk leaves out of b's index: none for a b shorter than 200, else those found more than
    // len(b) // 100 + 1 times.
    private static Set<Character> popular(final String b) {
        final Set<Character> popular = new HashSet<>();
        if (b.length() >= AUTOJUNK_LENGTH) {
            final Map<Character, Integer> counts = new HashMap<>();
            for (int j = 0; j < b.length(); j++) {
                counts.merge(b.charAt(j), 1, Integer::sum);
            }
            final int most = b.length() / 100 + 1;
            counts.forEach((c, count) -> {
                if (count > most) {
                    popular.add(c);
                }
            });
        }
        return popular;
    }

    // The characters in the matching blocks of a[alo:ahi] and b[blo:bhi]: the longest match (earliest in a, then
    // in b) of characters not popular, grown over equal characters either side as difflib's find_longest_match
    // grows it, and those either side of it.
    private static int matches(final String a, final int alo, final int ahi,
                               final String b, final int blo, final int bhi, final Set<Character> popular) {
        int bestI = alo;
        int bestJ = blo;
        int bestSize = 0;
        int[] previous = new int[bhi - blo + 1];
        for (int i = alo; i < ahi; i++) {
            final int[] current = new int[bhi - blo + 1];
            for (int j = blo; j < bhi; j++) {
                if (a.charAt(i) == b.charAt(j) && !popular.contains(b.charAt(j))) {
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
        while (bestI > alo && bestJ > blo && a.charAt(bestI - 1) == b.charAt(bestJ - 1)) {
            bestI--;
            bestJ--;
            bestSize++;
        }
        while (bestI + bestSize < ahi && bestJ + bestSize < bhi
               && a.charAt(bestI + bestSize) == b.charAt(bestJ + bestSize)) {
            bestSize++;
        }
        if (bestSize == 0) {
            return 0;
        }
        return bestSize
               + matches(a, alo, bestI, b, blo, bestJ, popular)
               + matches(a, bestI + bestSize, ahi, b, bestJ + bestSize, bhi, popular);
    }


    // --------------------------------------------------------------------------------


    private record Scored(double score, String word) {

    }
}
