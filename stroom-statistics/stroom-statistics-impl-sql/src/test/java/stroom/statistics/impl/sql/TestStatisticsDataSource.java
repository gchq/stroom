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


import stroom.statistics.impl.sql.search.StatisticStoreDocUtil;
import stroom.statistics.impl.sql.shared.CustomRollUpMask;
import stroom.statistics.impl.sql.shared.StatisticField;
import stroom.statistics.impl.sql.shared.StatisticRollUpType;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticsDataSourceData;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestStatisticsDataSource {

    private static final String FIELD1 = "field1";
    private static final String FIELD2 = "field2";
    private static final String FIELD3 = "field3";

    @Test
    void testIsValidFieldPass() {
        final StatisticStoreDoc sds = buildStatisticsDataSource(true);
        assertThat(StatisticStoreDocUtil.createFieldPositionMap(sds).containsKey(FIELD1)).isTrue();
    }

    @Test
    void testIsValidFieldFailBadFieldName() {
        final StatisticStoreDoc sds = buildStatisticsDataSource(true);

        final String fieldToTest = "BadFieldName";

        assertThat(StatisticStoreDocUtil.createFieldPositionMap(sds).containsKey(fieldToTest)).isFalse();
    }

    @Test
    void testIsValidFieldFailNoFields() {
        // build with no fields
        final StatisticStoreDoc sds = buildStatisticsDataSource(false);

        final String fieldToTest = "BadFieldName";

        assertThat(StatisticStoreDocUtil.createFieldPositionMap(sds).containsKey(fieldToTest)).isFalse();
    }

    @Test
    void testListOrder1() {
        final StatisticStoreDoc sds = buildStatisticsDataSource(true);

        assertThat(new ArrayList<>(sds.getConfig().getFields())).isEqualTo(List.of(new StatisticField(FIELD1),
                new StatisticField(FIELD2),
                new StatisticField(FIELD3)));

        assertThat(StatisticStoreDocUtil.getFieldNames(sds)).isEqualTo(List.of(FIELD1, FIELD2, FIELD3));

        assertThat(StatisticStoreDocUtil.getFieldNames(sds)).isEqualTo(List.of(FIELD1, FIELD2, FIELD3));
    }

    @Test
    void testListOrder2() {
        StatisticStoreDoc sds = buildStatisticsDataSource(true);

        assertThat(sds.getConfig().getFields()).isEqualTo(List.of(new StatisticField(FIELD1),
                new StatisticField(FIELD2),
                new StatisticField(FIELD3)));

        assertThat(StatisticStoreDocUtil.getFieldNames(sds)).isEqualTo(List.of(FIELD1, FIELD2, FIELD3));

        assertThat(StatisticStoreDocUtil.getFieldNames(sds)).isEqualTo(List.of(FIELD1, FIELD2, FIELD3));

        // remove an item and check the order
        sds = StatisticStoreDocUtil.removeField(sds, new StatisticField(FIELD2));

        assertThat(sds.getConfig().getFields()).isEqualTo(List.of(
                new StatisticField(FIELD1),
                new StatisticField(FIELD3)));

        assertThat(StatisticStoreDocUtil.getFieldNames(sds)).isEqualTo(List.of(FIELD1, FIELD3));

        assertThat(StatisticStoreDocUtil.getFieldNames(sds)).isEqualTo(List.of(FIELD1, FIELD3));

        // add an item back in and check the order

        sds = StatisticStoreDocUtil.addField(sds, new StatisticField(FIELD2));

        assertThat(sds.getConfig().getFields()).isEqualTo(List.of(new StatisticField(FIELD1),
                new StatisticField(FIELD2),
                new StatisticField(FIELD3)));

        assertThat(StatisticStoreDocUtil.getFieldNames(sds)).isEqualTo(List.of(FIELD1, FIELD2, FIELD3));

        assertThat(StatisticStoreDocUtil.getFieldNames(sds)).isEqualTo(List.of(FIELD1, FIELD2, FIELD3));
    }

    @Test
    void testFieldPositions() {
        StatisticStoreDoc sds = buildStatisticsDataSource(true);

        assertThat(StatisticStoreDocUtil.createFieldPositionMap(sds).get(FIELD1).intValue()).isEqualTo(0);
        assertThat(StatisticStoreDocUtil.createFieldPositionMap(sds).get(FIELD2).intValue()).isEqualTo(1);
        assertThat(StatisticStoreDocUtil.createFieldPositionMap(sds).get(FIELD3).intValue()).isEqualTo(2);

        sds = StatisticStoreDocUtil.removeField(sds, new StatisticField(FIELD2));

        assertThat(StatisticStoreDocUtil.createFieldPositionMap(sds).get(FIELD1).intValue()).isEqualTo(0);
        assertThat(StatisticStoreDocUtil.createFieldPositionMap(sds).get(FIELD2)).isEqualTo(null);
        assertThat(StatisticStoreDocUtil.createFieldPositionMap(sds).get(FIELD3).intValue()).isEqualTo(1);

        sds = StatisticStoreDocUtil.addField(sds, new StatisticField(FIELD2));

        assertThat(StatisticStoreDocUtil.createFieldPositionMap(sds).get(FIELD1).intValue()).isEqualTo(0);
        assertThat(StatisticStoreDocUtil.createFieldPositionMap(sds).get(FIELD2).intValue()).isEqualTo(1);
        assertThat(StatisticStoreDocUtil.createFieldPositionMap(sds).get(FIELD3).intValue()).isEqualTo(2);
    }

    @Test
    void testIsRollUpCombinationSupported_nullList() {
        final StatisticStoreDoc sds = buildStatisticsDataSource(true);
        assertThat(StatisticStoreDocUtil.isRollUpCombinationSupported(sds,
                null)).isTrue();
    }

    @Test
    void testIsRollUpCombinationSupported_emptyList() {
        final StatisticStoreDoc sds = buildStatisticsDataSource(true);
        assertThat(StatisticStoreDocUtil.isRollUpCombinationSupported(sds,
                new HashSet<>())).isTrue();
    }

    @Test
    void testIsRollUpCombinationSupported_rollUpTypeAll() {
        final StatisticStoreDoc sds = buildStatisticsDataSource(true)
                .copy()
                .rollUpType(StatisticRollUpType.ALL)
                .build();
        assertThat(StatisticStoreDocUtil.isRollUpCombinationSupported(sds,
                new HashSet<>(List.of(FIELD1)))).isTrue();
    }

    @Test
    void testIsRollUpCombinationSupported_rollUpTypeNone() {
        final StatisticStoreDoc sds = buildStatisticsDataSource(true)
                .copy()
                .rollUpType(StatisticRollUpType.NONE)
                .build();
        assertThat(StatisticStoreDocUtil.isRollUpCombinationSupported(sds,
                new HashSet<>(List.of(FIELD1)))).isFalse();
    }

    @Test
    void testIsRollUpCombinationSupported_rollUpTypeCustom() {
        final StatisticStoreDoc sds = buildStatisticsDataSource(true)
                .copy()
                .rollUpType(StatisticRollUpType.CUSTOM)
                .build();

        // check it copes in or out of order
        assertThat(StatisticStoreDocUtil.isRollUpCombinationSupported(sds,
                new HashSet<>(List.of(FIELD1, FIELD2, FIELD3)))).isTrue();
        assertThat(StatisticStoreDocUtil.isRollUpCombinationSupported(sds,
                new HashSet<>(List.of(FIELD2, FIELD3, FIELD1)))).isTrue();

        // check the other valid combinations
        assertThat(StatisticStoreDocUtil.isRollUpCombinationSupported(sds,
                new HashSet<>(List.of(FIELD1, FIELD2)))).isTrue();
        assertThat(StatisticStoreDocUtil.isRollUpCombinationSupported(sds,
                new HashSet<>(List.of(FIELD1)))).isTrue();

        assertThat(StatisticStoreDocUtil.isRollUpCombinationSupported(sds,
                new HashSet<>(List.of(FIELD3)))).isFalse();
    }

    private StatisticStoreDoc buildStatisticsDataSource(final boolean addFields) {
        StatisticsDataSourceData statisticsDataSourceData = StatisticsDataSourceData.builder().build();

        if (addFields) {
            statisticsDataSourceData = statisticsDataSourceData
                    .copy()
                    .fields(List.of(
                            new StatisticField(FIELD2),
                            new StatisticField(FIELD3),
                            new StatisticField(FIELD1)))
                    .build();

            statisticsDataSourceData = StatisticStoreDocUtil.addCustomRollUpMask(statisticsDataSourceData,
                    new CustomRollUpMask(List.of(0, 1, 2))); // fields
            // 1,2,3
            statisticsDataSourceData = StatisticStoreDocUtil.addCustomRollUpMask(statisticsDataSourceData,
                    new CustomRollUpMask(List.of(0, 1))); // fields
            // 1,2
            statisticsDataSourceData = StatisticStoreDocUtil.addCustomRollUpMask(statisticsDataSourceData,
                    new CustomRollUpMask(List.of(0))); // field
            // 1
            statisticsDataSourceData = StatisticStoreDocUtil.addCustomRollUpMask(statisticsDataSourceData,
                    new CustomRollUpMask(Collections.emptyList()));
        }

        return StatisticStoreDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .config(statisticsDataSourceData)
                .build();
    }
}
