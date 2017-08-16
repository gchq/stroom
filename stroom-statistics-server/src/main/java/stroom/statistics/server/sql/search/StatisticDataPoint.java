/*
 * Copyright 2017 Crown Copyright
 *
 * This file is part of Stroom-Stats.
 *
 * Stroom-Stats is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Stroom-Stats is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Stroom-Stats.  If not, see <http://www.gnu.org/licenses/>.
 */

package stroom.statistics.server.sql.search;

import stroom.statistics.server.sql.StatisticTag;
import stroom.statistics.shared.StatisticType;

import java.util.List;
import java.util.Map;

public interface StatisticDataPoint {

    /**
     * @return The time in ms since epoch that the statistic event(s) bucket started
     */
    long getTimeMs();

    /**
     * @return The size of the time bucket that the data point represents
     */
    long getPrecisionMs();

    /**
     * @return A list of the {@link StatisticTag} objects that qualify the data point
     */
    List<StatisticTag> getTags();

    /**
     * @return The qualifying {@link StatisticTag} objects represented as map with the tag as key and tag value as value
     */
    Map<String, String> getTagsAsMap();

    /**
     * @return The {@link StatisticType} of the data point, e.g. COUNT, VALUE, etc.
     */
    StatisticType getStatisticType();

    /**
     * @param fieldName The name of the field to get a value for
     * @return The value of the named field (or null if it doesn't exist) represented as a string
     */
    String getFieldValue(final String fieldName);
}
