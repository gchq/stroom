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


import stroom.statistics.impl.sql.shared.CustomRollUpMask;
import stroom.statistics.impl.sql.shared.StatisticField;
import stroom.statistics.impl.sql.shared.StatisticRollupResource;
import stroom.statistics.impl.sql.shared.StatisticsDataSourceData;
import stroom.statistics.impl.sql.shared.StatisticsDataSourceFieldChangeRequest;
import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestStatisticsDataSourceFieldChangesHandler extends StroomUnitTest {

    /**
     * Go from B, C, D to A,B,D
     */
    @Test
    void testExec() {
        final StatisticField fieldA = new StatisticField("A");
        final StatisticField fieldB = new StatisticField("B");
        final StatisticField fieldC = new StatisticField("C");
        final StatisticField fieldD = new StatisticField("D");

        final CustomRollUpMask mask1 = new CustomRollUpMask(new ArrayList<>());
        final CustomRollUpMask mask2 = new CustomRollUpMask(new ArrayList<>(Arrays.asList(0, 1, 2)));
        final CustomRollUpMask mask3 = new CustomRollUpMask(new ArrayList<>(Arrays.asList(0)));
        final CustomRollUpMask mask4 = new CustomRollUpMask(new ArrayList<>(Arrays.asList(1)));
        final CustomRollUpMask mask5 = new CustomRollUpMask(new ArrayList<>(Arrays.asList(2)));

        final List<StatisticField> oldFields = new ArrayList<>(Arrays.asList(fieldB, fieldC, fieldD));
        final Set<CustomRollUpMask> oldMasks = new HashSet<>(
                Arrays.asList(mask1, mask2, mask3, mask4, mask5));

        final StatisticsDataSourceData oldData = new StatisticsDataSourceData(oldFields, oldMasks);

        final List<StatisticField> newFields = new ArrayList<>(Arrays.asList(fieldA, fieldB, fieldD));

        final StatisticsDataSourceData newData = new StatisticsDataSourceData(newFields);

        final StatisticsDataSourceFieldChangeRequest request = new StatisticsDataSourceFieldChangeRequest(oldData,
                newData);

        final StatisticRollupResource resource =
                new StatisticRollupResourceImpl(() -> new StatisticRollupServiceImpl());

        final StatisticsDataSourceData result = resource.fieldChange(request);

        assertThat(result).isNotNull();

        assertThat(result.getCustomRollUpMasks().size()).isEqualTo(4);

        final CustomRollUpMask newMask1 = new CustomRollUpMask(new ArrayList<>());
        final CustomRollUpMask newMask2 = new CustomRollUpMask(new ArrayList<>(Arrays.asList(1, 2)));
        final CustomRollUpMask newMask3 = new CustomRollUpMask(new ArrayList<>(Arrays.asList(1)));
        final CustomRollUpMask newMask5 = new CustomRollUpMask(new ArrayList<>(Arrays.asList(2)));

        assertThat(result.getCustomRollUpMasks().contains(newMask1)).isTrue();
        assertThat(result.getCustomRollUpMasks().contains(newMask2)).isTrue();
        assertThat(result.getCustomRollUpMasks().contains(newMask3)).isTrue();
        assertThat(result.getCustomRollUpMasks().contains(newMask5)).isTrue();
    }
}
