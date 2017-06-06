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

package stroom.statistics.sql;

import org.apache.commons.lang.mutable.MutableLong;
import org.junit.Test;
import stroom.statistics.sql.exception.StatisticsEventValidationException;
import stroom.statistics.sql.rollup.RollUpBitMask;
import stroom.statistics.shared.common.StatisticField;
import stroom.statistics.shared.common.StatisticRollUpType;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.statistics.shared.StatisticsDataSourceData;
import stroom.statistics.sql.rollup.RolledUpStatisticEvent;
import stroom.util.test.StroomUnitTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import static org.junit.Assert.assertEquals;

public class TestSQLStatisticAggregateMap extends StroomUnitTest {
    private static final long timeMs = 101_000L;
    private static final String statName = "MyStat";
    private static final long precision = 1_000L;
    private static final long COUNT_VAL = 1L;
    private static final double VALUE_VAL = 1.5;
    private static final double JUNIT_DOUBLE_DELTA = 0.0001;

    private static final String TAG1_NAME = "colour";
    private static final String TAG1_VALUE = "Red";

    private static final String TAG2_NAME = "state";
    private static final String TAG2_VALUE = "IN";

    private static final String TAG3_NAME = "user";
    private static final String TAG3_VALUE = "user1";

    @Test
    public void testAddRolledUpEventOneCountEvent() throws StatisticsEventValidationException {
        final SQLStatisticAggregateMap aggregateMap = new SQLStatisticAggregateMap();

        aggregateMap.addRolledUpEvent(buildEvent(COUNT_VAL), precision);

        // one event with 8 perms so 8 entries
        assertEquals(8, aggregateMap.size());

        // time gets rounded to 100_000L
        final long expectedKeyTime = new Long(timeMs / precision) * precision;

        for (final Entry<SQLStatKey, MutableLong> entry : aggregateMap.countEntrySet()) {
            assertEquals(expectedKeyTime, entry.getKey().getMs());
            assertEquals(1, entry.getValue().longValue());
        }
    }

    @Test
    public void testAddRolledUpEventOneValueEvent() throws StatisticsEventValidationException {
        final SQLStatisticAggregateMap aggregateMap = new SQLStatisticAggregateMap();

        aggregateMap.addRolledUpEvent(buildEvent(VALUE_VAL), precision);

        // one event with 8 perms so 8 entries
        assertEquals(8, aggregateMap.size());

        // time gets rounded to 100_000L
        final long expectedKeyTime = new Long(timeMs / precision) * precision;

        for (final Entry<SQLStatKey, MutableLong> entry : aggregateMap.countEntrySet()) {
            assertEquals(expectedKeyTime, entry.getKey().getMs());
            assertEquals(VALUE_VAL, entry.getValue().longValue(), JUNIT_DOUBLE_DELTA);
        }
    }

    @Test
    public void testAddRolledUpEventThreeCountEventsWithSameKey() throws StatisticsEventValidationException {
        final SQLStatisticAggregateMap aggregateMap = new SQLStatisticAggregateMap();

        aggregateMap.addRolledUpEvent(buildEvent(COUNT_VAL), precision);
        aggregateMap.addRolledUpEvent(buildEvent(COUNT_VAL), precision);
        aggregateMap.addRolledUpEvent(buildEvent(COUNT_VAL), precision);

        // one event with 8 perms so 8 entries
        assertEquals(8, aggregateMap.size());

        // time gets rounded to 100_000L
        final long expectedKeyTime = new Long(timeMs / precision) * precision;

        for (final Entry<SQLStatKey, MutableLong> entry : aggregateMap.countEntrySet()) {
            assertEquals(expectedKeyTime, entry.getKey().getMs());

            // three events each with a count of 1 so value in map should be 3
            assertEquals(3, entry.getValue().longValue());
        }
    }

