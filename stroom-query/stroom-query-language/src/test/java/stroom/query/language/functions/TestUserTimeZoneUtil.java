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

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestUserTimeZoneUtil {

    // The names that the preferences offer and ZoneId.of(String) refuses.
    @ParameterizedTest
    @CsvSource({
            "EST,-05:00",
            "MST,-07:00",
            "HST,-10:00",
            "ROC,+08:00",
            "Europe/London,+01:00"})
    void preferenceNamesAreKnown(final String id, final String expectedOffset) {
        final ZoneId zoneId = UserTimeZoneUtil.getZoneId(zone(id), null);

        assertThat(zoneId.getRules().getOffset(Instant.parse("2024-07-01T00:00:00Z")))
                .isEqualTo(ZoneOffset.of(expectedOffset));
    }

    @Test
    void unknownNameIsReported() {
        assertThatThrownBy(() -> UserTimeZoneUtil.getZoneId(zone("Mars/Base"), null))
                .isInstanceOf(DateTimeException.class)
                .hasMessage("Unknown time zone 'Mars/Base'");
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
