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

package stroom.ai.impl.tools;

import java.util.ArrayList;
import java.util.List;

/// Which records of a stream to read when it holds more than can be read: a block at its head, then the rest spread
/// over what follows. Its head alone missed kinds of event further in (seen in testing: the first 500 of 6,000 events
/// were almost all of one kind); spread alone missed what sat between the records read (seen in testing: a byte count
/// only some events early in their stream had, then said to be in none of them). Each catches what the other misses.
public final class RecordPicks {

    /// The share of the records read that are read from the head, as a divisor: a quarter.
    static final int HEAD_SHARE = 4;

    private RecordPicks() {
        // Static only.
    }

    /// @param total How many records the stream holds.
    /// @param want  How many to read.
    /// @return The indexes of the records to read, in order: all of them when the stream holds no more than wanted.
    public static List<Long> headAndSpread(final long total, final int want) {
        final List<Long> picks = new ArrayList<>();
        if (total <= 0 || want <= 0) {
            return picks;
        }
        if (total <= want) {
            for (long index = 0; index < total; index++) {
                picks.add(index);
            }
            return picks;
        }
        final int head = Math.max(1, want / HEAD_SHARE);
        for (long index = 0; index < head; index++) {
            picks.add(index);
        }
        final int rest = want - head;
        final long after = total - head;
        for (int n = 0; n < rest; n++) {
            picks.add(head + n * after / rest);
        }
        return picks;
    }

    /// @param total How many records the stream holds.
    /// @param want  How many are read.
    /// @return How many of them are read from the head; the rest are spread over what follows.
    public static int headCount(final long total, final int want) {
        return total <= want
                ? (int) Math.max(0, total)
                : Math.max(1, want / HEAD_SHARE);
    }
}
