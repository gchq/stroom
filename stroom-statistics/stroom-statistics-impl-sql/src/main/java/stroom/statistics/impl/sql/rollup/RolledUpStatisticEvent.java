/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.statistics.impl.sql.rollup;

import stroom.statistics.impl.sql.StatisticEvent;
import stroom.statistics.impl.sql.StatisticTag;
import stroom.statistics.impl.sql.TimeAgnosticStatisticEvent;
import stroom.statistics.impl.sql.shared.StatisticType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Wrapper for a {@link StatisticEvent} that adds a list of {@link StatisticTag}
 * lists, i.e. the different rollup permutations of the original statistic tag
 * list. The iterator allows you to get access to the underlying
 * {@link TimeAgnosticStatisticEvent} objects which are built on the fly.
 */
public class RolledUpStatisticEvent implements Iterable<TimeAgnosticStatisticEvent> {

    private final StatisticEvent originalStatisticEvent;
    private final List<List<StatisticTag>> tagListPermutations;

    public RolledUpStatisticEvent(final StatisticEvent originalStatisticEvent,
                                  final List<List<StatisticTag>> tagListPermutations) {
        this.originalStatisticEvent = originalStatisticEvent;
        this.tagListPermutations = tagListPermutations;
    }

    /**
     * To be used when no roll ups are required
     */
    public RolledUpStatisticEvent(final StatisticEvent originalStatisticEvent) {
        this.originalStatisticEvent = originalStatisticEvent;
        this.tagListPermutations = new ArrayList<>();
        this.tagListPermutations.add(originalStatisticEvent.getTagList());
    }

    public long getTimeMs() {
        return originalStatisticEvent.getTimeMs();
    }

    // public long getPrecisionMs() {
    // return originalStatisticEvent.getPrecisionMs();
    // }

    public StatisticType getType() {
        return originalStatisticEvent.getType();
    }

    public String getName() {
        return originalStatisticEvent.getName();
    }

    public double getValue() {
        return originalStatisticEvent.getValue();
    }

    public long getCount() {
        return originalStatisticEvent.getCount();
    }

    public int getPermutationCount() {
        return tagListPermutations.size();
    }

    @Override
    public Iterator<TimeAgnosticStatisticEvent> iterator() {
        return new Iterator<TimeAgnosticStatisticEvent>() {
            int nextElement = 0;

            @Override
            public boolean hasNext() {
                if (tagListPermutations == null || tagListPermutations.size() == 0) {
                    return false;
                }

                return nextElement < tagListPermutations.size();
            }

            @Override
            public TimeAgnosticStatisticEvent next() {
                if (tagListPermutations == null || tagListPermutations.size() == 0) {
                    return null;
                }

                // the tag list embedded in the originalStatisticEvent is
                // ignored as it will be contained within the
                // tagListPermutations
                if (originalStatisticEvent.getType().equals(StatisticType.COUNT)) {
                    return TimeAgnosticStatisticEvent.createCount(originalStatisticEvent.getName(),
                            tagListPermutations.get(nextElement++), originalStatisticEvent.getCount());
                } else {
                    return TimeAgnosticStatisticEvent.createValue(originalStatisticEvent.getName(),
                            tagListPermutations.get(nextElement++), originalStatisticEvent.getValue());
                }
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("Remove is not supported for this class");

            }
        };
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((originalStatisticEvent == null)
                ? 0
                : originalStatisticEvent.hashCode());
        result = prime * result + ((tagListPermutations == null)
                ? 0
                : tagListPermutations.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final RolledUpStatisticEvent other = (RolledUpStatisticEvent) obj;
        if (originalStatisticEvent == null) {
            if (other.originalStatisticEvent != null) {
                return false;
            }
        } else if (!originalStatisticEvent.equals(other.originalStatisticEvent)) {
            return false;
        }
        if (tagListPermutations == null) {
            return other.tagListPermutations == null;
        } else {
            return tagListPermutations.equals(other.tagListPermutations);
        }
    }

    @Override
    public String toString() {
        return "RolledUpStatisticEvent [originalStatisticEvent=" + originalStatisticEvent + ", tagListPermutations="
                + tagListPermutations + "]";
    }

}
