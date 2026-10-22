/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl.tools.sample;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestTimePatterns {

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', nullValues = "None", value = {
            "2026-09-28T10:00:00.000Z|yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "2026-09-28T10:00:00+01:00|yyyy-MM-dd'T'HH:mm:ssXXX",
            "2026-09-28 10:00:00,123|yyyy-MM-dd HH:mm:ss,SSS",
            "28/Sep/2026:10:00:00 +0000|dd/MMM/yyyy:HH:mm:ss Z",
            "Sep  8 10:00:00|MMM  d HH:mm:ss",
            "Oct  1 10:00:00 2026|MMM  d HH:mm:ss yyyy",
            "10/01/2026 10:00:00 PM|MM/dd/yyyy hh:mm:ss a",
            "20261001T100000Z|yyyyMMdd'T'HHmmssX",
            "Wed, 01 Oct 2026 10:00:00 GMT|EEE, dd MMM yyyy HH:mm:ss z",
            "1 Oct 2026 10:00:00|d MMM yyyy HH:mm:ss",
            "1790600000|epoch seconds",
            "alice|None",
            "10.0.0.1|None"})
    void timePatternsAreInferredAndFitTheirValues(final String value, final String pattern) {
        assertThat(TimePatterns.inferTimePattern(value)).isEqualTo(pattern);
        if (pattern != null) {
            assertThat(TimePatterns.checkTimeFormat(pattern, List.of(value))).isNull();
        }
    }

    @Test
    void aWrongTimeFormatIsNamedWithTheShapeTheValuesHave() {
        final String message = TimePatterns.checkTimeFormat("yyyy-MM-dd HH:mm:ss", List.of("2026-10-01T10:00:00Z"));

        assertThat(message).contains("does not match the sample's values").contains("yyyy-MM-dd'T'HH:mm:ssX")
                .isEqualTo("time_format 'yyyy-MM-dd HH:mm:ss' does not match the sample's values, e.g. "
                           + "['2026-10-01T10:00:00Z']; the values look like \"yyyy-MM-dd'T'HH:mm:ssX\"");
        assertThat(TimePatterns.patternRegex("yyyy-MM-dd'T'HH:mm:ss").matcher("2026-10-01T10:00:00").lookingAt())
                .isTrue();
        assertThat(TimePatterns.checkTimeFormat("epoch_ms", List.of("1790600000123"))).isNull();
    }

    @Test
    void epochSecondsWithAFractionAreATime() {
        // Seen with a Bluecoat log: 1791712800.123 was a plain decimal, so the time was mapped by hand without its
        // milliseconds.
        assertThat(TimePatterns.inferTimePattern("1791712800.123")).isEqualTo("epoch seconds");
        assertThat(TimePatterns.inferTimePattern("1791712800.123456")).isEqualTo("epoch seconds");
        assertThat(TimePatterns.checkTimeFormat("epoch seconds", List.of("1791712800.123"))).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "2026-10-01T18:00:00.123+10:00|yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "2026-10-01T08:00:00.123Z|yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "2026-10-01 18:00:00.123 +1000|yyyy-MM-dd HH:mm:ss.SSS Z",
            "2026-10-01 18:00:00 AEST|yyyy-MM-dd HH:mm:ss z",
            "2026-10-01T18:00:00.123 Australia/Sydney|yyyy-MM-dd'T'HH:mm:ss.SSS VV",
            "2026-10-01T18:00:00.123+10:00[Australia/Sydney]|yyyy-MM-dd'T'HH:mm:ss.SSSXXX'['VV']'"})
    void offsetsAndZonesAreReadIntoThePatternSoTheTimeConvertsToUtc(final String value, final String pattern) {
        assertThat(TimePatterns.inferTimePattern(value)).isEqualTo(pattern);
        assertThat(TimePatterns.checkTimeFormat(pattern, List.of(value))).isNull();
    }

    @Test
    void epochMicrosecondsAndNanosecondsAreTimesToo() {
        // Asked for by the user: microseconds, rounded to the event-logging schema's milliseconds.
        assertThat(TimePatterns.inferTimePattern("1790841600123456")).isEqualTo("epoch microseconds");
        assertThat(TimePatterns.inferTimePattern("1790841600123456789")).isEqualTo("epoch nanoseconds");
        assertThat(TimePatterns.checkTimeFormat("epoch_us", List.of("1790841600123456"))).isNull();
        assertThat(TimePatterns.checkTimeFormat("epoch_ns", List.of("1790841600123"))).isNotNull();
    }

    @Test
    void aPatternThisCheckDoesNotUnderstandIsSaidSo() {
        assertThat(TimePatterns.patternRegex("yyyy-MM-dd'T")).isNull();
        assertThat(TimePatterns.checkTimeFormat("yyyy-MM-dd'T", List.of("2026-10-01")))
                .isEqualTo("time_format \"yyyy-MM-dd'T\" is not a Java date pattern this check understands");
        assertThat(TimePatterns.checkTimeFormat("epoch_s", List.of("yesterday")))
                .isEqualTo("time_format 'epoch_s' expects digits only (a fraction of a second allowed); the sample "
                           + "has ['yesterday']");
    }
}
