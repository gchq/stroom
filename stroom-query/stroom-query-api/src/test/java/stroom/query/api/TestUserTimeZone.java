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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the non-null guarantee the getters make.
 *
 * <p>Three of the four factories pass {@code null} for the offset components -
 * {@code local()}, {@code utc()} and {@code fromId(..)} - and only the constructor's
 * {@code Objects.requireNonNullElse} calls turn those into zeros. Callers rely on that: reading
 * the offsets as {@code int} unboxes them, so a {@code null} would throw rather than degrade.
 * {@code DateTimeFormatter.getTimeZoneSettings} does exactly that for every date the UI renders.</p>
 *
 * <p>Without this test the coercion is unguarded: removing it would compile, pass every existing
 * test, and fail at runtime for any user whose zone is not an explicit offset - which is the
 * default.</p>
 */
class TestUserTimeZone {

    @Test
    void testFactoriesNeverLeaveOffsetsNull() {
        for (final UserTimeZone timeZone : new UserTimeZone[]{
                UserTimeZone.local(),
                UserTimeZone.utc(),
                UserTimeZone.fromId("Europe/London"),
                UserTimeZone.fromOffset(2, 30),
                UserTimeZone.builder().use(UserTimeZone.Use.UTC).build()}) {

            assertThat(timeZone.getOffsetHours())
                    .as("offsetHours of %s", timeZone.getUse())
                    .isNotNull();
            assertThat(timeZone.getOffsetMinutes())
                    .as("offsetMinutes of %s", timeZone.getUse())
                    .isNotNull();
            assertThat(timeZone.getUse()).isNotNull();
            assertThat(timeZone.getId()).isNotNull();
        }
    }

    /** Explicit nulls through the JSON creator are coerced too, not just the factories. */
    @Test
    void testExplicitNullsAreCoerced() {
        final UserTimeZone timeZone = new UserTimeZone(null, null, null, null);

        assertThat(timeZone.getUse()).isEqualTo(UserTimeZone.Use.UTC);
        assertThat(timeZone.getId()).isEmpty();
        assertThat(timeZone.getOffsetHours()).isZero();
        assertThat(timeZone.getOffsetMinutes()).isZero();
    }
}
