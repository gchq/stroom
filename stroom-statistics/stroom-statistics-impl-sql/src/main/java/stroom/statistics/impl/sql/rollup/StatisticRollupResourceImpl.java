/*
 * Copyright 2017 Crown Copyright
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

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.statistics.impl.sql.shared.CustomRollUpMask;
import stroom.statistics.impl.sql.shared.CustomRollUpMaskFields;
import stroom.statistics.impl.sql.shared.StatisticField;
import stroom.statistics.impl.sql.shared.StatisticRollupResource;
import stroom.statistics.impl.sql.shared.StatisticsDataSourceData;
import stroom.statistics.impl.sql.shared.StatisticsDataSourceFieldChangeRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

//Utility functions without side effects (at least by design)
@AutoLogged(OperationType.UNLOGGED)
@Singleton
class StatisticRollupResourceImpl implements StatisticRollupResource {
    final Provider<StatisticRollupService> statisticRollupServiceProvider;

    @Inject
    StatisticRollupResourceImpl(final Provider<StatisticRollupService> statisticRollupServiceProvider) {
        this.statisticRollupServiceProvider = statisticRollupServiceProvider;
    }

    @Override
    public List<CustomRollUpMask> bitMaskPermGeneration(final Integer fieldCount) {
        return statisticRollupServiceProvider.get().bitMaskPermGeneration(fieldCount);
    }

    @Override
    public List<CustomRollUpMaskFields> bitMaskConversion(final List<Short> maskValues) {
        return statisticRollupServiceProvider.get().bitMaskConversion(maskValues);
    }

    @Override
    public StatisticsDataSourceData fieldChange(final StatisticsDataSourceFieldChangeRequest request) {
        return statisticRollupServiceProvider.get().fieldChange(request.getOldStatisticsDataSourceData(),
                request.getNewStatisticsDataSourceData());
    }
}
