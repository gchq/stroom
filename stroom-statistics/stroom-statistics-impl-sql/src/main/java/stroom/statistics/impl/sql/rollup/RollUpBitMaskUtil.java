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

import stroom.statistics.impl.sql.StatisticTag;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class RollUpBitMaskUtil {

    /**
     * Builds a {@link RollUpBitMask} object from a list of StatisticTags where
     * the tag's value is either a value or '*' (to mark a roll up). The passed
     * list MUST be ordered by tag name as the positions of the tags in the list
     * directly relate to the bit mask positions.
     *
     * @param tags A list of {@link StatisticTag} objects ordered by their tag
     *             name
     * @return A new {@link RollUpBitMask} object
     */
    public static RollUpBitMask fromSortedTagList(final List<StatisticTag> tags) {
        final SortedSet<Integer> tagPositions = new TreeSet<>();
        int pos = 0;
        if (tags != null) {
            for (final StatisticTag tag : tags) {
                if (RollUpBitMask.ROLL_UP_TAG_VALUE.equals(tag.getValue())) {
                    tagPositions.add(pos);
                }
                pos++;
            }
        }
        return RollUpBitMask.fromTagPositions(tagPositions);
    }

}
