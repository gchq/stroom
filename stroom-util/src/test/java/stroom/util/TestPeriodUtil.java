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

package stroom.util;


import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestPeriodUtil {

    private static final int N2000 = 2000;
    private static final int N2001 = 2001;
    private static final int N2002 = 2002;
    private static final int N1 = 1;
    private static final int N12 = 12;
    private static final int N31 = 31;

    @Test
    void test1() {
        assertThat(PeriodUtil.createYearPeriod(N2001).contains(PeriodUtil.createDate(N2001, N1, N1))).isTrue();
        assertThat(PeriodUtil.createYearPeriod(N2001).contains(PeriodUtil.createDate(N2001, N12, N31))).isTrue();
        assertThat(PeriodUtil.createYearPeriod(N2001).contains(PeriodUtil.createDate(N2000, N12, N31))).isFalse();
        assertThat(PeriodUtil.createYearPeriod(N2001).contains(PeriodUtil.createDate(N2002, N1, N1))).isFalse();
    }

    @Test
    void testGetPrecision() {
        assertThat(PeriodUtil.getPrecision(new Period(0L, 100L).duration(), 100)).isEqualTo(0);
        assertThat(PeriodUtil.getPrecision(new Period(0L, 100L).duration(), 10)).isEqualTo(1);
    }
}
