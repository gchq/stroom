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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

class TestRoundDay extends AbstractFunctionTest<RoundDay> {

    @Override
    Class<RoundDay> getFunctionType() {
        return RoundDay.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        final Instant time = LocalDateTime.of(2025, 4, 7, 12, 0, 1)
                .toInstant(ZoneOffset.UTC);

        final Instant truncatedUp = time.truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS);

        final Instant timeT = LocalDateTime.of(2025, 4, 7, 12, 0, 0)
                .toInstant(ZoneOffset.UTC);

        final Instant truncated = time.truncatedTo(ChronoUnit.DAYS);

        final Instant timeWithZone = LocalDateTime.of(2025, 4, 7, 10, 0, 1)
                .toInstant(ZoneOffset.ofHours(-2));

        final Instant truncatedUpWithZone = timeWithZone.truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS);

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
                        "long date down",
                        ValDate.create(truncated.toEpochMilli()),
                        ValLong.create(timeT.toEpochMilli())),
                TestCase.of(
                        "string date down",
                        ValDate.create(truncated.toEpochMilli()),
                        ValString.create(DateUtil.createNormalDateTimeString(timeT.toEpochMilli()))),
                TestCase.of(
                        "long date with timezone",
                        ValDate.create(truncatedUpWithZone.toEpochMilli()),
                        ValLong.create(timeWithZone.toEpochMilli()))
        );
    }


}