    @Test
    public void testAddRolledUpEventThreeValueEventsWithSameKey() throws StatisticsEventValidationException {
        final SQLStatisticAggregateMap aggregateMap = new SQLStatisticAggregateMap();

        aggregateMap.addRolledUpEvent(buildEvent(VALUE_VAL), precision);
        aggregateMap.addRolledUpEvent(buildEvent(VALUE_VAL), precision);
        aggregateMap.addRolledUpEvent(buildEvent(VALUE_VAL), precision);

        // one event with 8 perms so 8 entries
        assertEquals(8, aggregateMap.size());

        // time gets rounded to 100_000L
        final long expectedKeyTime = new Long(timeMs / precision) * precision;

        for (final Entry<SQLStatKey, MutableLong> entry : aggregateMap.countEntrySet()) {
            assertEquals(expectedKeyTime, entry.getKey().getMs());

            // three events each with a count of 1 so value in map should be 3
            assertEquals(VALUE_VAL * 3, entry.getValue().longValue(), JUNIT_DOUBLE_DELTA);
        }
    }

    @Test
    public void testAddRolledUpEventThreeCountEventsWithDifferentKeys() throws StatisticsEventValidationException {
        final SQLStatisticAggregateMap aggregateMap = new SQLStatisticAggregateMap();

        aggregateMap.addRolledUpEvent(buildEvent("_1", COUNT_VAL), precision);
        aggregateMap.addRolledUpEvent(buildEvent("_2", COUNT_VAL), precision);
        aggregateMap.addRolledUpEvent(buildEvent("_3", COUNT_VAL), precision);

        // three events with 8 perms each so 24 puts to the map, but three of
        // them are for all tags rolled up
        // 'MyStat0007/colour/*/state/*/user/*' so they go into the same key,
        // thus we take two off
        assertEquals((8 * 3) - 2, aggregateMap.size());

        // time gets rounded to 100_000L
        final long expectedKeyTime = new Long(timeMs / precision) * precision;

        for (final Entry<SQLStatKey, MutableLong> entry : aggregateMap.countEntrySet()) {
            final int markerCount = countStringInString(entry.getKey().getName(), RollUpBitMask.ROLL_UP_TAG_VALUE);

            System.out.println(
                    entry.getKey() + "  val: " + entry.getValue().longValue() + " markerCount: " + markerCount);
            assertEquals(expectedKeyTime, entry.getKey().getMs());

            // all event perms are put into different keys so value should be 1
            // unless it is the all rolled up version
            // in which case it has had three puts to it
            // Use split as a bit of a hack to count the number of roll up
            // marker chars in the name
            if (markerCount == 3) {
                assertEquals(3, entry.getValue().longValue());
            } else {
                assertEquals(1, entry.getValue().longValue());
            }

        }
    }

    @Test
    public void testAddRolledUpEventThreeValueEventsWithDifferentKeys() throws StatisticsEventValidationException {
        final SQLStatisticAggregateMap aggregateMap = new SQLStatisticAggregateMap();

        aggregateMap.addRolledUpEvent(buildEvent("_1", VALUE_VAL), precision);
        aggregateMap.addRolledUpEvent(buildEvent("_2", VALUE_VAL), precision);
        aggregateMap.addRolledUpEvent(buildEvent("_3", VALUE_VAL), precision);

        // three events with 8 perms each so 24 puts to the map, but three of
        // them are for all tags rolled up
        // 'MyStat0007/colour/*/state/*/user/*' so they go into the same key,
        // thus we take two off
        assertEquals((8 * 3) - 2, aggregateMap.size());

        // time gets rounded to 100_000L
        final long expectedKeyTime = new Long(timeMs / precision) * precision;

        for (final Entry<SQLStatKey, MutableLong> entry : aggregateMap.countEntrySet()) {
            final int markerCount = countStringInString(entry.getKey().getName(), RollUpBitMask.ROLL_UP_TAG_VALUE);

            System.out.println(
                    entry.getKey() + "  val: " + entry.getValue().longValue() + " markerCount: " + markerCount);
            assertEquals(expectedKeyTime, entry.getKey().getMs());

            // all event perms are put into different keys so value should be 1
            // unless it is the all rolled up version
            // in which case it has had three puts to it
            // Use split as a bit of a hack to count the number of roll up
            // marker chars in the name
            if (markerCount == 3) {
                assertEquals(VALUE_VAL * 3, entry.getValue().longValue(), JUNIT_DOUBLE_DELTA);
            } else {
                assertEquals(VALUE_VAL, entry.getValue().longValue(), JUNIT_DOUBLE_DELTA);
            }

        }
    }

