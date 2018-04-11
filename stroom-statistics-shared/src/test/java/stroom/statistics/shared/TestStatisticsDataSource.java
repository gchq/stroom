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

package stroom.statistics.shared;

import org.junit.Assert;
import org.junit.Test;
import stroom.statistics.shared.common.CustomRollUpMask;
import stroom.statistics.shared.common.StatisticField;
import stroom.statistics.shared.common.StatisticRollUpType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class TestStatisticsDataSource {
    private static final String FIELD1 = "field1";
    private static final String FIELD2 = "field2";
    private static final String FIELD3 = "field3";

    @Test
    public void testIsValidFieldPass() {
        final StatisticStoreEntity sds = buildStatisticsDataSource(true);

        final String fieldToTest = FIELD1;

        Assert.assertTrue(sds.isValidField(fieldToTest));
    }

    @Test
    public void testIsValidFieldFailBadFieldName() {
        final StatisticStoreEntity sds = buildStatisticsDataSource(true);

        final String fieldToTest = "BadFieldName";

        Assert.assertFalse(sds.isValidField(fieldToTest));
    }

    @Test
    public void testIsValidFieldFailNoFields() {
        // build with no fields
        final StatisticStoreEntity sds = buildStatisticsDataSource(false);

        final String fieldToTest = "BadFieldName";

        Assert.assertFalse(sds.isValidField(fieldToTest));
    }

    @Test
    public void testListOrder1() {
        final StatisticStoreEntity sds = buildStatisticsDataSource(true);

        Assert.assertEquals(
                Arrays.asList(new StatisticField(FIELD1), new StatisticField(FIELD2), new StatisticField(FIELD3)),
                new ArrayList<>(sds.getStatisticDataSourceDataObject().getStatisticFields()));

        Assert.assertEquals(Arrays.asList(FIELD1, FIELD2, FIELD3), getFieldNames(sds));

        Assert.assertEquals(Arrays.asList(FIELD1, FIELD2, FIELD3), sds.getFieldNames());
    }

    @Test
    public void testListOrder2() {
        final StatisticStoreEntity sds = buildStatisticsDataSource(true);

        Assert.assertEquals(
                Arrays.asList(new StatisticField(FIELD1), new StatisticField(FIELD2), new StatisticField(FIELD3)),
                sds.getStatisticDataSourceDataObject().getStatisticFields());

        Assert.assertEquals(Arrays.asList(FIELD1, FIELD2, FIELD3), getFieldNames(sds));

        Assert.assertEquals(Arrays.asList(FIELD1, FIELD2, FIELD3), sds.getFieldNames());

        // remove an item and check the order

        sds.getStatisticDataSourceDataObject().removeStatisticField(new StatisticField(FIELD2));

        Assert.assertEquals(Arrays.asList(new StatisticField(FIELD1), new StatisticField(FIELD3)),
                sds.getStatisticDataSourceDataObject().getStatisticFields());

        Assert.assertEquals(Arrays.asList(FIELD1, FIELD3), getFieldNames(sds));

        Assert.assertEquals(Arrays.asList(FIELD1, FIELD3), sds.getFieldNames());

        // add an item back in and check the order

        sds.getStatisticDataSourceDataObject().addStatisticField(new StatisticField(FIELD2));

        Assert.assertEquals(
                Arrays.asList(new StatisticField(FIELD1), new StatisticField(FIELD2), new StatisticField(FIELD3)),
                sds.getStatisticDataSourceDataObject().getStatisticFields());

        Assert.assertEquals(Arrays.asList(FIELD1, FIELD2, FIELD3), getFieldNames(sds));

        Assert.assertEquals(Arrays.asList(FIELD1, FIELD2, FIELD3), sds.getFieldNames());
    }

    private List<String> getFieldNames(final StatisticStoreEntity sds) {
        final List<String> list = new ArrayList<>();
        for (final StatisticField statisticField : sds.getStatisticDataSourceDataObject().getStatisticFields()) {
            list.add(statisticField.getFieldName());
        }
        return list;
    }

    @Test
    public void testFieldPositions() {
        final StatisticStoreEntity sds = buildStatisticsDataSource(true);

        Assert.assertEquals(0, sds.getPositionInFieldList(FIELD1).intValue());
        Assert.assertEquals(1, sds.getPositionInFieldList(FIELD2).intValue());
        Assert.assertEquals(2, sds.getPositionInFieldList(FIELD3).intValue());

        sds.getStatisticDataSourceDataObject().removeStatisticField(new StatisticField(FIELD2));

        Assert.assertEquals(0, sds.getPositionInFieldList(FIELD1).intValue());
        Assert.assertEquals(null, sds.getPositionInFieldList(FIELD2));
        Assert.assertEquals(1, sds.getPositionInFieldList(FIELD3).intValue());

        sds.getStatisticDataSourceDataObject().addStatisticField(new StatisticField(FIELD2));

        Assert.assertEquals(0, sds.getPositionInFieldList(FIELD1).intValue());
        Assert.assertEquals(1, sds.getPositionInFieldList(FIELD2).intValue());
        Assert.assertEquals(2, sds.getPositionInFieldList(FIELD3).intValue());
    }

    @Test
    public void testIsRollUpCombinationSupported_nullList() {
        final StatisticStoreEntity sds = buildStatisticsDataSource(true);

        Assert.assertTrue(sds.isRollUpCombinationSupported(null));
    }

    @Test
    public void testIsRollUpCombinationSupported_emptyList() {
        final StatisticStoreEntity sds = buildStatisticsDataSource(true);

        Assert.assertTrue(sds.isRollUpCombinationSupported(new HashSet<>()));
    }

    @Test
    public void testIsRollUpCombinationSupported_rollUpTypeAll() {
        final StatisticStoreEntity sds = buildStatisticsDataSource(true);

        sds.setRollUpType(StatisticRollUpType.ALL);

        Assert.assertTrue(sds.isRollUpCombinationSupported(new HashSet<>(Arrays.asList(FIELD1))));
    }

    @Test
    public void testIsRollUpCombinationSupported_rollUpTypeNone() {
        final StatisticStoreEntity sds = buildStatisticsDataSource(true);

        sds.setRollUpType(StatisticRollUpType.NONE);

        Assert.assertFalse(sds.isRollUpCombinationSupported(new HashSet<>(Arrays.asList(FIELD1))));
    }

    @Test
    public void testIsRollUpCombinationSupported_rollUpTypeCustom() {
        final StatisticStoreEntity sds = buildStatisticsDataSource(true);

        sds.setRollUpType(StatisticRollUpType.CUSTOM);

        // check it copes in or out of order
        Assert.assertTrue(sds.isRollUpCombinationSupported(new HashSet<>(Arrays.asList(FIELD1, FIELD2, FIELD3))));
        Assert.assertTrue(sds.isRollUpCombinationSupported(new HashSet<>(Arrays.asList(FIELD2, FIELD3, FIELD1))));

        // check the other valid combinations
        Assert.assertTrue(sds.isRollUpCombinationSupported(new HashSet<>(Arrays.asList(FIELD1, FIELD2))));
        Assert.assertTrue(sds.isRollUpCombinationSupported(new HashSet<>(Arrays.asList(FIELD1))));

        Assert.assertFalse(sds.isRollUpCombinationSupported(new HashSet<>(Arrays.asList(FIELD3))));
    }

    private StatisticStoreEntity buildStatisticsDataSource(final boolean addFields) {
        final StatisticsDataSourceData statisticsDataSourceData = new StatisticsDataSourceData();

        if (addFields) {
            statisticsDataSourceData.addStatisticField(new StatisticField(FIELD2));
            statisticsDataSourceData.addStatisticField(new StatisticField(FIELD3));
            statisticsDataSourceData.addStatisticField(new StatisticField(FIELD1));

            statisticsDataSourceData.addCustomRollUpMask(new CustomRollUpMask(Arrays.asList(0, 1, 2))); // fields
            // 1,2,3
            statisticsDataSourceData.addCustomRollUpMask(new CustomRollUpMask(Arrays.asList(0, 1))); // fields
            // 1,2
            statisticsDataSourceData.addCustomRollUpMask(new CustomRollUpMask(Arrays.asList(0))); // field
            // 1
            statisticsDataSourceData.addCustomRollUpMask(new CustomRollUpMask(Collections.emptyList()));
        }

        final StatisticStoreEntity sds = new StatisticStoreEntity();
        sds.setStatisticDataSourceDataObject(statisticsDataSourceData);
        return sds;
    }
}
