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

import stroom.statistics.sql.SQLStatisticEventStore;
import stroom.statistics.sql.SQLStatisticsEventValidator;

public class StatisticsEventValidatorFactory {
    private static final StatisticsEventValidator SQL_STATISTICS_EVENT_VALIDATOR;

    // The validators are stateless so hold a single instance that can be reused
    // again and again.
    static {
        SQL_STATISTICS_EVENT_VALIDATOR = new SQLStatisticsEventValidator();
    }

    public static StatisticsEventValidator getInstance(String engineName) {
        if (engineName == null) {
            throw new NullPointerException("Cannot pass a null engineName to getInstance");
        } else if (engineName.toLowerCase().equals(SQLStatisticEventStore.ENGINE_NAME)) {
            return SQL_STATISTICS_EVENT_VALIDATOR;
        } else {
            throw new IllegalArgumentException(
                    String.format("The supplied engineName [%s] is not supported", engineName));
        }
    }
}
