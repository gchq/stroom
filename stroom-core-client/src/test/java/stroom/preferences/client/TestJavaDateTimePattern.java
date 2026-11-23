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

package stroom.preferences.client;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class TestJavaDateTimePattern {

    // What a random pattern is made of: letters, the characters with a meaning and a literal
    private static final String RANDOM_CHARS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ'[]{}#- ";
    // Dates that between them make every field as wide as it can be, in a zone whose offset had seconds
    private static final List<ZonedDateTime> WIDEST_DATES = List.of(
            LocalDateTime.of(1971, 9, 15, 14, 59, 59, 999_999_999).atZone(ZoneId.of("Africa/Monrovia")),
            LocalDateTime.of(9998, 12, 29, 23, 59, 59, 999_999_999).atZone(ZoneId.of("Europe/London")));
    // Padding and the letters that make numbers, with a literal, a quote and an optional section,
    // so that numbers often follow each other
    private static final String NUMBER_CHARS = "ppppyuYwMLQqdhHkKmsecWSDAnNFgEa-'[]";

    // TimePreferences.STANDARD_FORMATS, which can't be read here as TimePreferences is a GWT widget
    private static final List<String> STANDARD_FORMATS = List.of(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS xx",
            "yyyy-MM-dd'T'HH:mm:ss.SSS xxx",
            "yyyy-MM-dd'T'HH:mm:ss.SSS VV",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "dd/MM/yyyy HH:mm:ss",
            "dd/MM/yy HH:mm:ss",
            "MM/dd/yyyy HH:mm:ss",
            "d MMM yyyy HH:mm:ss",
            "yyyy-MM-dd",
            "dd/MM/yyyy",
            "dd/MM/yy",
            "MM/dd/yyyy",
            "d MMM yyyy");

    /// Every standard format offered can be used.
    @Test
    void standardFormats() {
        for (final String format : STANDARD_FORMATS) {
            assertThat(JavaDateTimePattern.invalidReason(format)).as(format).isNull();
        }
    }

    /// A blank or missing pattern can't be used.
    @Test
    void blank() {
        assertThat(JavaDateTimePattern.invalidReason(null)).isEqualTo("Enter a format");
        assertThat(JavaDateTimePattern.invalidReason("")).isEqualTo("Enter a format");
        assertThat(JavaDateTimePattern.invalidReason("  ")).isEqualTo("Enter a format");
    }

    /// A broken pattern says why.
    @Test
    void reasons() {
        assertThat(JavaDateTimePattern.invalidReason("dd/bb/yyyy")).isEqualTo("Unknown pattern letter 'b'");
        assertThat(JavaDateTimePattern.invalidReason("ddd")).isEqualTo("Too many 'd' letters (2 at most)");
        assertThat(JavaDateTimePattern.invalidReason("yyyy'T")).isEqualTo("A quote isn't closed");
        assertThat(JavaDateTimePattern.invalidReason("yyyy]")).isEqualTo("']' has no '[' before it");
        assertThat(JavaDateTimePattern.invalidReason("yyyy#")).isEqualTo("'#' is reserved");
        assertThat(JavaDateTimePattern.invalidReason("V")).isEqualTo("'V' must be 'VV'");
        assertThat(JavaDateTimePattern.invalidReason("p")).isEqualTo("'p' must be followed by pattern letters");
        assertThat(JavaDateTimePattern.invalidReason("ppddHH"))
                .isEqualTo("A padded number can't be followed straight by another");
        assertThat(JavaDateTimePattern.invalidReason("dppddH"))
                .isEqualTo("A padded number can't be followed straight by another");
    }

    /// Padding narrower than its field can be is refused, as Java fails on it as it formats a date.
    @Test
    void paddingTooNarrow() {
        assertThat(JavaDateTimePattern.invalidReason("pdd/MM/yyyy"))
                .isEqualTo("Too few 'p' letters to pad 'd' (2 at least)");
        assertThat(JavaDateTimePattern.invalidReason("pd")).isEqualTo("Too few 'p' letters to pad 'd' (2 at least)");
        assertThat(JavaDateTimePattern.invalidReason("ppyyyy"))
                .isEqualTo("Too few 'p' letters to pad 'y' (4 at least)");
        assertThat(JavaDateTimePattern.invalidReason("ppppppppMMMM"))
                .isEqualTo("Too few 'p' letters to pad 'M' (9 at least)");
        assertThat(JavaDateTimePattern.invalidReason("ppppppppppppVV"))
                .isEqualTo("Too few 'p' letters to pad 'V' (32 at least)");
    }

    /// A time zone's id or name can be padded to the width said, which no zone's is wider than, and
    /// no less.
    @Test
    void paddedTimeZones() {
        for (final String zone : List.of("VV", "z", "zz", "zzz", "zzzz", "v", "vvvv")) {
            final String tooFew = JavaDateTimePattern.invalidReason("p" + zone);
            assertThat(tooFew).startsWith("Too few 'p' letters");
            final int widest = Integer.parseInt(tooFew.replaceAll("\\D", ""));
            assertThat(JavaDateTimePattern.invalidReason("p".repeat(widest - 1) + zone)).isEqualTo(tooFew);
            final String padded = "p".repeat(widest) + zone;
            assertThat(JavaDateTimePattern.invalidReason(padded)).isNull();
            final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(padded, Locale.ENGLISH);
            for (final String id : ZoneId.getAvailableZoneIds()) {
                for (final int year : List.of(1970, 2000, 2026)) {
                    for (int month = 1; month <= 12; month++) {
                        assertThat(formatter.format(LocalDateTime.of(year, month, 15, 12, 0).atZone(ZoneId.of(id))))
                                .as(id)
                                .hasSize(widest);
                    }
                }
            }
        }
    }

    /// Quotes, optional sections and padding that Java accepts are accepted.
    @Test
    void literalsSectionsAndPadding() {
        assertThat(JavaDateTimePattern.invalidReason("yyyy-MM-dd'T'HH:mm")).isNull();
        assertThat(JavaDateTimePattern.invalidReason("'It''s' yyyy")).isNull();
        assertThat(JavaDateTimePattern.invalidReason("yyyy[-MM[-dd]]")).isNull();
        assertThat(JavaDateTimePattern.invalidReason("yyyy[-MM")).isNull();
        assertThat(JavaDateTimePattern.invalidReason("ppd")).isNull();
        assertThat(JavaDateTimePattern.invalidReason("ddppHH")).isNull();
        assertThat(JavaDateTimePattern.invalidReason("ppdd-HH")).isNull();
    }

    /// Agrees with `java.time` for every letter, one to twenty-one of it, with and without padding
    /// of one to twenty-one.
    @Test
    void agreesWithJavaForEveryLetterAndCount() {
        for (char letter = 'A'; letter <= 'z'; letter++) {
            if (!Character.isLetter(letter)) {
                continue;
            }
            for (int count = 1; count <= 21; count++) {
                final String pattern = String.valueOf(letter).repeat(count);
                assertAgrees(pattern);
                for (int padding = 1; padding <= 21; padding++) {
                    assertAgrees("p".repeat(padding) + pattern);
                }
            }
        }
    }

    /// Agrees with `java.time` for random patterns of letters, quotes, brackets and the reserved
    /// characters.
    @Test
    void agreesWithJavaForRandomPatterns() {
        assertAgreesForRandom(RANDOM_CHARS, 200_000, 42);
    }

    /// Agrees with `java.time` for random patterns of padding and numbers, which Java joins when
    /// one follows another.
    @Test
    void agreesWithJavaForRandomNumbers() {
        assertAgreesForRandom(NUMBER_CHARS, 200_000, 7);
    }

    private static void assertAgreesForRandom(final String chars, final int patterns, final long seed) {
        final Random random = new Random(seed);
        for (int i = 0; i < patterns; i++) {
            final int length = 1 + random.nextInt(10);
            final StringBuilder pattern = new StringBuilder();
            for (int j = 0; j < length; j++) {
                pattern.append(chars.charAt(random.nextInt(chars.length())));
            }
            assertAgrees(pattern.toString());
        }
    }

    private static void assertAgrees(final String pattern) {
        final String reason = JavaDateTimePattern.invalidReason(pattern);
        // Java pads the time zone of these dates to less than other zones need (see paddedTimeZones)
        if (pattern.trim().isEmpty()
            || (reason != null && reason.matches("Too few 'p' letters to pad '[zvV]'.*"))) {
            return;
        }
        assertThat(reason == null)
                .as(pattern)
                .isEqualTo(javaAccepts(pattern));
    }

    // Whether Java makes a formatter of the pattern that can format dates. It fails on some patterns
    // with other than an IllegalArgumentException, e.g. a ClassCastException on a padded number
    // followed straight by another
    private static boolean javaAccepts(final String pattern) {
        try {
            final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH);
            WIDEST_DATES.forEach(formatter::format);
            return true;
        } catch (final RuntimeException e) {
            return false;
        }
    }
}
