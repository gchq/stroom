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

import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A duration argument written as {@code param('key')} must work exactly as a literal does.
 *
 * <p>It did not. {@code parseDuration} read the argument with a bare {@code param.toString()}, which
 * for a function renders its own <em>source text</em> — so {@code floorTime(t, param('w'))} tried to
 * parse the string {@code "param('w')"} as a {@code Duration}, threw, and produced {@code ValErr} for
 * every row.</p>
 *
 * <p><b>The failure mode is why this test exists.</b> A {@code ValErr} cell is not a search error, so
 * nothing was reported: a query grouping by the result collapsed to a single error group and the
 * caller simply saw no data. Stroom's floor map hit exactly that — its density histogram went blank
 * with no error anywhere. A silent wrong answer is the kind of regression a test has to hold down,
 * because nothing else will notice it.</p>
 *
 * <p>See {@code AbstractRoundDateTime.constantString} for the fix and why it is marked local.</p>
 */
class TestRoundDateTimeConstantDuration {

    private static final long INPUT = Instant.parse("2026-01-01T10:44:30.000Z").toEpochMilli();

    private static String evaluate(final String expression,
                                   final Map<String, String> params) throws Exception {
        final ExpressionParser parser = new ExpressionParser(new ParamFactory(Map.of()));
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create("time");
        final Expression exp = parser.parse(new ExpressionContext(), fieldIndex, expression);
        exp.setStaticMappedValues(params);

        final ValueReferenceIndex valueReferenceIndex = new ValueReferenceIndex();
        exp.addValueReferences(valueReferenceIndex);
        final StoredValues storedValues = valueReferenceIndex.createStoredValues();
        final Generator generator = exp.createGenerator();
        generator.set(Val.of(ValDate.create(INPUT)), storedValues);
        return generator.eval(storedValues, null).toString();
    }

    @Test
    void floorTimeTreatsAParameterAsItTreatsALiteral() throws Exception {
        final String fromLiteral = evaluate("floorTime(${time}, 'PT10M')", Map.of());
        final String fromParam = evaluate(
                "floorTime(${time}, param('bucketWidth'))",
                Map.of("bucketWidth", "PT10M"));

        assertThat(fromLiteral).isEqualTo("2026-01-01T10:40:00.000Z");
        assertThat(fromParam)
                .as("a parameterised width is what lets a stored query survive a change of zoom")
                .isEqualTo(fromLiteral);
    }

    @Test
    void ceilingTimeTreatsAParameterAsItTreatsALiteral() throws Exception {
        // The expected value is pinned, not just compared to the literal's: two identical errors
        // would satisfy an equality check and prove nothing.
        assertThat(evaluate("ceilingTime(${time}, 'PT10M')", Map.of()))
                .isEqualTo("2026-01-01T10:50:00.000Z");
        assertThat(evaluate("ceilingTime(${time}, param('w'))", Map.of("w", "PT10M")))
                .isEqualTo("2026-01-01T10:50:00.000Z");
    }

    @Test
    void roundTimeTreatsAParameterAsItTreatsALiteral() throws Exception {
        assertThat(evaluate("roundTime(${time}, 'PT10M')", Map.of()))
                .isEqualTo("2026-01-01T10:40:00.000Z");
        assertThat(evaluate("roundTime(${time}, param('w'))", Map.of("w", "PT10M")))
                .isEqualTo("2026-01-01T10:40:00.000Z");
    }

    /**
     * A duration that is not constant is still refused.
     *
     * <p>Not a gap in the fix but the point of it: rounding to a width that differs from row to row
     * has no meaning, so a field reference is reported as an invalid duration exactly as before.</p>
     */
    @Test
    void perRowDurationIsStillRejected() throws Exception {
        assertThat(evaluate("floorTime(${time}, ${time})", Map.of()))
                .contains("not something computed per row");
    }

    /**
     * An unmapped parameter and a per-row argument are different mistakes.
     *
     * <p>Both reach the same branch, and telling someone whose parameter has no value that their
     * argument "must be a constant" sends them to fix the wrong thing.</p>
     */
    @Test
    void anUnmappedParameterSaysSoRatherThanBlamingTheExpression() throws Exception {
        assertThat(evaluate("floorTime(${time}, param('missing'))", Map.of()))
                .contains("no value was supplied");
    }
}
