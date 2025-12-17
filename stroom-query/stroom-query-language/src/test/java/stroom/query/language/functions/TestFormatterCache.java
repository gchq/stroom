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

package stroom.query.language.functions;

import stroom.query.language.functions.FormatterCache.Mode;
import stroom.test.common.TestUtil;
import stroom.util.date.DateUtil;

import io.vavr.Tuple;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestFormatterCache {

    @TestFactory
    Stream<DynamicTest> testParse() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(
                        String.class,
                        String.class,
                        String.class)
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    final String dateStr = testCase.getInput()._1;
                    final String pattern = testCase.getInput()._2;
                    final String timeZone = testCase.getInput()._3;
                    final long epochMs = FormatterCache.parse(dateStr, pattern, timeZone);

                    // Make sure FormatterCache is dishing out same instances each time
                    final DateTimeFormatter formatter1 = FormatterCache.getFormatter(pattern, Mode.PARSE);
                    final DateTimeFormatter formatter2 = FormatterCache.getFormatter(pattern, Mode.PARSE);
                    assertThat(formatter2)
                            .isSameAs(formatter1);
                    final ZoneId zoneId1 = FormatterCache.getZoneId(timeZone);
                    final ZoneId zoneId2 = FormatterCache.getZoneId(timeZone);
                    assertThat(zoneId2)
                            .isSameAs(zoneId1);

                    return DateUtil.createNormalDateTimeString(epochMs);
                })
                .withSimpleEqualityAssertion()
                // assumes default parser format in UTC
                .addCase(Tuple.of("2010-01-01T23:59:59.123Z", null, null), "2010-01-01T23:59:59.123Z")
                .addCase(Tuple.of("2010-01-01T23:59:59.1+02:00", null, null), "2010-01-01T21:59:59.100Z")
                .addCase(Tuple.of("2010-01-01", "yyyy-MM-dd", null), "2010-01-01T00:00:00.000Z")
                // Use timezone arg
                .addCase(
                        Tuple.of("2010-01-01T14:15:16.123", "yyyy-MM-dd'T'HH:mm:ss.SSS", "+0200"),
                        "2010-01-01T12:15:16.123Z")
                // Timezone arg ignored as dateStr has a timezone
                .addCase(
                        Tuple.of("2010-01-01T14:15:16.123Z", "yyyy-MM-dd'T'HH:mm:ss.SSSXX", "+0200"),
                        "2010-01-01T14:15:16.123Z")
                .build();
    }
}
