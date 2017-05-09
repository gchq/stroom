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

import org.junit.Assert;
import org.junit.Test;
import stroom.statistics.sql.SQLStatisticsEventValidator;

public class TestStatisticsEventValidatorFactory {
    @Test
    public void testGetInstance_SQL() throws Exception {
        final StatisticsEventValidator validator = StatisticsEventValidatorFactory.getInstance("sql");

        Assert.assertTrue(validator instanceof SQLStatisticsEventValidator);
    }

    @Test
    public void testGetInstance_SQLMixedCase() throws Exception {
        final StatisticsEventValidator validator = StatisticsEventValidatorFactory.getInstance("sQl");

        Assert.assertTrue(validator instanceof SQLStatisticsEventValidator);
    }

    @Test
    public void testGetInstance_multipleSQLCalls() throws Exception {
        final StatisticsEventValidator validator1 = StatisticsEventValidatorFactory.getInstance("Sql");
        final StatisticsEventValidator validator2 = StatisticsEventValidatorFactory.getInstance("sQL");

        // same object instance
        Assert.assertTrue(validator1 == validator2);
    }

    @Test(expected = NullPointerException.class)
    public void testGetInstance_null() throws Exception {
        StatisticsEventValidatorFactory.getInstance(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetInstance_badEngineName() throws Exception {
        StatisticsEventValidatorFactory.getInstance("foobar");
    }
}
