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

package stroom.statistics.server.common.rollup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import stroom.util.test.StroomUnitTest;
import org.junit.Assert;
import org.junit.Test;

import stroom.statistics.shared.CustomRollUpMask;
import stroom.statistics.shared.StatisticField;
import stroom.statistics.shared.StatisticsDataSourceData;
import stroom.statistics.shared.StatisticsDataSourceFieldChangeAction;

public class TestStatisticsDataSourceFieldChangeHandler extends StroomUnitTest {
    /**
     * Go from B, C, D to A,B,D
     */
    @Test
    public void testExec() throws Exception {
        final StatisticField fieldA = new StatisticField("A");
        final StatisticField fieldB = new StatisticField("B");
        final StatisticField fieldC = new StatisticField("C");
        final StatisticField fieldD = new StatisticField("D");

        final CustomRollUpMask mask1 = new CustomRollUpMask(new ArrayList<Integer>());
        final CustomRollUpMask mask2 = new CustomRollUpMask(new ArrayList<Integer>(Arrays.asList(0, 1, 2)));
        final CustomRollUpMask mask3 = new CustomRollUpMask(new ArrayList<Integer>(Arrays.asList(0)));
        final CustomRollUpMask mask4 = new CustomRollUpMask(new ArrayList<Integer>(Arrays.asList(1)));
        final CustomRollUpMask mask5 = new CustomRollUpMask(new ArrayList<Integer>(Arrays.asList(2)));

        final List<StatisticField> oldFields = new ArrayList<StatisticField>(Arrays.asList(fieldB, fieldC, fieldD));
        final Set<CustomRollUpMask> oldMasks = new HashSet<CustomRollUpMask>(
                Arrays.asList(mask1, mask2, mask3, mask4, mask5));

        final StatisticsDataSourceData oldData = new StatisticsDataSourceData(oldFields, oldMasks);

        final List<StatisticField> newFields = new ArrayList<StatisticField>(Arrays.asList(fieldA, fieldB, fieldD));

        final StatisticsDataSourceData newData = new StatisticsDataSourceData(newFields);

        final StatisticsDataSourceFieldChangeAction action = new StatisticsDataSourceFieldChangeAction(oldData,
                newData);

        final StatisticsDataSourceFieldChangeHandler handler = new StatisticsDataSourceFieldChangeHandler();

        final StatisticsDataSourceData result = handler.exec(action);

        Assert.assertNotNull(result);

        Assert.assertEquals(4, result.getCustomRollUpMasks().size());

        final CustomRollUpMask newMask1 = new CustomRollUpMask(new ArrayList<Integer>());
        final CustomRollUpMask newMask2 = new CustomRollUpMask(new ArrayList<Integer>(Arrays.asList(1, 2)));
        final CustomRollUpMask newMask3 = new CustomRollUpMask(new ArrayList<Integer>(Arrays.asList(1)));
        final CustomRollUpMask newMask5 = new CustomRollUpMask(new ArrayList<Integer>(Arrays.asList(2)));

        Assert.assertTrue(result.getCustomRollUpMasks().contains(newMask1));
        Assert.assertTrue(result.getCustomRollUpMasks().contains(newMask2));
        Assert.assertTrue(result.getCustomRollUpMasks().contains(newMask3));
        Assert.assertTrue(result.getCustomRollUpMasks().contains(newMask5));
    }
}
