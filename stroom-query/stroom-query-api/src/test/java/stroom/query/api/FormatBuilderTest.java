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

package stroom.query.api;

import stroom.query.api.Format.Type;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FormatBuilderTest {
    @Test
    void doesBuildNumber() {
        final Integer decimalPlaces = 5;
        final Boolean useSeperator = true;

        final Format format = Format
                .builder()
                .type(Type.NUMBER)
                .settings(NumberFormatSettings
                        .builder()
                        .decimalPlaces(decimalPlaces)
                        .useSeparator(useSeperator)
                        .build())
                .build();

        assertThat(format.getType()).isEqualTo(Format.Type.NUMBER);

        final NumberFormatSettings numberFormatSettings = (NumberFormatSettings) format.getSettings();
        assertThat(numberFormatSettings).isNotNull();
        assertThat(numberFormatSettings.getDecimalPlaces()).isEqualTo(decimalPlaces);
        assertThat(numberFormatSettings.getUseSeparator()).isEqualTo(useSeperator);
    }

    @Test
    void doesBuildDateTime() {
        final String pattern = "DAY MONTH YEAR";

        final String timeZoneId = "someId";
        final UserTimeZone.Use use = UserTimeZone.Use.LOCAL;
        final Integer offsetHours = 3;
        final Integer offsetMinutes = 5;

        final Format format = Format
                .builder()
                .type(Type.DATE_TIME)
                .settings(DateTimeFormatSettings
                        .builder()
                        .pattern(pattern)
                        .timeZone(UserTimeZone
                                .builder()
                                .id(timeZoneId)
                                .use(use)
                                .offsetHours(offsetHours)
                                .offsetMinutes(offsetMinutes)
                                .build())
                        .build())
                .build();

        assertThat(format.getType()).isEqualTo(Format.Type.DATE_TIME);

        final DateTimeFormatSettings dateTimeFormatSettings = (DateTimeFormatSettings) format.getSettings();
        assertThat(dateTimeFormatSettings).isNotNull();
        assertThat(dateTimeFormatSettings.getPattern()).isEqualTo(pattern);

        final UserTimeZone timeZone = dateTimeFormatSettings.getTimeZone();
        assertThat(timeZone.getId()).isEqualTo(timeZoneId);
        assertThat(timeZone.getUse()).isEqualTo(use);
        assertThat(timeZone.getOffsetHours()).isEqualTo(offsetHours);
        assertThat(timeZone.getOffsetMinutes()).isEqualTo(offsetMinutes);
    }
}
