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

package stroom.statistics.stroomstats.rollup;

import stroom.security.Insecure;
import stroom.stats.shared.CustomRollUpMask;
import stroom.stats.shared.StatisticField;
import stroom.stats.shared.StroomStatsStoreEntityData;
import stroom.stats.shared.StroomStatsStoreFieldChangeAction;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;

import java.util.HashMap;
import java.util.Map;

@TaskHandlerBean(task = StroomStatsStoreFieldChangeAction.class)
@Insecure
class StroomStatsStoreFieldChangeHandler
        extends AbstractTaskHandler<StroomStatsStoreFieldChangeAction, StroomStatsStoreEntityData> {
    @Override
    public StroomStatsStoreEntityData exec(final StroomStatsStoreFieldChangeAction action) {
        final StroomStatsStoreEntityData oldStatisticsDataSourceData = action.getOldEntityData();
        final StroomStatsStoreEntityData newStatisticsDataSourceData = action.getNewEntityData();
        final StroomStatsStoreEntityData copy = newStatisticsDataSourceData.deepCopy();

        if (!oldStatisticsDataSourceData.getStatisticFields()
                .equals(newStatisticsDataSourceData.getStatisticFields())) {
            final Map<Integer, Integer> newToOldFieldPositionMap = new HashMap<>();

            int pos = 0;
            for (final StatisticField newField : newStatisticsDataSourceData.getStatisticFields()) {
                // old position may be null if this is a new field
                newToOldFieldPositionMap.put(pos++,
                        oldStatisticsDataSourceData.getFieldPositionInList(newField.getFieldName()));
            }

            copy.clearCustomRollUpMask();

            for (final CustomRollUpMask oldCustomRollUpMask : oldStatisticsDataSourceData.getCustomRollUpMasks()) {
                final RollUpBitMask oldRollUpBitMask = RollUpBitMask
                        .fromTagPositions(oldCustomRollUpMask.getRolledUpTagPositions());

                final RollUpBitMask newRollUpBitMask = oldRollUpBitMask.convert(newToOldFieldPositionMap);

                copy.addCustomRollUpMask(new CustomRollUpMask(newRollUpBitMask.getTagPositionsAsList()));
            }
        }

        return copy;
    }
}
