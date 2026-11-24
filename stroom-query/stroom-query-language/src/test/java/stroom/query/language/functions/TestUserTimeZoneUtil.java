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

package stroom.query.language.functions;

import stroom.query.api.DateTimeSettings;
import stroom.query.api.UserTimeZone;
import stroom.query.api.UserTimeZone.Use;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.ParseException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestUserTimeZoneUtil {

    @ParameterizedTest
    @CsvSource({
            "Europe/London,+01:00",
            "America/New_York,-04:00",
            "Etc/GMT+5,-05:00",
            "UTC,Z",
            "GMT,Z",
            "Z,Z",
            "+0530,+05:30",
            "-05:00,-05:00",
            "+02,+02:00",
            "GMT+2,+02:00",
            "UTC-03:00,-03:00",
            "' Europe/London ',+01:00"})
    void namesThatAFunctionCanBeGiven(final String name, final String expectedOffset) {
        assertThat(UserTimeZoneUtil.getZoneId(ValString.create(name))
                .getRules()
                .getOffset(Instant.parse("2024-07-01T00:00:00Z")))
                .isEqualTo(ZoneOffset.of(expectedOffset));
    }

    // Short names, whether or not Java knows them, as they mean different things to different people.
    @ParameterizedTest
    @ValueSource(strings = {
            "BST", "AST", "IST", "PST", "CST", "EST", "MST", "HST", "ROC", "CET", "EET", "MET", "WET", "EST5EDT", "GB",
            "NZ", "Japan", "Cuba", "Zulu", "Universal", "GMT0", "utc", "Mars", "Mars/Base", "+25:00", "GMT+99"})
    void namesThatAFunctionCantBeGiven(final String name) {
        assertThatThrownBy(() -> UserTimeZoneUtil.getZoneId(ValString.create(name)))
                .isInstanceOf(DateTimeException.class)
                .hasMessage("Unknown time zone '" + name + "'. Use a name such as Europe/London");
        assertThatThrownBy(() -> DateUtil.getTimeZone(name))
                .isInstanceOf(ParseException.class)
                .hasMessage("Unknown time zone '" + name + "'. Use a name such as Europe/London");
    }

    @Test
    void noName() throws ParseException {
        for (final Val val : List.of(ValNull.INSTANCE, ValString.create(""), ValString.create(" "))) {
            assertThatThrownBy(() -> UserTimeZoneUtil.getZoneId(val))
                    .isInstanceOf(DateTimeException.class)
                    .hasMessage("There is no time zone");
        }
        // As parseDate and formatDate have always had it.
        assertThat(DateUtil.getTimeZone("")).isEqualTo(ZoneOffset.UTC);
    }

    // These could be chosen from the lists before.
    @ParameterizedTest
    @CsvSource({
            "CET,+02:00",
            "GB,+01:00",
            "Europe/London,+01:00"})
    void namesThatWereChosenBeforeAreKept(final String id, final String expectedOffset) {
        assertThat(UserTimeZoneUtil.getZoneId(zone(id), null)
                .getRules()
                .getOffset(Instant.parse("2024-07-01T00:00:00Z")))
                .isEqualTo(ZoneOffset.of(expectedOffset));
    }

    @ParameterizedTest
    @ValueSource(strings = {"EST", "BST", "ROC"})
    void namesThatJavaDoesNotKnowAreNot(final String id) {
        assertThatThrownBy(() -> UserTimeZoneUtil.getZoneId(zone(id), null))
                .isInstanceOf(DateTimeException.class)
                .hasMessage("Unknown time zone '" + id + "'. Use a name such as Europe/London");
    }

    @Test
    void unknownNameIsReported() {
        assertThatThrownBy(() -> UserTimeZoneUtil.getZoneId(zone("Mars/Base"), null))
                .isInstanceOf(DateTimeException.class)
                .hasMessage("Unknown time zone 'Mars/Base'. Use a name such as Europe/London");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void missingNameIsReported(final String id) {
        assertThatThrownBy(() -> UserTimeZoneUtil.getZoneId(zone(id), null))
                .isInstanceOf(DateTimeException.class)
                .hasMessage("There is no time zone");
    }

    @Test
    void noSettingsIsUtc() {
        assertThat(UserTimeZoneUtil.getZoneId((DateTimeSettings) null)).isEqualTo(ZoneOffset.UTC);
        assertThat(UserTimeZoneUtil.getZoneId((ExpressionContext) null)).isEqualTo(ZoneOffset.UTC);
        assertThat(UserTimeZoneUtil.getZoneId(null, null)).isEqualTo(ZoneOffset.UTC);
    }

    private static UserTimeZone zone(final String id) {
        return UserTimeZone.builder().use(Use.ID).id(id).build();
    }
}
