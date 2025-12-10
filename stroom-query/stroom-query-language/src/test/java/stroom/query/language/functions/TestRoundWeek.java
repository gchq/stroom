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
import java.util.stream.Stream;

public class TestRoundWeek extends AbstractFunctionTest<RoundWeek> {

    @Override
    Class<RoundWeek> getFunctionType() {
        return RoundWeek.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        final Instant time = LocalDateTime.of(2025, 4, 10, 12, 31, 0)
                .toInstant(ZoneOffset.UTC);

        final Instant truncatedUp = LocalDateTime.ofInstant(time, ZoneOffset.UTC)
                .withMonth(4)
                .withDayOfMonth(14)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant(ZoneOffset.UTC);

        final Instant timeT = LocalDateTime.of(2025, 4, 10, 0, 0, 0)
                .toInstant(ZoneOffset.UTC);

        final Instant truncated = LocalDateTime.ofInstant(time, ZoneOffset.UTC)
                .withMonth(4)
                .withDayOfMonth(7)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant(ZoneOffset.UTC);

        final Instant timeWithZone = LocalDateTime.of(2025, 4, 10, 10, 31, 0)
                .toInstant(ZoneOffset.ofHours(-2));

        final Instant truncatedUpWithZone = LocalDateTime.ofInstant(timeWithZone, ZoneOffset.ofHours(2))
                .withMonth(4)
                .withDayOfMonth(14)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant(ZoneOffset.ofHours(0));

        return Stream.of(
                TestCase.of(
                        "long date",
                        ValDate.create(truncatedUp.toEpochMilli()),
                        ValLong.create(time.toEpochMilli())),
                TestCase.of(
                        "string date",
                        ValDate.create(truncatedUp.toEpochMilli()),
                        ValString.create(DateUtil.createNormalDateTimeString(time.toEpochMilli()))),
                TestCase.of(
                        "long date",
                        ValDate.create(truncated.toEpochMilli()),
                        ValLong.create(timeT.toEpochMilli())),
                TestCase.of(
                        "string date",
                        ValDate.create(truncated.toEpochMilli()),
                        ValString.create(DateUtil.createNormalDateTimeString(timeT.toEpochMilli()))),
                TestCase.of(
                        "long date with timezone",
                        ValDate.create(truncatedUpWithZone.toEpochMilli()),
                        ValLong.create(timeWithZone.toEpochMilli()))
        );
    }
}
