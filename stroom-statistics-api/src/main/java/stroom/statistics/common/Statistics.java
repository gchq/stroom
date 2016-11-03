/*
 * Copyright 2016 Crown Copyright
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

package stroom.statistics.common;

import java.util.List;

import stroom.statistics.shared.StatisticStore;

public interface Statistics {
    /**
     * @return the name of the engine
     */
    String getEngineName();

    /**
     * @param statisticEvent
     *            add event to the store
     * @param statisticsDataSource
     *            The data source to store the stat against
     * @return
     */
    boolean putEvent(StatisticEvent statisticEvent, StatisticStore statisticsDataSource);

    /**
     * @param statisticEvents
     *            add serveral event to the store. All events must be for the
     *            passes data source
     * @param statisticsDataSource
     *            The data source to store the stat against
     * @return
     */
    boolean putEvents(List<StatisticEvent> statisticEvents, StatisticStore statisticsDataSource);

    /**
     * @param statisticEvent
     *            add event to the store
     */
    boolean putEvent(StatisticEvent statisticEvent);

    /**
     * @param statisticEvents
     *            add several events to the store. All events must be for the
     *            same statistic name and engine
     */
    boolean putEvents(List<StatisticEvent> statisticEvents);

    /**
     * For a given statistic tag name, it returns all known values existing in
     * the statistic store
     *
     * @param tagName
     *            The statistic tag name to search for
     * @return A list of values associated with the given statistic tag name
     */
    List<String> getValuesByTag(String tagName);

    /**
     * For a given statistic tag name and part of a value, it returns all known
     * values existing in the statistic store that match
     *
     * @param tagName
     *            The statistic tag name to search for
     * @param partialValue
     *            A contiguous part of a statistic tag value which should be
     *            contained in any matches
     * @return Those statistic tag values that match both criteria
     */
    List<String> getValuesByTagAndPartialValue(String tagName, String partialValue);

    /**
     * Flushes all events currently held in memory down to the persistent event
     * store
     */
    void flushAllEvents();
}
