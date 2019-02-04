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

package stroom.util.date;


import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestDateUtil {
    @Test
    void testSimpleZuluTimes() {
        doTest("2008-11-18T09:47:50.548Z");
        doTest("2008-11-18T09:47:00.000Z");
        doTest("2008-11-18T13:47:00.000Z");
        doTest("2008-01-01T13:47:00.000Z");
        doTest("2008-08-01T13:47:00.000Z");
    }

    private void doTest(final String dateString) {
        final long date = DateUtil.parseNormalDateTimeString(dateString);

        // Convert Back to string
        assertThat(DateUtil.createNormalDateTimeString(date)).isEqualTo(dateString);
    }

    @Test
    void testSimple() {
        assertThat(DateUtil.createNormalDateTimeString(DateUtil.parseNormalDateTimeString("2010-01-01T23:59:59.000Z"))).isEqualTo("2010-01-01T23:59:59.000Z");

    }

    @Test
    void testSimpleFileFormat() {
        assertThat(DateUtil.createFileDateTimeString(DateUtil.parseNormalDateTimeString("2010-01-01T23:59:59.000Z"))).isEqualTo("2010-01-01T23#59#59,000Z");
    }
}