    @Test
    public void testAdd_TwoMapsEachWithThreeCountEventsWithSameKey() throws StatisticsEventValidationException {
        final SQLStatisticAggregateMap aggregateMap1 = new SQLStatisticAggregateMap();

        aggregateMap1.addRolledUpEvent(buildEvent(COUNT_VAL), precision);
        aggregateMap1.addRolledUpEvent(buildEvent(COUNT_VAL), precision);
        aggregateMap1.addRolledUpEvent(buildEvent(COUNT_VAL), precision);

        final SQLStatisticAggregateMap aggregateMap2 = new SQLStatisticAggregateMap();

        aggregateMap2.addRolledUpEvent(buildEvent(COUNT_VAL), precision);
        aggregateMap2.addRolledUpEvent(buildEvent(COUNT_VAL), precision);
        aggregateMap2.addRolledUpEvent(buildEvent(COUNT_VAL), precision);

        // one event with 8 perms so 8 entries
        assertEquals(8, aggregateMap1.size());
        assertEquals(8, aggregateMap2.size());

        aggregateMap1.add(aggregateMap2);
        assertEquals(8, aggregateMap1.size());
        assertEquals(8, aggregateMap2.size());

        // time gets rounded to 100_000L
        final long expectedKeyTime = new Long(timeMs / precision) * precision;

        for (final Entry<SQLStatKey, MutableLong> entry : aggregateMap1.countEntrySet()) {
            assertEquals(expectedKeyTime, entry.getKey().getMs());

            // three events each with a count of 1 so value in map should be 3
            assertEquals(6, entry.getValue().longValue());
        }
    }

    @Test
    public void testAdd_TwoMapsEachWithThreeValueEventsWithSameKey() throws StatisticsEventValidationException {
        final SQLStatisticAggregateMap aggregateMap1 = new SQLStatisticAggregateMap();

        aggregateMap1.addRolledUpEvent(buildEvent(VALUE_VAL), precision);
        aggregateMap1.addRolledUpEvent(buildEvent(VALUE_VAL), precision);
        aggregateMap1.addRolledUpEvent(buildEvent(VALUE_VAL), precision);

        final SQLStatisticAggregateMap aggregateMap2 = new SQLStatisticAggregateMap();

        aggregateMap2.addRolledUpEvent(buildEvent(VALUE_VAL), precision);
        aggregateMap2.addRolledUpEvent(buildEvent(VALUE_VAL), precision);
        aggregateMap2.addRolledUpEvent(buildEvent(VALUE_VAL), precision);

        // one event with 8 perms so 8 entries
        assertEquals(8, aggregateMap1.size());
        assertEquals(8, aggregateMap2.size());

        aggregateMap1.add(aggregateMap2);
        assertEquals(8, aggregateMap1.size());
        assertEquals(8, aggregateMap2.size());

        // time gets rounded to 100_000L
        final long expectedKeyTime = new Long(timeMs / precision) * precision;

        for (final Entry<SQLStatKey, MutableLong> entry : aggregateMap1.countEntrySet()) {
            assertEquals(expectedKeyTime, entry.getKey().getMs());

            // three events each with a count of 1 so value in map should be 3
            assertEquals(VALUE_VAL * 6, entry.getValue().longValue(), JUNIT_DOUBLE_DELTA);
        }
    }

