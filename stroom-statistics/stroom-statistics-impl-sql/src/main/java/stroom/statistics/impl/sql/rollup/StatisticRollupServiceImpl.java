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
import stroom.statistics.impl.sql.shared.CustomRollUpMaskFields;
import stroom.statistics.impl.sql.shared.StatisticField;
import stroom.statistics.impl.sql.shared.StatisticsDataSourceData;

import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
public class StatisticRollupServiceImpl implements StatisticRollupService {

    @Override
    public List<CustomRollUpMask> bitMaskPermGeneration(final Integer fieldCount) {
        final Set<List<Integer>> perms = RollUpBitMask.getRollUpPermutationsAsPositions(fieldCount);
        final List<CustomRollUpMask> masks = new ArrayList<>();
        for (final List<Integer> perm : perms) {
            masks.add(new CustomRollUpMask(perm));
        }
        return masks;
    }

    @Override
    public List<CustomRollUpMaskFields> bitMaskConversion(final List<Short> maskValues) {
        final List<CustomRollUpMaskFields> customRollUpMaskFieldsList = new ArrayList<>();

        int id = 0;
        for (final Short maskValue : maskValues) {
            final Set<Integer> tagPositions = RollUpBitMask.fromShort(maskValue).getTagPositions();
            customRollUpMaskFieldsList.add(new CustomRollUpMaskFields(id++, maskValue, tagPositions));
        }
        Collections.sort(customRollUpMaskFieldsList);
        return customRollUpMaskFieldsList;
    }

    @Override
    public StatisticsDataSourceData fieldChange(final StatisticsDataSourceData oldStatisticsDataSourceData,
                                                final StatisticsDataSourceData newStatisticsDataSourceData) {
        final StatisticsDataSourceData copy = newStatisticsDataSourceData.deepCopy();

        if (!oldStatisticsDataSourceData.getFields()
                .equals(newStatisticsDataSourceData.getFields())) {
            final Map<Integer, Integer> newToOldFieldPositionMap = new HashMap<>();

            int pos = 0;
            for (final StatisticField newField : newStatisticsDataSourceData.getFields()) {
                // old position may be null if this is a new field
                newToOldFieldPositionMap.put(pos++,
                        oldStatisticsDataSourceData.getFieldPositionInList(newField.getFieldName()));
            }

            copy.clearCustomRollUpMask();

            for (final CustomRollUpMask oldCustomRollUpMask : oldStatisticsDataSourceData.getCustomRollUpMasks()) {
                final RollUpBitMask oldRollUpBitMask = RollUpBitMask
                        .fromTagPositions(oldCustomRollUpMask.getRolledUpTagPosition());

                final RollUpBitMask newRollUpBitMask = oldRollUpBitMask.convert(newToOldFieldPositionMap);

                copy.addCustomRollUpMask(new CustomRollUpMask(newRollUpBitMask.getTagPositionsAsList()));
            }
        }

        return copy;
    }

}
