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

package stroom.statistics.server.stroomstats.entity;

import stroom.entity.shared.FindDocumentEntityCriteria;
import stroom.entity.shared.StringCriteria;
import stroom.statistics.shared.StatisticType;

public class FindStroomStatsStoreEntityCriteria extends FindDocumentEntityCriteria {

    private static final long serialVersionUID = -548246480426695203L;

    private StatisticType statisticType = null;

    private FindStroomStatsStoreEntityCriteria(final String name,
                                               final StatisticType statisticType) {
        if (name != null) {
            super.setName(new StringCriteria(name));
        }

        if (statisticType != null) {
            this.statisticType = statisticType;
        }
    }

    public static FindStroomStatsStoreEntityCriteria instance() {
        return new FindStroomStatsStoreEntityCriteria(null, null);
    }

    public static FindStroomStatsStoreEntityCriteria instanceByName(final String name) {
        return new FindStroomStatsStoreEntityCriteria(name, null);
    }

    public static FindStroomStatsStoreEntityCriteria instanceByNameAndStatType(final String name,
                                                                               final StatisticType statisticType) {
        return new FindStroomStatsStoreEntityCriteria(name, statisticType);
    }

    public StatisticType getStatisticType() {
        return statisticType;
    }
}
