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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

class TestIsWeekend extends AbstractFunctionTest<IsWeekend> {

    @Override
    Class<IsWeekend> getFunctionType() {
        return IsWeekend.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        final Instant weekdayUTC = LocalDateTime.of(2025, 5, 8, 15, 30, 55)
                .toInstant(ZoneOffset.UTC);
        final Instant weekendUTC = LocalDateTime.of(2025, 5, 11, 15, 30, 55)
                .toInstant(ZoneOffset.UTC);

        //test for UTC+5
        final Instant weekdayUTCPlus5 = LocalDateTime.of(2025, 5, 8, 15, 30, 55)
                .toInstant(ZoneOffset.ofHours(5));
        final Instant weekendUTCPlus5 = LocalDateTime.of(2025, 5, 11, 15, 30, 55)
                .toInstant(ZoneOffset.ofHours(5));
        final Instant mondayUTC = LocalDateTime.of(2025, 5, 5, 12, 0, 0)
                .toInstant(ZoneOffset.UTC);

        final Instant tuesdayUTC = LocalDateTime.of(2025, 5, 6, 12, 0, 0)
                .toInstant(ZoneOffset.UTC);
        final Instant wednesdayUTC = LocalDateTime.of(2025, 5, 7, 12, 0, 0)
                .toInstant(ZoneOffset.UTC);

        final Instant fridayUTC = LocalDateTime.of(2025, 5, 9, 12, 0, 0)
                .toInstant(ZoneOffset.UTC);
        final Instant saturdayUTC = LocalDateTime.of(2025, 5, 10, 12, 0, 0)
                .toInstant(ZoneOffset.UTC);

        // transition times
        final Instant fridayNightUTC = LocalDateTime.of(2025, 5, 9, 23, 59, 59)
                .toInstant(ZoneOffset.UTC);
        final Instant saturdayMorningUTC = LocalDateTime.of(2025, 5, 10, 0, 0, 0)
                .toInstant(ZoneOffset.UTC);

        final Instant sundayNightUTC = LocalDateTime.of(2025, 5, 11, 23, 59, 59)
                .toInstant(ZoneOffset.UTC);
        final Instant mondayMorningUTC = LocalDateTime.of(2025, 5, 12, 0, 0, 0)
                .toInstant(ZoneOffset.UTC);

        // months and years
        final Instant decemberWeekend = LocalDateTime.of(2024, 12, 28, 12, 0, 0)
                .toInstant(ZoneOffset.UTC);
        final Instant januaryWeekday = LocalDateTime.of(2026, 1, 2, 12, 0, 0)
                .toInstant(ZoneOffset.UTC);

        // timezone (UTC-8)
        final Instant saturdayUTCMinus8 = LocalDateTime.of(2025, 5, 10, 12, 0, 0)
                .toInstant(ZoneOffset.ofHours(-8));

        return Stream.of(
                TestCase.of(
                        "long date UTC",
                        ValBoolean.create(false),
                        ValDate.create(weekdayUTC.toEpochMilli())),
                TestCase.of(
                        "string date UTC",
                        ValBoolean.create(true),
                        ValDate.create(weekendUTC.toEpochMilli())),
                TestCase.of(
                        "long date UTC+5",
                        ValBoolean.create(false),
                        ValDate.create(weekdayUTCPlus5.toEpochMilli())),
                TestCase.of(
                        "string date UTC+5",
                        ValBoolean.create(true),
                        ValDate.create(weekendUTCPlus5.toEpochMilli())),
                TestCase.of(
                        "Monday",
                        ValBoolean.create(false),
                        ValDate.create(mondayUTC.toEpochMilli())),
                TestCase.of(
                        "Tuesday",
                        ValBoolean.create(false),
                        ValDate.create(tuesdayUTC.toEpochMilli())),
                TestCase.of(
                        "Wednesday",
                        ValBoolean.create(false),
                        ValDate.create(wednesdayUTC.toEpochMilli())),
                TestCase.of(
                        "Friday",
                        ValBoolean.create(false),
                        ValDate.create(fridayUTC.toEpochMilli())),
                TestCase.of(
                        "Saturday",
                        ValBoolean.create(true),
                        ValDate.create(saturdayUTC.toEpochMilli())),
                TestCase.of(
                        "Friday night (23:59:59)",
                        ValBoolean.create(false),
                        ValDate.create(fridayNightUTC.toEpochMilli())),
                TestCase.of(
                        "Saturday morning (00:00:00)",
                        ValBoolean.create(true),
                        ValDate.create(saturdayMorningUTC.toEpochMilli())),
                TestCase.of(
                        "Sunday night (23:59:59)",
                        ValBoolean.create(true),
                        ValDate.create(sundayNightUTC.toEpochMilli())),
                TestCase.of(
                        "Monday morning (00:00:00)",
                        ValBoolean.create(false),
                        ValDate.create(mondayMorningUTC.toEpochMilli())),
                TestCase.of(
                        "December 2024 (Saturday)",
                        ValBoolean.create(true),
                        ValDate.create(decemberWeekend.toEpochMilli())),
                TestCase.of(
                        "January 2026 (Friday)",
                        ValBoolean.create(false),
                        ValDate.create(januaryWeekday.toEpochMilli())),
                TestCase.of(
                        "Saturday in UTC-8",
                        ValBoolean.create(true),
                        ValDate.create(saturdayUTCMinus8.toEpochMilli())),
                TestCase.of(
                        "Null input",
                        ValNull.INSTANCE,
                        ValNull.INSTANCE)
        );
    }
}
