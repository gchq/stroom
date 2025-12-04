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

package stroom.statistics.impl.sql;

import stroom.statistics.impl.sql.rollup.RollUpBitMask;
import stroom.statistics.impl.sql.rollup.RollUpBitMaskUtil;

import java.util.List;
import java.util.Objects;

public class SQLStatKey {

    private final long ms;
    private final String name;
    private final int hashCode;

    public SQLStatKey(final long ms,
                      final String statName,
                      final List<StatisticTag> tags) {
        this.ms = ms;
        this.name = buildKeyString(statName, tags);

        // name includes statName and tags
        hashCode = Objects.hash(ms, name);
    }

    /**
     * We do direct SQL with keys so the MUST be cleaned
     */
    public static final String cleanText(final String text) {
        return SQLStatisticsEventValidator.cleanString(text);
    }

    /**
     * SQL Stats stores the name, bit mask, tags and their values all as one big
     * string of the form
     * <p>
     * XXXXXXXXXXaaaa¬Tag1¬Tag1Val¬Tag2¬Tag2Val
     * <p>
     * or if there are no tags at all then
     * <p>
     * XXXXXXXXXXaaaa
     * <p>
     * where XXXXXXXXXX is the stat name and aaaa is the hex form of the rollup
     * bit mask
     */
    private String buildKeyString(final String statName, final List<StatisticTag> tags) {
        Objects.requireNonNull(statName);
        final StringBuilder keyStringBuilder = new StringBuilder();

        keyStringBuilder.append(cleanText(statName));

        // add the rollup bit mask (always 4 hex values, e.g. 7FFA)
        keyStringBuilder.append(RollUpBitMaskUtil.fromSortedTagList(tags).asHexString());

        if (tags != null && tags.size() > 0) {
            for (final StatisticTag tag : tags) {
                keyStringBuilder.append(SQLStatisticConstants.NAME_SEPARATOR);
                keyStringBuilder.append(cleanText(tag.getTag()));
                keyStringBuilder.append(SQLStatisticConstants.NAME_SEPARATOR);

                // handle null/empty values with a magic value
                final String value = cleanTagValue(tag.getValue());
                if (value == null || value.isEmpty()) {
                    keyStringBuilder.append(SQLStatisticConstants.NULL_VALUE_STRING);
                } else {
                    keyStringBuilder.append(value);
                }
            }
        }
        return keyStringBuilder.toString();
    }

    private String cleanTagValue(final String tagValue) {
        if (tagValue != null && tagValue.equals(RollUpBitMask.ROLL_UP_TAG_VALUE)) {
            return tagValue;
        } else {
            return cleanText(tagValue);
        }
    }

    public long getMs() {
        return ms;
    }

    /**
     * @return The compound name consisting of the statistic name, the roll up
     * mask in hex form and any tag/value pairs
     */
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SQLStatKey that = (SQLStatKey) o;
        return ms == that.ms &&
                name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return name;
    }
}
