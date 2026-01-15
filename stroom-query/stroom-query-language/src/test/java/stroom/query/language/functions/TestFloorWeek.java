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

public class TestFloorWeek extends AbstractFunctionTest<FloorWeek> {

    @Override
    Class<FloorWeek> getFunctionType() {
        return FloorWeek.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        final Instant time = LocalDateTime.of(2025, 4, 10, 10, 30, 30)
                .toInstant(ZoneOffset.UTC);

        // April 7th 2025 is the Monday
        final Instant floored = LocalDateTime.of(2025, 4, 7, 0, 0)
                .toInstant(ZoneOffset.UTC);

        return Stream.of(
                TestCase.of(
                        "long date",
                        ValDate.create(floored.toEpochMilli()),
                        ValLong.create(time.toEpochMilli())),
                TestCase.of(
                        "string date",
                        ValDate.create(floored.toEpochMilli()),
                        ValString.create(DateUtil.createNormalDateTimeString(time.toEpochMilli())))
        );
    }
}
