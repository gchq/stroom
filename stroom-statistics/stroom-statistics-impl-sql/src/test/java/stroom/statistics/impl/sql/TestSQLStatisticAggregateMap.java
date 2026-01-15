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

import stroom.statistics.impl.sql.exception.StatisticsEventValidationException;
import stroom.statistics.impl.sql.rollup.RollUpBitMask;
import stroom.statistics.impl.sql.rollup.RolledUpStatisticEvent;
import stroom.statistics.impl.sql.shared.StatisticField;
import stroom.statistics.impl.sql.shared.StatisticRollUpType;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticsDataSourceData;
import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class TestSQLStatisticAggregateMap extends StroomUnitTest {

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
    void testAddRolledUpEventOneCountEvent() throws StatisticsEventValidationException {
        final SQLStatisticAggregateMap aggregateMap = new SQLStatisticAggregateMap();

        aggregateMap.addRolledUpEvent(buildEvent(COUNT_VAL), precision);

        // one event with 8 perms so 8 entries
        assertThat(aggregateMap.size())
                .isEqualTo(8);

        // time gets rounded to 100_000L
        final long expectedKeyTime = (timeMs / precision) * precision;

        for (final Entry<SQLStatKey, LongAdder> entry : aggregateMap.countEntrySet()) {
            assertThat(entry.getKey().getMs())
                    .isEqualTo(expectedKeyTime);
            assertThat(entry.getValue().longValue())
                    .isEqualTo(1);
        }
    }

    @Test
    void testAddRolledUpEventOneValueEvent() throws StatisticsEventValidationException {
        final SQLStatisticAggregateMap aggregateMap = new SQLStatisticAggregateMap();

        aggregateMap.addRolledUpEvent(buildEvent(VALUE_VAL), precision);

        // one event with 8 perms so 8 entries
        assertThat(aggregateMap.size())
                .isEqualTo(8);

        // time gets rounded to 100_000L
        final long expectedKeyTime = (timeMs / precision) * precision;

        for (final Entry<SQLStatKey, SQLStatisticAggregateMap.ValueStatValue> entry : aggregateMap.valueEntrySet()) {
            assertThat(entry.getKey().getMs())
                    .isEqualTo(expectedKeyTime);
            assertThat(entry.getValue().getValue())
                    .isCloseTo(VALUE_VAL, within(JUNIT_DOUBLE_DELTA));
            assertThat(entry.getValue().getCount())
                    .isEqualTo(1);
        }
    }

    @Test
    void testAddRolledUpEventThreeCountEventsWithSameKey() throws StatisticsEventValidationException {
        final SQLStatisticAggregateMap aggregateMap = new SQLStatisticAggregateMap();

        aggregateMap.addRolledUpEvent(buildEvent(COUNT_VAL), precision);
        aggregateMap.addRolledUpEvent(buildEvent(COUNT_VAL), precision);
        aggregateMap.addRolledUpEvent(buildEvent(COUNT_VAL), precision);

        // one event with 8 perms so 8 entries
        assertThat(aggregateMap.size())
                .isEqualTo(8);

        // time gets rounded to 100_000L
        final long expectedKeyTime = (timeMs / precision) * precision;

        for (final Entry<SQLStatKey, LongAdder> entry : aggregateMap.countEntrySet()) {
            assertThat(entry.getKey().getMs())
                    .isEqualTo(expectedKeyTime);

            // three events each with a count of 1 so value in map should be 3
            assertThat(entry.getValue().longValue())
                    .isEqualTo(3);
        }
    }

    @Test
    void testAddRolledUpEventThreeValueEventsWithSameKey() throws StatisticsEventValidationException {
        final SQLStatisticAggregateMap aggregateMap = new SQLStatisticAggregateMap();

        aggregateMap.addRolledUpEvent(buildEvent(VALUE_VAL), precision);
        aggregateMap.addRolledUpEvent(buildEvent(VALUE_VAL), precision);
        aggregateMap.addRolledUpEvent(buildEvent(VALUE_VAL), precision);

        // one event with 8 perms so 8 entries
        assertThat(aggregateMap.size())
                .isEqualTo(8);

        // time gets rounded to 100_000L
        final long expectedKeyTime = (timeMs / precision) * precision;

        for (final Entry<SQLStatKey, SQLStatisticAggregateMap.ValueStatValue> entry : aggregateMap.valueEntrySet()) {
            assertThat(entry.getKey().getMs())
                    .isEqualTo(expectedKeyTime);

            // three events each with a count of 1 so value in map should be 3
            assertThat(entry.getValue().getValue())
                    .isCloseTo(VALUE_VAL * 3, within(JUNIT_DOUBLE_DELTA));
            assertThat(entry.getValue().getCount())
                    .isEqualTo(3);
        }
    }

    @Test
    void testAddRolledUpEventThreeCountEventsWithDifferentKeys() throws StatisticsEventValidationException {
        final SQLStatisticAggregateMap aggregateMap = new SQLStatisticAggregateMap();

        aggregateMap.addRolledUpEvent(buildEvent("_1", COUNT_VAL), precision);
        aggregateMap.addRolledUpEvent(buildEvent("_2", COUNT_VAL), precision);
        aggregateMap.addRolledUpEvent(buildEvent("_3", COUNT_VAL), precision);

        // three events with 8 perms each so 24 puts to the map, but three of
        // them are for all tags rolled up
        // 'MyStat0007/colour/*/state/*/user/*' so they go into the same key,
        // thus we take two off
        assertThat(aggregateMap.size())
                .isEqualTo((8 * 3) - 2);

        // time gets rounded to 100_000L
        final long expectedKeyTime = (timeMs / precision) * precision;

        for (final Entry<SQLStatKey, LongAdder> entry : aggregateMap.countEntrySet()) {
            final int markerCount = countStringInString(entry.getKey().getName(), RollUpBitMask.ROLL_UP_TAG_VALUE);

            System.out.println(
                    entry.getKey() + "  val: " + entry.getValue().longValue()
                    + " markerCount: " + markerCount);
            assertThat(entry.getKey().getMs())
                    .isEqualTo(expectedKeyTime);

            // all event perms are put into different keys so value should be 1
            // unless it is the all rolled up version
            // in which case it has had three puts to it
            // Use split as a bit of a hack to count the number of roll up
            // marker chars in the name
            if (markerCount == 3) {
                assertThat(entry.getValue().longValue())
                        .isEqualTo(3);
            } else {
                assertThat(entry.getValue().longValue())
                        .isEqualTo(1);
            }

        }
    }

    @Test
    void testAddRolledUpEventThreeValueEventsWithDifferentKeys() throws StatisticsEventValidationException {
        final SQLStatisticAggregateMap aggregateMap = new SQLStatisticAggregateMap();

        aggregateMap.addRolledUpEvent(buildEvent("_1", VALUE_VAL), precision);
        aggregateMap.addRolledUpEvent(buildEvent("_2", VALUE_VAL), precision);
        aggregateMap.addRolledUpEvent(buildEvent("_3", VALUE_VAL), precision);

        // three events with 8 perms each so 24 puts to the map, but three of
        // them are for all tags rolled up
        // 'MyStat0007/colour/*/state/*/user/*' so they go into the same key,
        // thus we take two off
        assertThat(aggregateMap.size())
                .isEqualTo((8 * 3) - 2);

        // time gets rounded to 100_000L
        final long expectedKeyTime = (timeMs / precision) * precision;

        for (final Entry<SQLStatKey, SQLStatisticAggregateMap.ValueStatValue> entry : aggregateMap.valueEntrySet()) {
            final int markerCount = countStringInString(entry.getKey().getName(), RollUpBitMask.ROLL_UP_TAG_VALUE);

            System.out.println(
                    entry.getKey() + "  val: " + entry.getValue()
                    + " markerCount: " + markerCount);
            assertThat(entry.getKey().getMs())
                    .isEqualTo(expectedKeyTime);

            // all event perms are put into different keys so value should be 1
            // unless it is the all rolled up version
            // in which case it has had three puts to it
            // Use split as a bit of a hack to count the number of roll up
            // marker chars in the name
            if (markerCount == 3) {
                assertThat(entry.getValue().getValue())
                        .isCloseTo(VALUE_VAL * 3, within(JUNIT_DOUBLE_DELTA));
                assertThat(entry.getValue().getCount())
                        .isEqualTo(3);
            } else {
                assertThat(entry.getValue().getValue())
                        .isCloseTo(VALUE_VAL, within(JUNIT_DOUBLE_DELTA));
                assertThat(entry.getValue().getCount())
                        .isEqualTo(1);
            }
        }
    }

    @Test
    void testAdd_TwoMapsEachWithThreeCountEventsWithSameKey() throws StatisticsEventValidationException {
        final SQLStatisticAggregateMap aggregateMap1 = new SQLStatisticAggregateMap();

        aggregateMap1.addRolledUpEvent(buildEvent(COUNT_VAL), precision);
        aggregateMap1.addRolledUpEvent(buildEvent(COUNT_VAL), precision);
        aggregateMap1.addRolledUpEvent(buildEvent(COUNT_VAL), precision);

        final SQLStatisticAggregateMap aggregateMap2 = new SQLStatisticAggregateMap();

        aggregateMap2.addRolledUpEvent(buildEvent(COUNT_VAL), precision);
        aggregateMap2.addRolledUpEvent(buildEvent(COUNT_VAL), precision);
        aggregateMap2.addRolledUpEvent(buildEvent(COUNT_VAL), precision);

        // one event with 8 perms so 8 entries
        assertThat(aggregateMap1.size())
                .isEqualTo(8);
        assertThat(aggregateMap2.size())
                .isEqualTo(8);

        aggregateMap1.add(aggregateMap2);
        assertThat(aggregateMap1.size())
                .isEqualTo(8);
        assertThat(aggregateMap2.size())
                .isEqualTo(8);

        // time gets rounded to 100_000L
        final long expectedKeyTime = (timeMs / precision) * precision;

        for (final Entry<SQLStatKey, LongAdder> entry : aggregateMap1.countEntrySet()) {
            assertThat(entry.getKey().getMs())
                    .isEqualTo(expectedKeyTime);

            // three events each with a count of 1 so value in map should be 3
            assertThat(entry.getValue().longValue())
                    .isEqualTo(6);
        }
    }

    @Test
    void testAdd_TwoMapsEachWithThreeValueEventsWithSameKey() throws StatisticsEventValidationException {
        final SQLStatisticAggregateMap aggregateMap1 = new SQLStatisticAggregateMap();

        aggregateMap1.addRolledUpEvent(buildEvent(VALUE_VAL), precision);
        aggregateMap1.addRolledUpEvent(buildEvent(VALUE_VAL), precision);
        aggregateMap1.addRolledUpEvent(buildEvent(VALUE_VAL), precision);

        final SQLStatisticAggregateMap aggregateMap2 = new SQLStatisticAggregateMap();

        aggregateMap2.addRolledUpEvent(buildEvent(VALUE_VAL), precision);
        aggregateMap2.addRolledUpEvent(buildEvent(VALUE_VAL), precision);
        aggregateMap2.addRolledUpEvent(buildEvent(VALUE_VAL), precision);

        // one event with 8 perms so 8 entries
        assertThat(aggregateMap1.size())
                .isEqualTo(8);
        assertThat(aggregateMap2.size())
                .isEqualTo(8);

        aggregateMap1.add(aggregateMap2);
        assertThat(aggregateMap1.size())
                .isEqualTo(8);
        assertThat(aggregateMap2.size())
                .isEqualTo(8);

        // time gets rounded to 100_000L
        final long expectedKeyTime = (timeMs / precision) * precision;

        for (final Entry<SQLStatKey, SQLStatisticAggregateMap.ValueStatValue> entry : aggregateMap1.valueEntrySet()) {
            assertThat(entry.getKey().getMs())
                    .isEqualTo(expectedKeyTime);

            // three events each with a count of 1 so value in map should be 3
            assertThat(entry.getValue().getValue())
                    .isCloseTo(VALUE_VAL * 6, within(JUNIT_DOUBLE_DELTA));
            assertThat(entry.getValue().getCount())
                    .isEqualTo(6);
        }
    }

    @Test
    void testAdd_TwoMapsEachWithThreeCountEventsWithDifferentKeys() throws StatisticsEventValidationException {
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
        assertThat(aggregateMap1.size())
                .isEqualTo((8 * 3) - 2);
        assertThat(aggregateMap2.size())
                .isEqualTo((8 * 3) - 2);

        aggregateMap1.add(aggregateMap2);

        assertThat(aggregateMap1.size())
                .isEqualTo((8 * 3) - 2);
        assertThat(aggregateMap2.size())
                .isEqualTo((8 * 3) - 2);

        // time gets rounded to 100_000L
        final long expectedKeyTime = (timeMs / precision) * precision;

        for (final Entry<SQLStatKey, LongAdder> entry : aggregateMap1.countEntrySet()) {
            final int markerCount = countStringInString(entry.getKey().getName(), RollUpBitMask.ROLL_UP_TAG_VALUE);

            System.out.println(
                    entry.getKey() + "  val: " + entry.getValue().longValue() + " markerCount: " + markerCount);
            assertThat(entry.getKey().getMs())
                    .isEqualTo(expectedKeyTime);

            // all event perms are put into different keys so value should be 1
            // unless it is the all rolled up version
            // in which case it has had three puts to it
            // Use split as a bit of a hack to count the number of roll up
            // marker chars in the name
            if (markerCount == 3) {
                assertThat(entry.getValue().longValue())
                        .isEqualTo(3 * 2);
            } else {
                assertThat(entry.getValue().longValue())
                        .isEqualTo(1 * 2);
            }

        }
    }

    @Test
    void testAdd_TwoMapsEachWithThreeValueEventsWithDifferentKeys() throws StatisticsEventValidationException {
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
        assertThat(aggregateMap1.size())
                .isEqualTo((8 * 3) - 2);
        assertThat(aggregateMap2.size())
                .isEqualTo((8 * 3) - 2);

        aggregateMap1.add(aggregateMap2);

        assertThat(aggregateMap1.size())
                .isEqualTo((8 * 3) - 2);
        assertThat(aggregateMap2.size())
                .isEqualTo((8 * 3) - 2);

        // time gets rounded to 100_000L
        final long expectedKeyTime = (timeMs / precision) * precision;

        for (final Entry<SQLStatKey, SQLStatisticAggregateMap.ValueStatValue> entry : aggregateMap1.valueEntrySet()) {
            final int markerCount = countStringInString(entry.getKey().getName(), RollUpBitMask.ROLL_UP_TAG_VALUE);

            System.out.println(
                    entry.getKey() + "  val: " + entry.getValue() + " markerCount: " + markerCount);
            assertThat(entry.getKey().getMs())
                    .isEqualTo(expectedKeyTime);

            // all event perms are put into different keys so value should be 1
            // unless it is the all rolled up version
            // in which case it has had three puts to it
            // Use split as a bit of a hack to count the number of roll up
            // marker chars in the name
            if (markerCount == 3) {
                assertThat(entry.getValue().getValue())
                        .isCloseTo(VALUE_VAL * 3 * 2, within(JUNIT_DOUBLE_DELTA));
                assertThat(entry.getValue().getCount())
                        .isEqualTo(3 * 2);
            } else {
                assertThat(entry.getValue().getValue())
                        .isCloseTo(VALUE_VAL * 2, within(JUNIT_DOUBLE_DELTA));
                assertThat(entry.getValue().getCount())
                        .isEqualTo(2);
            }
        }
    }

    @Test
    void testAddRolledUpEventKeyTooLong() {
        assertThatThrownBy(() -> {
            final SQLStatisticAggregateMap aggregateMap = new SQLStatisticAggregateMap();

            final StringBuilder statNameBuilder = new StringBuilder();

            for (int i = 0; i < SQLStatisticConstants.STAT_VAL_SRC_NAME_COLUMN_LENGTH + 1; i++) {
                statNameBuilder.append("x");
            }

            aggregateMap.addRolledUpEvent(buildEvent(statNameBuilder.toString(), "", COUNT_VAL), precision);
        })
                .isInstanceOf(StatisticsEventValidationException.class);
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
        final StatisticEvent event;

        if (value instanceof Long) {
            event = StatisticEvent.createCount(timeMs, statName, buildTagList(valueSuffix), (Long) value);
        } else {
            event = StatisticEvent.createValue(timeMs, statName, buildTagList(valueSuffix), (Double) value);
        }

        return SQLStatisticEventStore.generateTagRollUps(event, buildStatisticDataSource(StatisticRollUpType.ALL));

    }

    private List<StatisticTag> buildTagList(final String valueSuffix) {
        final List<StatisticTag> tagList = new ArrayList<>();

        tagList.add(new StatisticTag(TAG1_NAME, TAG1_VALUE + valueSuffix));
        tagList.add(new StatisticTag(TAG2_NAME, TAG2_VALUE + valueSuffix));
        tagList.add(new StatisticTag(TAG3_NAME, TAG3_VALUE + valueSuffix));

        return tagList;
    }

    private StatisticStoreDoc buildStatisticDataSource(final StatisticRollUpType statisticRollUpType) {
        final StatisticsDataSourceData statisticFields = new StatisticsDataSourceData();

        final List<StatisticField> fields = new ArrayList<>();

        fields.add(new StatisticField(TAG1_NAME));
        fields.add(new StatisticField(TAG2_NAME));
        fields.add(new StatisticField(TAG3_NAME));

        statisticFields.setFields(fields);

        return StatisticStoreDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .config(statisticFields)
                .rollUpType(statisticRollUpType)
                .build();
    }
}
