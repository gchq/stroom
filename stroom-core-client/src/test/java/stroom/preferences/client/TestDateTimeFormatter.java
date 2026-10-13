/*
 * Copyright 2023 Crown Copyright
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

public class TestDateTimeFormatter {
    @Test
    void test() {
        final DateTimeFormatter dateTimeFormatter = new DateTimeFormatter(null);
        assertThat(dateTimeFormatter
                .convertJavaDateTimePattern("yyyy-MM-dd'T'HH:mm:ss.SSSXX"))
                .isEqualTo("YYYY-MM-DD[T]HH:mm:ss.SSSZ");
        assertThat(dateTimeFormatter
                .convertJavaDateTimePattern("yyyy-MM-dd'T'HH'#'mm'#'ss,SSSXX"))
                .isEqualTo("YYYY-MM-DD[T]HH#mm#ss,SSSZ");
        assertThat(dateTimeFormatter
                .convertJavaDateTimePattern("E, dd MMM yyyy HH:mm:ss Z"))
                .isEqualTo("ddd, DD MMM YYYY HH:mm:ss Z");
    }

    // ------------------------------------------------------------------------
    // toCompactPattern
    //
    // Compacting drops sub-minute precision and the zone token, and changes
    // nothing else. It must derive from the user's own pattern rather than
    // replace it: UserPreferences.dateTimePattern is the only date-localisation
    // control Stroom has - there is no locale detection anywhere - so imposing a
    // fixed pattern would silently override a user's explicit choice of field
    // order, separators and 12- or 24-hour clock.
    // ------------------------------------------------------------------------

    /**
     * The realistic range of user preferences, given as the Java pattern the user
     * actually sets, run through the same conversion the formatter uses.
     */
    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            // Stroom's default: ISO with millis and offset.
            "yyyy-MM-dd'T'HH:mm:ss.SSSXX          | YYYY-MM-DD[T]HH:mm",
            // Common European / UK day-first forms.
            "dd/MM/yyyy HH:mm:ss                  | DD/MM/YYYY HH:mm",
            "dd-MM-yyyy HH:mm:ss                  | DD-MM-YYYY HH:mm",
            "dd.MM.yyyy HH:mm:ss                  | DD.MM.YYYY HH:mm",
            // US month-first, 12-hour with meridiem - the meridiem must survive.
            "MM/dd/yyyy hh:mm:ss a                | MM/DD/YYYY hh:mm a",
            "M/d/yyyy h:mm:ss a                   | M/D/YYYY h:mm a",
            // ISO-ish without the T literal.
            "yyyy-MM-dd HH:mm:ss                  | YYYY-MM-DD HH:mm",
            "yyyy/MM/dd HH:mm:ss                  | YYYY/MM/DD HH:mm",
            // Already compact - nothing to strip, nothing changed.
            "dd/MM/yyyy HH:mm                     | DD/MM/YYYY HH:mm",
            "yyyy-MM-dd HH:mm                     | YYYY-MM-DD HH:mm",
            "h:mm a                               | h:mm a",
            // Date only.
            "yyyy-MM-dd                           | YYYY-MM-DD",
            "dd/MM/yyyy                           | DD/MM/YYYY",
            // Zone variants: offset, abbreviation and zone id all go.
            "yyyy-MM-dd HH:mm:ss xxx              | YYYY-MM-DD HH:mm",
            "yyyy-MM-dd HH:mm:ss xx               | YYYY-MM-DD HH:mm",
            "yyyy-MM-dd HH:mm:ss VV               | YYYY-MM-DD HH:mm",
            "yyyy-MM-dd HH:mm:ss Z                | YYYY-MM-DD HH:mm",
            // Millis without a zone, and millis with a comma separator.
            "HH:mm:ss.SSS                         | HH:mm",
            "yyyy-MM-dd'T'HH'#'mm'#'ss,SSSXX      | YYYY-MM-DD[T]HH#mm",
            // Day and month names are the user's business - only precision goes.
            "E, dd MMM yyyy HH:mm:ss Z            | ddd, DD MMM YYYY HH:mm",
            "EEEE, dd MMMM yyyy HH:mm:ss          | dddd, DD MMMM YYYY HH:mm",
            // Seconds with an unusual separator.
            "yyyy-MM-dd HH:mm-ss                  | YYYY-MM-DD HH:mm",
    })
    void testToCompactPattern_userPatterns(final String javaPattern, final String expected) {
        final DateTimeFormatter formatter = new DateTimeFormatter(null);
        final String moment = formatter.convertJavaDateTimePattern(javaPattern.trim());

        assertThat(formatter.toCompactPattern(moment))
                .as("compacting " + javaPattern.trim() + " (moment: " + moment + ")")
                .isEqualTo(expected.trim());
    }

    /** Compacting is idempotent — a compacted pattern compacts to itself. */
    @ParameterizedTest
    @ValueSource(strings = {
            "yyyy-MM-dd'T'HH:mm:ss.SSSXX",
            "dd/MM/yyyy HH:mm:ss",
            "MM/dd/yyyy hh:mm:ss a",
            "EEEE, dd MMMM yyyy HH:mm:ss",
            "yyyy-MM-dd",
    })
    void testToCompactPattern_isIdempotent(final String javaPattern) {
        final DateTimeFormatter formatter = new DateTimeFormatter(null);
        final String once = formatter.toCompactPattern(
                formatter.convertJavaDateTimePattern(javaPattern));

        assertThat(formatter.toCompactPattern(once)).isEqualTo(once);
    }

    /** Whatever the pattern, the result never keeps seconds, millis or a zone token. */
    @ParameterizedTest
    @ValueSource(strings = {
            "yyyy-MM-dd'T'HH:mm:ss.SSSXX",
            "dd/MM/yyyy HH:mm:ss",
            "MM/dd/yyyy hh:mm:ss a",
            "yyyy-MM-dd HH:mm:ss xxx",
            "E, dd MMM yyyy HH:mm:ss Z",
            "HH:mm:ss.SSS",
    })
    void testToCompactPattern_dropsPrecisionAndZone(final String javaPattern) {
        final DateTimeFormatter formatter = new DateTimeFormatter(null);
        final String compact = formatter.toCompactPattern(
                formatter.convertJavaDateTimePattern(javaPattern));

        assertThat(compact).as("seconds").doesNotContain("ss");
        assertThat(compact).as("fractional seconds").doesNotContain("S");
        assertThat(compact).as("zone").doesNotContain("Z").doesNotContain("z");
        assertThat(compact).as("minutes must survive").contains("mm");
    }

    /**
     * A bracketed literal is never mistaken for a token, even when it contains
     * pattern letters.
     */
    @Test
    void testToCompactPattern_leavesBracketedLiteralsAlone() {
        final DateTimeFormatter formatter = new DateTimeFormatter(null);

        assertThat(formatter.toCompactPattern("YYYY-MM-DD[T]HH:mm:ss"))
                .isEqualTo("YYYY-MM-DD[T]HH:mm");
        assertThat(formatter.toCompactPattern("HH:mm:ss [ss]"))
                .as("a literal 'ss' must survive while the token is removed")
                .isEqualTo("HH:mm [ss]");
        assertThat(formatter.toCompactPattern("HH:mm [at] SSS"))
                .as("a literal containing pattern letters is untouched")
                .isEqualTo("HH:mm [at]");
    }

    /**
     * A pattern with nothing left after stripping is returned unchanged rather than
     * emptied — better a too-precise label than a blank one.
     */
    @Test
    void testToCompactPattern_unshortenablePatternIsReturnedAsIs() {
        final DateTimeFormatter formatter = new DateTimeFormatter(null);

        assertThat(formatter.toCompactPattern("ss")).isEqualTo("ss");
        assertThat(formatter.toCompactPattern("ss.SSS")).isEqualTo("ss.SSS");
        assertThat(formatter.toCompactPattern("Z")).isEqualTo("Z");
    }

    @Test
    void testToCompactPattern_nullIsNull() {
        assertThat(new DateTimeFormatter(null).toCompactPattern(null)).isNull();
    }

    /** No stray separators or double spaces are left behind. */
    @ParameterizedTest
    @ValueSource(strings = {
            "yyyy-MM-dd'T'HH:mm:ss.SSSXX",
            "dd/MM/yyyy HH:mm:ss",
            "E, dd MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd HH:mm:ss VV",
    })
    void testToCompactPattern_leavesNoDanglingSeparators(final String javaPattern) {
        final DateTimeFormatter formatter = new DateTimeFormatter(null);
        final String compact = formatter.toCompactPattern(
                formatter.convertJavaDateTimePattern(javaPattern));

        assertThat(compact).doesNotContain("  ");
        assertThat(compact.trim()).isEqualTo(compact);
        assertThat(compact).doesNotMatch(".*[.,:#/-]$");
    }

}
