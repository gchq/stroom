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
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Behaviour shared by `floorTime`, `ceilingTime` and `roundTime`: which durations they accept,
/// where the halfway point of `roundTime` lies, and how multiples of the duration follow the
/// query's time zone.
class TestAbstractRoundTimeToDuration {

    private static final String UTC = "UTC";
    private static final String LONDON = "Europe/London";
    private static final String KOLKATA = "Asia/Kolkata";

    // --------------------------------------------------------------------------------
    // Durations
    // --------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"floorTime", "ceilingTime", "roundTime"})
    void negativeDurationIsRejected(final String function) throws Exception {
        final Val result = evaluate(UTC, function + "(${time}, '-PT5M')", "2025-04-07T10:44:30Z");

        assertThat(result.type().isError()).isTrue();
        assertThat(result.toString()).isEqualTo("ERR: Invalid duration: -PT5M - it must be at least one millisecond");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "PT0.0005S"})
    void durationUnderOneMillisecondIsRejected(final String duration) throws Exception {
        final Val result = evaluate(UTC, "floorTime(${time}, '" + duration + "')", "2025-04-07T10:44:30Z");

        assertThat(result.type().isError()).isTrue();
        assertThat(result.toString()).contains("it must be at least one millisecond");
    }

    @Test
    void invalidDurationIsReportedOnEveryRow() throws Exception {
        final ValueReferenceIndex valueReferenceIndex = new ValueReferenceIndex();
        final Generator generator = createGenerator(UTC, "floorTime(${time}, 'INVALID')", valueReferenceIndex);
        final StoredValues storedValues = valueReferenceIndex.createStoredValues();

        for (int row = 0; row < 2; row++) {
            generator.set(Val.of(ValDate.create(millis("2025-04-07T10:44:30Z"))), storedValues);
            assertThat(generator.eval(storedValues, null).toString())
                    .isEqualTo("ERR: Invalid duration format: INVALID");
        }
    }

    // --------------------------------------------------------------------------------
    // roundTime halfway point
    // --------------------------------------------------------------------------------

    @Test
    void roundTimeToOneMillisecondLeavesTheTimeAlone() throws Exception {
        assertThat(evaluate(UTC, "roundTime(${time}, 'PT0.001S')", 1_000_001L).toLong())
                .isEqualTo(1_000_001L);
    }

    @Test
    void roundTimeWithOddDurationRoundsDownBelowHalfway() throws Exception {
        // 2 ms past a multiple of 5 ms is nearer the multiple below.
        assertThat(evaluate(UTC, "roundTime(${time}, 'PT0.005S')", 1_000_002L).toLong())
                .isEqualTo(1_000_000L);
    }

    @Test
    void roundTimeWithOddDurationRoundsUpAboveHalfway() throws Exception {
        assertThat(evaluate(UTC, "roundTime(${time}, 'PT0.005S')", 1_000_003L).toLong())
                .isEqualTo(1_000_005L);
    }

    @Test
    void roundTimeExactlyHalfwayRoundsUp() throws Exception {
        assertThat(evaluate(UTC, "roundTime(${time}, 'PT0.004S')", 1_000_002L).toLong())
                .isEqualTo(1_000_004L);
    }

    // --------------------------------------------------------------------------------
    // Time zones
    // --------------------------------------------------------------------------------

    @Test
    void floorTimeToADayStartsAtLocalMidnight() throws Exception {
        // 00:30 on 1 July in London (BST, +01:00).
        assertThat(evaluateToInstant(LONDON, "floorTime(${time}, 'P1D')", "2025-06-30T23:30:00Z"))
                .isEqualTo("2025-06-30T23:00:00Z");
    }

    @Test
    void ceilingTimeToADayEndsAtLocalMidnight() throws Exception {
        assertThat(evaluateToInstant(LONDON, "ceilingTime(${time}, 'P1D')", "2025-06-30T22:30:00Z"))
                .isEqualTo("2025-06-30T23:00:00Z");
    }

    @Test
    void floorTimeToAnHourFollowsAHalfHourOffset() throws Exception {
        // 10:44 in Kolkata (+05:30) floors to 10:00 there, not to 10:30.
        assertThat(evaluateToInstant(KOLKATA, "floorTime(${time}, 'PT1H')", "2025-04-07T05:14:00Z"))
                .isEqualTo("2025-04-07T04:30:00Z");
    }

    @Test
    void roundTimeToAnHourFollowsAHalfHourOffset() throws Exception {
        // 10:44 in Kolkata rounds to 11:00 there.
        assertThat(evaluateToInstant(KOLKATA, "roundTime(${time}, 'PT1H')", "2025-04-07T05:14:00Z"))
                .isEqualTo("2025-04-07T05:30:00Z");
    }

    @Test
    void floorTimeBefore1970FollowsTheZone() throws Exception {
        // 12:20 on 10 March 1965 in Kolkata floors to local midnight.
        assertThat(evaluateToInstant(KOLKATA, "floorTime(${time}, 'P1D')", "1965-03-10T06:50:00Z"))
                .isEqualTo("1965-03-09T18:30:00Z");
    }

    @Test
    void floorTimeIntoTheGapWhenClocksGoForwardGivesTheMomentOfTheChange() throws Exception {
        // On 30 March 2025 London jumps from 01:00 GMT to 02:00 BST. 02:10 BST floors to 01:30
        // local, which never happens, so the result is the change itself rather than a later time.
        assertThat(evaluateToInstant(LONDON, "floorTime(${time}, 'PT90M')", "2025-03-30T01:10:00Z"))
                .isEqualTo("2025-03-30T01:00:00Z");
    }

    @Test
    void ceilingTimeIntoTheGapWhenClocksGoForwardGivesTheMomentOfTheChange() throws Exception {
        // 00:50 GMT rounds up to 01:30 local, which never happens.
        assertThat(evaluateToInstant(LONDON, "ceilingTime(${time}, 'PT90M')", "2025-03-30T00:50:00Z"))
                .isEqualTo("2025-03-30T01:00:00Z");
    }

    @Test
    void floorTimeWhenClocksGoBackKeepsTheFirstOffset() throws Exception {
        // On 26 October 2025 London goes from 02:00 BST back to 01:00 GMT, so 01:00 to 02:00 local
        // happens twice. 01:40 BST, the first time round, floors to 01:30 BST.
        assertThat(evaluateToInstant(LONDON, "floorTime(${time}, 'PT30M')", "2025-10-26T00:40:00Z"))
                .isEqualTo("2025-10-26T00:30:00Z");
    }

    @Test
    void floorTimeWhenClocksGoBackKeepsTheSecondOffset() throws Exception {
        // 01:40 GMT, the second time round, floors to 01:30 GMT.
        assertThat(evaluateToInstant(LONDON, "floorTime(${time}, 'PT30M')", "2025-10-26T01:40:00Z"))
                .isEqualTo("2025-10-26T01:30:00Z");
    }

    private static String evaluateToInstant(final String zoneId,
                                            final String expression,
                                            final String input) throws Exception {
        final Val result = evaluate(zoneId, expression, input);
        assertThat(result.type().isError())
                .as(result.toString())
                .isFalse();
        return Instant.ofEpochMilli(result.toLong()).toString();
    }

    private static Val evaluate(final String zoneId,
                                final String expression,
                                final String input) throws Exception {
        return evaluate(zoneId, expression, millis(input));
    }

    private static Val evaluate(final String zoneId,
                                final String expression,
                                final long inputMillis) throws Exception {
        final ValueReferenceIndex valueReferenceIndex = new ValueReferenceIndex();
        final Generator generator = createGenerator(zoneId, expression, valueReferenceIndex);
        final StoredValues storedValues = valueReferenceIndex.createStoredValues();
        generator.set(Val.of(ValDate.create(inputMillis)), storedValues);
        return generator.eval(storedValues, null);
    }

    private static Generator createGenerator(final String zoneId,
                                             final String expression,
                                             final ValueReferenceIndex valueReferenceIndex) throws Exception {
        final DateTimeSettings dateTimeSettings = DateTimeSettings.builder()
                .timeZone(UserTimeZone.fromId(zoneId))
                .build();
        final ExpressionContext expressionContext = new ExpressionContext(null, dateTimeSettings, null, null);
        final ExpressionParser parser = new ExpressionParser(new ParamFactory(Map.of()));
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create("time");
        final Expression exp = parser.parse(expressionContext, fieldIndex, expression);
        exp.setStaticMappedValues(Map.of());
        exp.addValueReferences(valueReferenceIndex);
        return exp.createGenerator();
    }

    private static long millis(final String instant) {
        return Instant.parse(instant).toEpochMilli();
    }
}
