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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimeZoneBuilderTest {
    @Test
    void doesBuild() {
        final String id = "someId";
        final UserTimeZone.Use use = UserTimeZone.Use.LOCAL;
        final Integer offsetHours = 3;
        final Integer offsetMinutes = 5;

        final UserTimeZone timeZone = UserTimeZone
                .builder()
                .id(id)
                .use(use)
                .offsetHours(offsetHours)
                .offsetMinutes(offsetMinutes)
                .build();

        assertThat(timeZone.getId()).isEqualTo(id);
        assertThat(timeZone.getUse()).isEqualTo(use);
        assertThat(timeZone.getOffsetHours()).isEqualTo(offsetHours);
        assertThat(timeZone.getOffsetMinutes()).isEqualTo(offsetMinutes);
    }
}
