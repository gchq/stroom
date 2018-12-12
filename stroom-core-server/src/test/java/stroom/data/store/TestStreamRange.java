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

package stroom.data.store;

import org.junit.jupiter.api.Disabled;
import stroom.util.test.StroomUnitTest;


@Disabled
class TestStreamRange extends StroomUnitTest {
//    @Test
//    public void testParseNothing() {
//        final StreamRange streamRange = new StreamRange("");
//        assertThat(streamRange.isFileLocation()).isFalse();
//    }
//
//    @Test
//    public void testParseEvents() {
//        final StreamRange streamRange = new StreamRange("EVENTS");
//        assertThat(streamRange.isFileLocation()).isFalse();
//    }
//
//    @Test
//    public void testParseShortDate() {
//        final StreamRange streamRange = new StreamRange("EVENTS/2011/01");
//        assertThat(streamRange.isFileLocation()).isFalse();
//    }
//
//    @Test
//    public void testParseSmallRange() {
//        final StreamRange streamRange = new StreamRange("EVENTS/2011/01/01");
//        assertThat(streamRange.isFileLocation()).isTrue();
//        assertThat(streamRange.isInvalidPath()).isFalse();
//        assertThat(streamRange.getFrom().longValue()).isEqualTo(1L);
//        assertThat(streamRange.getTo().longValue()).isEqualTo(1000L);
//        assertThat(streamRange.getCreatePeriod().getFrom()
//                .equals(DateUtil.parseNormalDateTimeString("2011-01-01T00:00:00.000Z"))).isTrue();
//        assertThat(streamRange.getCreatePeriod().getTo()
//                .equals(DateUtil.parseNormalDateTimeString("2011-01-02T00:00:00.000Z"))).isTrue();
//    }
//
//    @Test
//    public void testParseBigRange() {
//        final StreamRange streamRange = new StreamRange("EVENTS/2011/01/01/101/500");
//        assertThat(streamRange.isFileLocation()).isTrue();
//        assertThat(streamRange.isInvalidPath()).isFalse();
//        assertThat(streamRange.getFrom().longValue()).isEqualTo(101500000L);
//        assertThat(streamRange.getTo().longValue()).isEqualTo(101501000L);
//        assertThat(streamRange.getCreatePeriod().getFrom()
//                .equals(DateUtil.parseNormalDateTimeString("2011-01-01T00:00:00.000Z"))).isTrue();
//        assertThat(streamRange.getCreatePeriod().getTo()
//                .equals(DateUtil.parseNormalDateTimeString("2011-01-02T00:00:00.000Z"))).isTrue();
//    }
//
//    @Test
//    public void testParseBigRangeOddFolder() {
//        final StreamRange streamRange1 = new StreamRange("EVENTS/2011/01/01/101/500/X");
//        assertThat(streamRange1.isInvalidPath()).isTrue();
//        final StreamRange streamRange2 = new StreamRange("EVENTS/X/X/X");
//        assertThat(streamRange2.isInvalidPath()).isTrue();
//    }
}
