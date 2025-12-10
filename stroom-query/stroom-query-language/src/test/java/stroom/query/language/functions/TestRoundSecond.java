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

import net.bytebuddy.asm.Advice.Local;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.stream.Stream;

public class TestRoundSecond extends AbstractFunctionTest<RoundSecond> {

    @Override
    Class<RoundSecond> getFunctionType() {
        return RoundSecond.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        final LocalDateTime input = LocalDateTime.of(2025, 4, 7, 10, 30, 30, 501_000_001);
        final LocalDateTime floored = input.withNano(0).plusSeconds(1);

        final long inputMillis = input.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
        final long expectedMillis = floored.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();

        final String formattedInput = DateUtil.createNormalDateTimeString(inputMillis);

        final LocalDateTime inputDown = LocalDateTime.of(2025, 4, 7, 10, 30, 30, 500_000_000);
        final LocalDateTime flooredDown = inputDown.withNano(0);

        final long inputMillisDown = inputDown.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
        final long expectedMillisDown = flooredDown.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();

        final String formattedInputDown = DateUtil.createNormalDateTimeString(inputMillisDown);

        return Stream.of(
                TestCase.of(
                        "string date (formatted)",
                        ValDate.create(expectedMillis),
                        ValString.create(formattedInput)
                ),
                TestCase.of(
                        "string date (formatted)",
                        ValDate.create(expectedMillisDown),
                        ValString.create(formattedInputDown)
                )
        );
    }
}