    @Test
    public void testAdd_TwoMapsEachWithThreeCountEventsWithDifferentKeys() throws StatisticsEventValidationException {
        final SQLStatisticAggregateMap aggregateMap1 = new SQLStatisticAggregateMap();

        aggregateMap1.addRolledUpEvent(buildEvent("_1", COUNT_VAL), precision);
        aggregateMap1.addRolledUpEvent(buildEvent("_2", COUNT_VAL), precision);
        aggregateMap1.addRolledUpEvent(buildEvent("_3", COUNT_VAL), precision);

        final SQLStatisticAggregateMap aggregateMap2 = new SQLStatisticAggregateMap();

        aggregateMap2.addRolledUpEvent(buildEvent("_1", COUNT_VAL), precision);
        aggregateMap2.addRolledUpEvent(buildEvent("_2", COUNT_VAL), precision);
        aggregateMap2.addRolledUpEvent(buildEvent("_3", COUNT_VAL), precision);

        // three events with 8 perms each so 24 puts to the map, but three of
        // them are for all tags rolled up
        // 'MyStat0007/colour/*/state/*/user/*' so they go into the same key,
        // thus we take two off
        assertEquals((8 * 3) - 2, aggregateMap1.size());
        assertEquals((8 * 3) - 2, aggregateMap2.size());

        aggregateMap1.add(aggregateMap2);

        assertEquals((8 * 3) - 2, aggregateMap1.size());
        assertEquals((8 * 3) - 2, aggregateMap2.size());

        // time gets rounded to 100_000L
        final long expectedKeyTime = new Long(timeMs / precision) * precision;

        for (final Entry<SQLStatKey, MutableLong> entry : aggregateMap1.countEntrySet()) {
            final int markerCount = countStringInString(entry.getKey().getName(), RollUpBitMask.ROLL_UP_TAG_VALUE);

            System.out.println(
                    entry.getKey() + "  val: " + entry.getValue().longValue() + " markerCount: " + markerCount);
            assertEquals(expectedKeyTime, entry.getKey().getMs());

            // all event perms are put into different keys so value should be 1
            // unless it is the all rolled up version
            // in which case it has had three puts to it
            // Use split as a bit of a hack to count the number of roll up
            // marker chars in the name
            if (markerCount == 3) {
                assertEquals(3 * 2, entry.getValue().longValue());
            } else {
                assertEquals(1 * 2, entry.getValue().longValue());
            }

        }
    }

    @Test
    public void testAdd_TwoMapsEachWithThreeValueEventsWithDifferentKeys() throws StatisticsEventValidationException {
        final SQLStatisticAggregateMap aggregateMap1 = new SQLStatisticAggregateMap();

        aggregateMap1.addRolledUpEvent(buildEvent("_1", VALUE_VAL), precision);
        aggregateMap1.addRolledUpEvent(buildEvent("_2", VALUE_VAL), precision);
        aggregateMap1.addRolledUpEvent(buildEvent("_3", VALUE_VAL), precision);

        final SQLStatisticAggregateMap aggregateMap2 = new SQLStatisticAggregateMap();

        aggregateMap2.addRolledUpEvent(buildEvent("_1", VALUE_VAL), precision);
        aggregateMap2.addRolledUpEvent(buildEvent("_2", VALUE_VAL), precision);
        aggregateMap2.addRolledUpEvent(buildEvent("_3", VALUE_VAL), precision);

        // three events with 8 perms each so 24 puts to the map, but three of
        // them are for all tags rolled up
        // 'MyStat0007/colour/*/state/*/user/*' so they go into the same key,
        // thus we take two off
        assertEquals((8 * 3) - 2, aggregateMap1.size());
        assertEquals((8 * 3) - 2, aggregateMap2.size());

        aggregateMap1.add(aggregateMap2);

        assertEquals((8 * 3) - 2, aggregateMap1.size());
        assertEquals((8 * 3) - 2, aggregateMap2.size());

        // time gets rounded to 100_000L
        final long expectedKeyTime = new Long(timeMs / precision) * precision;

        for (final Entry<SQLStatKey, MutableLong> entry : aggregateMap1.countEntrySet()) {
            final int markerCount = countStringInString(entry.getKey().getName(), RollUpBitMask.ROLL_UP_TAG_VALUE);

            System.out.println(
                    entry.getKey() + "  val: " + entry.getValue().longValue() + " markerCount: " + markerCount);
            assertEquals(expectedKeyTime, entry.getKey().getMs());

            // all event perms are put into different keys so value should be 1
            // unless it is the all rolled up version
            // in which case it has had three puts to it
            // Use split as a bit of a hack to count the number of roll up
            // marker chars in the name
            if (markerCount == 3) {
                assertEquals(VALUE_VAL * 3 * 2, entry.getValue().longValue(), JUNIT_DOUBLE_DELTA);
            } else {
                assertEquals(VALUE_VAL * 2, entry.getValue().longValue(), JUNIT_DOUBLE_DELTA);
            }
        }
    }

