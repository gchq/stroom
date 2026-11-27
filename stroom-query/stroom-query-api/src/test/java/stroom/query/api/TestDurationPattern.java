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

package stroom.query.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDurationPattern {

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            // The largest unit has all that is left.
            "d.hh:mm:ss|PT49H30M5S|2.01:30:05",
            "hh:mm|PT49H30M|49:30",
            "d.hh:mm|PT49H30M|2.01:30",
            "mm:ss|PT1H2M3S|62:03",
            "s|PT1H|3600",
            "fff|PT1.5S|1500",
            "ff|PT1.5S|150",
            "f|PT1.5S|15",
            // Units that are skipped are in the next one down, so nothing is lost.
            "d mm|PT25H30M|1 90",
            "h 'h' s 's'|PT1H2M3S|1 h 123 s",
            // The order doesn't matter.
            "ss:mm:hh|PT1H2M3S|03:02:01",
            "mm:ss mm|PT1H2M3S|62:03 62",
            // Digits.
            "h:m:s|PT1H2M3S|1:2:3",
            "hh:mm:ss|PT1H2M3S|01:02:03",
            "dddd|PT48H|0002",
            "hh|PT1000H|1000",
            "s.f|PT1.987S|1.9",
            "s.ff|PT1.987S|1.98",
            "s.fff|PT1.987S|1.987",
            "s.fff|PT1.005S|1.005",
            // What is too small is left out.
            "hh:mm|PT59.999S|00:00",
            // Negatives.
            "hh:mm|PT-1H-30M|-01:30",
            "d.hh|PT-25H|-1.01",
            "d.hh|PT-24H|-1.00",
            "hh:mm|PT-59S|00:00",
            "s.f|PT-0.1S|-0.1",
            "hh:mm|PT0S|00:00",
            // Text.
            "h'h' mm'm'|PT1H2M|1h 02m",
            "'d'd|PT48H|d2",
            "d''|PT48H|2'",
            "d'o''clock'|PT48H|2o'clock",
            "d'a''''b'|PT48H|2a''b",
            "d 'x''y' ''|PT48H|2 x'y '",
            "'' d|PT48H|' 2",
            "[d] é %s|PT48H|[2] é %0"})
    void format(final String pattern, final Duration duration, final String expected) {
        assertThat(DurationPattern.parse(pattern).format(duration.toMillis())).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "d.hh:mm:ss|2.01:30:05|PT49H30M5S",
            "hh:mm:ss|26:03:04|PT26H3M4S",
            "hh:mm:ss|1:2:3|PT1H2M3S",
            "h:m:s|0001:02:03|PT1H2M3S",
            "mm:ss|90:00|PT90M",
            "ss:mm:hh|03:02:01|PT1H2M3S",
            "d mm|1 90|PT25H30M",
            "d mm|1 1439|PT47H59M",
            "s|3600|PT1H",
            "h 'h' s 's'|1 h 123 s|PT1H2M3S",
            // Only the largest can make a larger one.
            "hh:mm:ss|23:59:59|PT23H59M59S",
            "hh:mm|1:60|",
            "mm:ss|1:60|",
            "d.hh|1.24|",
            "d mm|1 1440|",
            "hh:mm|1:99999999999999999999|",
            "hh:mm|0001:00059|PT1H59M",
            "m:fff|0:9223372036854775807|",
            "m:fff|-0:9223372036854775808|",
            // Only what is in the pattern can be too long.
            "hh:mm|99999999999999999999:00|too long",
            "hh:mm|99999999999999999999:60|",
            "hh:mm|99999999999999999999 foo|",
            "hh:mm|99999999999999999999|",
            // Fractions of a second.
            "s.fff|1.987|PT1.987S",
            "s.fff|1.5|PT1.5S",
            "s.fff|1.05|PT1.05S",
            "s.f|1.987|PT1.987S",
            "s.ff|1.123456789|PT1.123S",
            "hh:mm:ss.f|0:00:01.123456|PT1.123S",
            "fff.s|987.1|PT1.987S",
            // With no seconds they are numbers of the unit.
            "fff|1500|PT1.5S",
            "ff|150|PT1.5S",
            "f|15|PT1.5S",
            "m:fff|1:5|PT1M0.005S",
            "m:fff|1:59999|PT1M59.999S",
            "m:fff|1:60000|",
            // Units that are together.
            "hhmmss|010203|PT1H2M3S",
            "hhmmss|1230203|PT123H2M3S",
            "hhmmss|10203|PT1H2M3S",
            "hhmmss|0203|",
            "hhmmss|203|",
            "ssfff|01987|PT1.987S",
            // With a fraction there must be as many digits as letters, as more could be of either end.
            "ssfff|1987|",
            "ssfff|01987654|",
            "ssfff|12987654|",
            "mm:ssfff|02:039871|",
            "hhmmssfff|010203456|PT1H2M3.456S",
            "hhmmssfff|010203123456|",
            "hhmmssfff|1230203456|",
            "hhmmssfff|10203456|",
            "mmfffss|0298703|PT2M3.987S",
            "mmfffss|10298703|",
            "m:fffss|1:98702|PT1M2.987S",
            "m:fffss|1:1234|",
            "m:fffss|1:512|",
            "m:fffss|1:1234502|",
            "m:fff|1:02|PT1M0.002S",
            "m:fff:ss|1:9876:2|PT1M2.987S",
            "m:fff:ss|1:9:2|PT1M2.9S",
            "ss:fff|1:987654|PT1.987S",
            "hhmm ss.fff|12302 3.987654|PT123H2M3.987S",
            "hhmm:ss|0102:03|PT1H2M3S",
            "mm:ssfff|02:03987|PT2M3.987S",
            "mm:ssfff|02:3987|",
            "dhh|101|PT25H",
            "hhmm|0175|",
            // Signs.
            "hh:mm|-01:30|PT-1H-30M",
            "hh:mm|+01:30|PT1H30M",
            "s.f|-0.1|PT-0.1S",
            "s.fff|-1.5|PT-1.5S",
            "hh:mm|-00:00|PT0S",
            "hh:mm|-1:60|",
            "hh:mm|--01:30|",
            "hh:mm|- 01:30|",
            "hh:mm|01:-30|",
            "-hh|-01|PT1H",
            "-hh|--01|PT-1H",
            "-hh|01|",
            "hh|-|",
            // Text.
            "h'h' mm'm'|1h 02m|PT1H2M",
            "'5'h|51|PT1H",
            "d'o''clock'|2o'clock|PT48H",
            "hh:mm|01.30|",
            "hh:mm|01:30:00|",
            "hh:mm| 01:30|",
            "hh:mm|01:30 |",
            "hh:mm|01:|",
            "hh:mm|:30|",
            "hh:mm|01|",
            "hh:mm|soon|",
            "hh:mm|٠١:30|",
            "hh:mm|1.5:30|"}, ignoreLeadingAndTrailingWhitespace = false)
    void read(final String pattern, final String text, final String expected) {
        final DurationPattern durationPattern = DurationPattern.parse(pattern);
        durationPattern.checkCanRead();
        if ("too long".equals(expected)) {
            assertThatThrownBy(() -> durationPattern.read(text)).isInstanceOf(ArithmeticException.class);
        } else {
            assertThat(durationPattern.read(text)).isEqualTo(expected == null
                    ? null
                    : (Long) Duration.parse(expected).toMillis());
        }
    }

    @Test
    void readEmpty() {
        assertThat(DurationPattern.parse("hh:mm").read("")).isNull();
    }

    // Whatever is written can be read.
    @Test
    void readWhatIsWritten() {
        for (final String pattern : List.of("d.hh:mm:ss.fff", "hh:mm:ss.fff", "mm:ss.fff", "s.fff", "fff", "d fff",
                "d m fff", "dhhmmssfff", "hhmmssfff", "fff:ss:mm:hh", "m's'ss,fff", "m:fffss", "''-d hh:mm:ss.fff")) {
            final DurationPattern durationPattern = DurationPattern.parse(pattern);
            durationPattern.checkCanRead();
            for (final long millis : new long[]{0, 1, 10, 100, 999, 1_000, 59_999, 60_000, 3_599_999, 3_600_000,
                    86_399_999, 86_400_000, 90_061_001, 123_456_789_012L, Long.MAX_VALUE, Long.MIN_VALUE}) {
                final boolean isTooLong = (pattern.equals("dhhmmssfff") && Math.abs(millis / 86_400_000) > 9)
                                          || (pattern.equals("hhmmssfff") && Math.abs(millis / 3_600_000) > 99);
                if (isTooLong) {
                    assertThat(durationPattern.read(durationPattern.format(millis))).as(pattern).isNull();
                    continue;
                }
                assertThat(durationPattern.read(durationPattern.format(millis))).as(pattern).isEqualTo(millis);
                assertThat(durationPattern.read(durationPattern.format(-millis))).as(pattern).isEqualTo(-millis);
            }
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "fff|9223372036854775808",
            "fff|-9223372036854775809",
            "d|106751991168",
            "d|-106751991168",
            "d.hh:mm:ss.fff|106751991167.07:12:55.808",
            "d.hh:mm:ss.fff|-106751991167.07:12:55.809"})
    void readTooLong(final String pattern, final String text) {
        assertThatThrownBy(() -> DurationPattern.parse(pattern).read(text)).isInstanceOf(ArithmeticException.class);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "d|86400000",
            "dddd|86400000",
            "hh|3600000",
            "m|60000",
            "s|1000",
            "f|100",
            "fff|1",
            "h:mm|0",
            "hmm|0",
            "h'h'|0",
            "-h|0"})
    void onlyUnit(final String pattern, final long expected) {
        assertThat(DurationPattern.parse(pattern).getOnlyUnitMillis()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "mm:ss mm|has a unit more than once",
            "h:hh|has a unit more than once",
            "s.f fff|has a unit more than once",
            "f ff|has a unit more than once",
            "hmm:ss m|has a unit more than once",
            "dmm|has units together that can't be told apart",
            "fffss|has units together that can't be told apart",
            "mmhh|has units together that can't be told apart",
            "hm|has units together that can't be told apart",
            "hhmms|has units together that can't be told apart",
            "sf:hhm|has units together that can't be told apart",
            "mfff|has units together that can't be told apart",
            "h'0'm|has a digit straight after a unit",
            "h0m|has a digit straight after a unit"})
    void patternsThatCantBeRead(final String pattern, final String message) {
        final DurationPattern durationPattern = DurationPattern.parse(pattern);
        assertThatThrownBy(durationPattern::checkCanRead)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The duration pattern \"" + pattern + "\" " + message
                            + ", so it can't be used to read a duration");
    }

    @Test
    void limits() {
        assertThat(DurationPattern.parse("fff").format(Long.MIN_VALUE)).isEqualTo("-9223372036854775808");
        assertThat(DurationPattern.parse("fff").format(Long.MAX_VALUE)).isEqualTo("9223372036854775807");
        assertThat(DurationPattern.parse("d.hh:mm:ss.fff").format(Long.MIN_VALUE))
                .isEqualTo("-106751991167.07:12:55.808");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "HH:mm|has a letter 'H' that is not a unit",
            "dM|has a letter 'M' that is not a unit",
            "hhh|has too many of 'h' together",
            "mmm|has too many of 'm' together",
            "sss|has too many of 's' together",
            "ffff|has too many of 'f' together",
            "ddddddddd|has too many of 'd' together",
            "'hours'|has no units",
            "::|has no units",
            "''|has no units",
            "h 'h|has a quote with no end",
            "h 'h''|has a quote with no end",
            "h '|has a quote with no end"})
    void mistakes(final String pattern, final String message) {
        assertThatThrownBy(() -> DurationPattern.parse(pattern))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The duration pattern \"" + pattern + "\" " + message);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void blank(final String pattern) {
        assertThatThrownBy(() -> DurationPattern.parse(pattern))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A duration pattern is needed, such as d.hh:mm:ss");
    }

    @Test
    void noPattern() {
        assertThatThrownBy(() -> DurationPattern.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
