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

package stroom.statistics.impl.sql.shared;

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"rolledUpTagPosition"})
@JsonInclude(Include.NON_NULL)
public class CustomRollUpMask implements Comparable<CustomRollUpMask> {

    /**
     * Holds a list of the positions of tags that are rolled up, zero based. The
     * position number is based on the alphanumeric sorted list of tag/field
     * names in the {@link StatisticStoreDoc}. Would use a SortedSet but that
     * is not supported by GWT. Must ensure the contents of this are sorted so
     * that when contains is called on lists of these objects it works
     * correctly.
     */
    @JsonProperty
    private final List<Integer> rolledUpTagPosition;

    @JsonCreator
    public CustomRollUpMask(@JsonProperty("rolledUpTagPosition") final List<Integer> rolledUpTagPosition) {
        final List<Integer> sorted = new ArrayList<>(NullSafe.list(rolledUpTagPosition));
        sorted.sort(Integer::compareTo);
        this.rolledUpTagPosition = Collections.unmodifiableList(sorted);
    }

    public List<Integer> getRolledUpTagPosition() {
        return rolledUpTagPosition;
    }

    public boolean isTagRolledUp(final int position) {
        return rolledUpTagPosition.contains(position);
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CustomRollUpMask that = (CustomRollUpMask) o;
        return Objects.equals(rolledUpTagPosition, that.rolledUpTagPosition);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(rolledUpTagPosition);
    }

    @Override
    public String toString() {
        return "CustomRollUpMask [rolledUpTagPositions=" + rolledUpTagPosition + "]";
    }

    @Override
    public int compareTo(final CustomRollUpMask o) {
        return IntegerListComparator.INSTANCE.compare(rolledUpTagPosition, o.rolledUpTagPosition);
    }

    public static class IntegerListComparator implements Comparator<List<Integer>> {

        public static final Comparator<List<Integer>> INSTANCE = new IntegerListComparator();

        @Override
        public int compare(final List<Integer> a, final List<Integer> b) {
            if (a == b) {
                return 0;
            }
            if (a == null) {
                return -1;
            }
            if (b == null) {
                return 1;
            }

            final int minLen = Math.min(a.size(), b.size());
            for (int i = 0; i < minLen; i++) {
                final int cmp = Integer.compare(a.get(i), b.get(i));
                if (cmp != 0) {
                    return cmp;
                }
            }
            return Integer.compare(a.size(), b.size());
        }
    }
}
