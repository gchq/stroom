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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestSQLStatisticAggregationTransactionHelper {

    @Test
    void condenseSql_condensed() {
        final String input = "   HELLO     WORLD THIS    IS     ME     ";
        final String output = SQLStatisticAggregationTransactionHelper.condenseSql(input);
        Assertions.assertThat(output)
                .isEqualTo("HELLO WORLD THIS IS ME");
    }

    @Test
    void condenseSql_unchanged() {
        final String input = "HELLO WORLD THIS IS ME";
        final String output = SQLStatisticAggregationTransactionHelper.condenseSql(input);
        Assertions.assertThat(output)
                .isEqualTo(input);
    }
}