    @Test(expected = StatisticsEventValidationException.class)
    public void testAddRolledUpEventKeyTooLong() throws StatisticsEventValidationException {
        final SQLStatisticAggregateMap aggregateMap = new SQLStatisticAggregateMap();

        final StringBuilder statNameBuilder = new StringBuilder();

        for (int i = 0; i < SQLStatisticConstants.STAT_VAL_SRC_NAME_COLUMN_LENGTH + 1; i++) {
            statNameBuilder.append("x");
        }

        aggregateMap.addRolledUpEvent(buildEvent(statNameBuilder.toString(), "", COUNT_VAL), precision);

    }

    private int countStringInString(final String text, final String searchString) {
        int counter = 0;

        String textCopy = text;

        int idx = 0;

        do {
            idx = textCopy.indexOf(searchString);

            if (idx == -1) {
                break;
            }

            textCopy = textCopy.substring(idx + 1);
            counter++;
        } while (idx != -1);

        return counter;

    }

    private RolledUpStatisticEvent buildEvent(final Number value) {
        return buildEvent("", value);
    }

    private RolledUpStatisticEvent buildEvent(final String valueSuffix, final Number value) {
        return buildEvent(statName, valueSuffix, value);
    }

    private RolledUpStatisticEvent buildEvent(final String statName, final String valueSuffix, final Number value) {
        StatisticEvent event;

        if (value instanceof Long) {
            event = StatisticEvent.createCount(timeMs, statName, buildTagList(valueSuffix), (Long) value);
        } else {
            event = StatisticEvent.createValue(timeMs, statName, buildTagList(valueSuffix), (Double) value);
        }

        return SQLStatisticEventStore.generateTagRollUps(event, buildStatisticDataSource(StatisticRollUpType.ALL));

    }

    private List<StatisticTag> buildTagList(final String valueSuffix) {
        final List<StatisticTag> tagList = new ArrayList<StatisticTag>();

        tagList.add(new StatisticTag(TAG1_NAME, TAG1_VALUE + valueSuffix));
        tagList.add(new StatisticTag(TAG2_NAME, TAG2_VALUE + valueSuffix));
        tagList.add(new StatisticTag(TAG3_NAME, TAG3_VALUE + valueSuffix));

        return tagList;
    }

    private StatisticStoreEntity buildStatisticDataSource(final StatisticRollUpType statisticRollUpType) {
        final StatisticStoreEntity statisticsDataSource = new StatisticStoreEntity();

        final StatisticsDataSourceData statisticFields = new StatisticsDataSourceData();

        final List<StatisticField> fields = new ArrayList<StatisticField>();

        fields.add(new StatisticField(TAG1_NAME));
        fields.add(new StatisticField(TAG2_NAME));
        fields.add(new StatisticField(TAG3_NAME));

        statisticFields.setStatisticFields(fields);

        statisticsDataSource.setStatisticDataSourceDataObject(statisticFields);
        statisticsDataSource.setRollUpType(statisticRollUpType);

        return statisticsDataSource;
    }
}
