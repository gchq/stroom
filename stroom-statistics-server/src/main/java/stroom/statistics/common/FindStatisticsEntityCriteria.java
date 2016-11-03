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

package stroom.statistics.common;

import stroom.entity.shared.FindDocumentEntityCriteria;
import stroom.entity.shared.PageRequest;
import stroom.entity.shared.StringCriteria;
import stroom.statistics.shared.StatisticType;

import java.util.ArrayList;
import java.util.List;

public class FindStatisticsEntityCriteria extends FindDocumentEntityCriteria {
    private static final long serialVersionUID = -1870494855143328041L;

    private List<String> engineNames = new ArrayList<String>();
    private StatisticType statisticType = null;

    private FindStatisticsEntityCriteria(final String name, final List<String> engineNames,
                                         final StatisticType statisticType) {
        if (name != null) {
            super.setName(new StringCriteria(name));
        }

        if (engineNames != null) {
            this.engineNames = upperCaseEngineList(engineNames);

            // if the list is empty then there are none that we want to return
            // so add a limit of 0 records to return nothing
            if (engineNames.isEmpty()) {
                setPageRequest(new PageRequest(0L, 0));
            }

        }
        if (statisticType != null) {
            this.statisticType = statisticType;
        }
    }

    public static FindStatisticsEntityCriteria instance() {
        return new FindStatisticsEntityCriteria(null, null, null);
    }

    public static FindStatisticsEntityCriteria instanceByName(final String name) {
        return new FindStatisticsEntityCriteria(name, null, null);
    }

    public static FindStatisticsEntityCriteria instanceByEngineName(final String engineName) {
        return new FindStatisticsEntityCriteria(null, singleEngineToList(engineName), null);
    }

    public static FindStatisticsEntityCriteria instanceByEngineNames(final List<String> engineNames) {
        return new FindStatisticsEntityCriteria(null, engineNames, null);
    }

    public static FindStatisticsEntityCriteria instanceByNameAndEngineName(final String name,
            final String engineName) {
        return new FindStatisticsEntityCriteria(name, singleEngineToList(engineName), null);
    }

    public static FindStatisticsEntityCriteria instanceByNameEngineNameAndStatType(final String name,
            final String engineName, final StatisticType statisticType) {
        return new FindStatisticsEntityCriteria(name, singleEngineToList(engineName), statisticType);
    }

    private static List<String> singleEngineToList(final String engine) {
        final List<String> engines = new ArrayList<String>();
        engines.add(engine);
        return engines;
    }

    private List<String> upperCaseEngineList(final List<String> engines) {
        if (engines != null) {
            for (int i = 0; i < engines.size(); i++) {
                engines.set(i, engines.get(i).toUpperCase());
            }
        }

        return engines;
    }

    public List<String> getEngineNames() {
        return engineNames;
    }

    public StatisticType getStatisticType() {
        return statisticType;
    }
}
