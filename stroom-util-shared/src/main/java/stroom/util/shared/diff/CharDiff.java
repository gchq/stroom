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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// The characters that differ between an old line and the new line that replaced it.
public final class CharDiff {

    // Changed ranges separated by this many equal characters or fewer are joined into one, so a
    // rewritten word shows as one change rather than a scatter of single letters.
    static final int MERGE_GAP = 2;
    // Above this combined length only the common prefix and suffix are removed, so a very long line
    // (e.g. un-formatted XML) can't make the character diff slow.
    static final int MAX_DIFF_LENGTH = 4000;

    private final List<CharRange> oldRanges;
    private final List<CharRange> newRanges;

    private CharDiff(final List<CharRange> oldRanges, final List<CharRange> newRanges) {
        this.oldRanges = Collections.unmodifiableList(oldRanges);
        this.newRanges = Collections.unmodifiableList(newRanges);
    }

    /// Finds the changed characters between two lines.
    ///
    /// @param oldLine The old line, not null.
    /// @param newLine The new line, not null.
    /// @return The changed ranges on each side. A side's list is empty if it has no changed characters
    /// (e.g. characters were only inserted).
    public static CharDiff of(final String oldLine, final String newLine) {
        final List<CharRange> oldRanges = new ArrayList<>();
        final List<CharRange> newRanges = new ArrayList<>();
        if (oldLine.equals(newLine)) {
            return new CharDiff(oldRanges, newRanges);
        }

        if (oldLine.length() + newLine.length() > MAX_DIFF_LENGTH) {
            int prefix = 0;
            while (prefix < oldLine.length()
                   && prefix < newLine.length()
                   && oldLine.charAt(prefix) == newLine.charAt(prefix)) {
                prefix++;
            }
            int suffix = 0;
            while (suffix < oldLine.length() - prefix
                   && suffix < newLine.length() - prefix
                   && oldLine.charAt(oldLine.length() - 1 - suffix) == newLine.charAt(newLine.length() - 1 - suffix)) {
                suffix++;
            }
            addIfNotEmpty(oldRanges, prefix, oldLine.length() - suffix);
            addIfNotEmpty(newRanges, prefix, newLine.length() - suffix);
            return new CharDiff(oldRanges, newRanges);
        }

        final List<DiffType> ops = new ArrayList<>();
        MyersDiff.diff(toInts(oldLine), toInts(newLine), ops);

        // Gather the regions that differ, as pairs of old and new ranges, joining ones that are only
        // separated by a short run of equal characters.
        int oldPos = 0;
        int newPos = 0;
        int regionOldStart = -1;
        int regionNewStart = -1;
        int regionOldEnd = -1;
        int regionNewEnd = -1;
        int index = 0;
        while (index < ops.size()) {
            final DiffType op = ops.get(index);
            if (op == DiffType.EQUAL) {
                int run = 0;
                while (index < ops.size() && ops.get(index) == DiffType.EQUAL) {
                    run++;
                    index++;
                }
                final boolean atEnd = index >= ops.size();
                if (regionOldStart >= 0 && (run > MERGE_GAP || atEnd)) {
                    addIfNotEmpty(oldRanges, regionOldStart, regionOldEnd);
                    addIfNotEmpty(newRanges, regionNewStart, regionNewEnd);
                    regionOldStart = -1;
                    regionNewStart = -1;
                }
                oldPos += run;
                newPos += run;
            } else {
                if (regionOldStart < 0) {
                    regionOldStart = oldPos;
                    regionNewStart = newPos;
                }
                if (op == DiffType.DELETE) {
                    oldPos++;
                } else {
                    newPos++;
                }
                regionOldEnd = oldPos;
                regionNewEnd = newPos;
                index++;
            }
        }
        if (regionOldStart >= 0) {
            addIfNotEmpty(oldRanges, regionOldStart, regionOldEnd);
            addIfNotEmpty(newRanges, regionNewStart, regionNewEnd);
        }
        return new CharDiff(oldRanges, newRanges);
    }

    /// @return The ranges of changed characters in the old line, in order.
    public List<CharRange> getOldRanges() {
        return oldRanges;
    }

    /// @return The ranges of changed characters in the new line, in order.
    public List<CharRange> getNewRanges() {
        return newRanges;
    }

    private static void addIfNotEmpty(final List<CharRange> ranges, final int start, final int end) {
        if (end > start) {
            ranges.add(new CharRange(start, end));
        }
    }

    private static int[] toInts(final String line) {
        final int[] ints = new int[line.length()];
        for (int i = 0; i < line.length(); i++) {
            ints[i] = line.charAt(i);
        }
        return ints;
    }

    @Override
    public String toString() {
        return oldRanges + " -> " + newRanges;
    }
}
