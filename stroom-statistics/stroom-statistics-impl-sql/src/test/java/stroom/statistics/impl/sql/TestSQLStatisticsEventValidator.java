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


import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestSQLStatisticsEventValidator {
    private final SQLStatisticsEventValidator validator = new SQLStatisticsEventValidator();

    @Test
    void testValidateEvent_NoTags() {
        final StatisticEvent event = StatisticEvent.createCount(123L, "statName", null, 1);

        final List<String> warnings = validator.validateEvent(event);

        assertThat(warnings.size()).isEqualTo(0);
    }

    @Test
    void testValidateEvent_badTag() {
        final List<StatisticTag> tags = new ArrayList<>();
        tags.add(new StatisticTag("x" + SQLStatisticConstants.NAME_SEPARATOR + "x", "someValue1"));
        tags.add(new StatisticTag("yy", "someValue2"));

        final StatisticEvent event = StatisticEvent.createCount(123L, "statName", tags, 1);

        final List<String> warnings = validator.validateEvent(event);

        assertThat(warnings.size()).isEqualTo(1);
    }

    @Test
    void testValidateEvent_badValue() {
        final List<StatisticTag> tags = new ArrayList<>();
        tags.add(new StatisticTag("xx", "someValue1"));
        tags.add(new StatisticTag("yy", "some" + SQLStatisticConstants.NAME_SEPARATOR + "Value2"));

        final StatisticEvent event = StatisticEvent.createCount(123L, "statName", tags, 1);

        final List<String> warnings = validator.validateEvent(event);

        assertThat(warnings.size()).isEqualTo(1);
    }

    @Test
    void testValidateEvent_badTagsAndValues() {
        final List<StatisticTag> tags = new ArrayList<>();
        tags.add(new StatisticTag("x" + SQLStatisticConstants.NAME_SEPARATOR + "x",
                "some" + SQLStatisticConstants.NAME_SEPARATOR + "Value1"));
        tags.add(new StatisticTag("y" + SQLStatisticConstants.NAME_SEPARATOR + "y",
                "some" + SQLStatisticConstants.NAME_SEPARATOR + "Value2"));

        final StatisticEvent event = StatisticEvent.createCount(123L, "statName", tags, 1);

        final List<String> warnings = validator.validateEvent(event);

        assertThat(warnings.size()).isEqualTo(4);
    }

    @Test
    void testCleanString_dirty() {
        final String dirtyString = "abc" + SQLStatisticConstants.NAME_SEPARATOR + "def";

        final String cleanString = validator.cleanString(dirtyString);

        assertThat(cleanString).isEqualTo("abc#def");

    }

    @Test
    void testCleanString_clean() {
        final String stringToClean = "0123456789aAzZ .()-_$\\/[]{}_!\"'£$%^&*+-=~@";

        final String cleanString = validator.cleanString(stringToClean);

        assertThat(cleanString).isEqualTo(stringToClean);

    }

    @Test
    void testIsKeyToLong_tooLong() {
        final StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < SQLStatisticConstants.STAT_VAL_SRC_NAME_COLUMN_LENGTH + 1; i++) {
            stringBuilder.append("x");
        }

        assertThat(SQLStatisticsEventValidator.isKeyTooLong(stringBuilder.toString())).isTrue();

    }

    @Test
    void testIsKeyToLong_notTooLong() {
        final StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("x");

        assertThat(SQLStatisticsEventValidator.isKeyTooLong(stringBuilder.toString())).isFalse();
    }
}
