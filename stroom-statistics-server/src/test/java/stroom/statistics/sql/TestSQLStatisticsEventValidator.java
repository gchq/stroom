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

package stroom.statistics.sql;

import org.junit.Assert;
import org.junit.Test;
import stroom.statistics.common.StatisticEvent;
import stroom.statistics.common.StatisticTag;

import java.util.ArrayList;
import java.util.List;

public class TestSQLStatisticsEventValidator {
    private final SQLStatisticsEventValidator validator = new SQLStatisticsEventValidator();

    @Test
    public void testValidateEvent_NoTags() throws Exception {
        final StatisticEvent event = StatisticEvent.createCount(123L, "statName", null, 1);

        final List<String> warnings = validator.validateEvent(event);

        Assert.assertEquals(0, warnings.size());
    }

    @Test
    public void testValidateEvent_badTag() throws Exception {
        final List<StatisticTag> tags = new ArrayList<StatisticTag>();
        tags.add(new StatisticTag("x" + SQLStatisticConstants.NAME_SEPARATOR + "x", "someValue1"));
        tags.add(new StatisticTag("yy", "someValue2"));

        final StatisticEvent event = StatisticEvent.createCount(123L, "statName", tags, 1);

        final List<String> warnings = validator.validateEvent(event);

        Assert.assertEquals(1, warnings.size());
    }

    @Test
    public void testValidateEvent_badValue() throws Exception {
        final List<StatisticTag> tags = new ArrayList<StatisticTag>();
        tags.add(new StatisticTag("xx", "someValue1"));
        tags.add(new StatisticTag("yy", "some" + SQLStatisticConstants.NAME_SEPARATOR + "Value2"));

        final StatisticEvent event = StatisticEvent.createCount(123L, "statName", tags, 1);

        final List<String> warnings = validator.validateEvent(event);

        Assert.assertEquals(1, warnings.size());
    }

    @Test
    public void testValidateEvent_badTagsAndValues() throws Exception {
        final List<StatisticTag> tags = new ArrayList<StatisticTag>();
        tags.add(new StatisticTag("x" + SQLStatisticConstants.NAME_SEPARATOR + "x",
                "some" + SQLStatisticConstants.NAME_SEPARATOR + "Value1"));
        tags.add(new StatisticTag("y" + SQLStatisticConstants.NAME_SEPARATOR + "y",
                "some" + SQLStatisticConstants.NAME_SEPARATOR + "Value2"));

        final StatisticEvent event = StatisticEvent.createCount(123L, "statName", tags, 1);

        final List<String> warnings = validator.validateEvent(event);

        Assert.assertEquals(4, warnings.size());
    }

    @Test
    public void testCleanString_dirty() throws Exception {
        final String dirtyString = "abc" + SQLStatisticConstants.NAME_SEPARATOR + "def";

        final String cleanString = validator.cleanString(dirtyString);

        Assert.assertEquals("abc#def", cleanString);

    }

    @Test
    public void testCleanString_clean() throws Exception {
        final String stringToClean = "0123456789aAzZ .()-_$\\/[]{}_!\"'£$%^&*+-=~@";

        final String cleanString = validator.cleanString(stringToClean);

        Assert.assertEquals(stringToClean, cleanString);

    }

    @Test
    public void testIsKeyToLong_tooLong() throws Exception {
        final StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < SQLStatisticConstants.STAT_VAL_SRC_NAME_COLUMN_LENGTH + 1; i++) {
            stringBuilder.append("x");
        }

        Assert.assertTrue(SQLStatisticsEventValidator.isKeyToLong(stringBuilder.toString()));

    }

    @Test
    public void testIsKeyToLong_notTooLong() throws Exception {
        final StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("x");

        Assert.assertFalse(SQLStatisticsEventValidator.isKeyToLong(stringBuilder.toString()));
    }
}
