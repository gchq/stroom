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

import stroom.query.api.HoppingWindow;
import stroom.query.api.token.TokenException;
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.time.Instant;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The functions that give the window a row is in: `windowStart()`, `windowEnd()` and `windowKey()`.
class TestWindowFunctions {

    private static final HoppingWindow LONDON_DAYS =
            new HoppingWindow("EventTime", "1d", "1h", "-6h", "Europe/London");
    private static final ExpressionContext WINDOW_CONTEXT = ExpressionContext
            .builder()
            .window(LONDON_DAYS)
            .build();
    private static final ExpressionContext PLAIN_CONTEXT = ExpressionContext.builder().build();

    @Test
    void functionTakesItsWindowsFromTheQueryBeingParsed() throws ParseException {
        assertThat(parse(WINDOW_CONTEXT, "windowStart()").toString())
                .isEqualTo("windowStart('window:1d|1h|-6h|Europe/London')");
        assertThat(parse(WINDOW_CONTEXT, "windowEnd()").toString())
                .isEqualTo("windowEnd('window:1d|1h|-6h|Europe/London')");
        assertThat(parse(WINDOW_CONTEXT, "windowKey()").toString())
                .isEqualTo("windowKey('window:1d|1h|-6h|Europe/London')");
    }

    @Test
    void windowsWithNoAdvanceOrOffsetAreCarriedToo() throws ParseException {
        final ExpressionContext context = ExpressionContext
                .builder()
                .window(new HoppingWindow("EventTime", "1h", null, null, "Z"))
                .build();

        final String parsed = parse(context, "windowStart()").toString();
        assertThat(parsed).isEqualTo("windowStart('window:1h|||Z')");
        assertThat(parse(PLAIN_CONTEXT, parsed).toString()).isEqualTo(parsed);
    }

    @Test
    void parsedFunctionKeepsItsWindowsWhenCompiledAgainWithNone() throws ParseException {
        // A column's expression is stored as text and compiled again where the query runs, where the
        // context knows nothing of the query's window clause.
        final String parsed = parse(WINDOW_CONTEXT, "windowStart()").toString();

        final Expression compiledAgain = parse(PLAIN_CONTEXT, parsed);

        assertThat(compiledAgain.toString()).isEqualTo(parsed);
        final TimeWindows timeWindows = TimeWindows.create(LONDON_DAYS);
        final long windowKey = lastWindowKey(timeWindows, "2026-06-15T13:47:00Z");
        assertThat(eval(compiledAgain, windowKey))
                .isEqualTo(ValDate.create(timeWindows.getWindowStart(windowKey)));
    }

    @Test
    void functionsGiveTheStartEndAndKeyOfARowsWindow() throws ParseException {
        final TimeWindows timeWindows = TimeWindows.create(LONDON_DAYS);
        final long windowKey = lastWindowKey(timeWindows, "2026-06-15T13:47:00Z");

        // 13:47 UTC is 14:47 in London, and windows start on the hour, so the latest started at 14:00
        // there, which is 13:00 UTC.
        assertThat(eval(parse(WINDOW_CONTEXT, "windowStart()"), windowKey))
                .isEqualTo(ValDate.create(Instant.parse("2026-06-15T13:00:00Z").toEpochMilli()));
        assertThat(eval(parse(WINDOW_CONTEXT, "windowEnd()"), windowKey))
                .isEqualTo(ValDate.create(Instant.parse("2026-06-16T13:00:00Z").toEpochMilli()));
        assertThat(eval(parse(WINDOW_CONTEXT, "windowKey()"), windowKey))
                .isEqualTo(ValLong.create(windowKey));
    }

    @Test
    void rowThatWasNeverInAWindowHasNoStart() throws ParseException {
        final Expression expression = parse(WINDOW_CONTEXT, "windowStart()");
        final ValueReferenceIndex valueReferenceIndex = new ValueReferenceIndex();
        expression.addValueReferences(valueReferenceIndex);
        final Generator generator = expression.createGenerator();

        assertThat(generator.eval(valueReferenceIndex.createStoredValues(), null)).isEqualTo(ValNull.INSTANCE);
    }

    @Test
    void functionOutsideAWindowQueryIsRefused() {
        assertThatThrownBy(() -> parse(PLAIN_CONTEXT, "windowStart()"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("'windowStart' can only be used in a query that has a window clause");
    }

    @Test
    void functionTakesNoParametersOfItsOwn() {
        assertThatThrownBy(() -> parse(WINDOW_CONTEXT, "windowStart(1)"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("Invalid number of parameters");
    }

    @Test
    void windowsThatCantBeUsedAreRefused() {
        assertThatThrownBy(() -> parse(PLAIN_CONTEXT, "windowStart('window:1h|25m||Z')"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("1h is not a multiple of 25m");
        assertThatThrownBy(() -> parse(PLAIN_CONTEXT, "windowStart('window:1h')"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("can't start with 'window:'");
    }

    private static Expression parse(final ExpressionContext expressionContext,
                                    final String expression) throws ParseException {
        return new ExpressionParser(new ParamFactory(new HashMap<>()))
                .parse(expressionContext, new FieldIndex(), expression);
    }

    private static long lastWindowKey(final TimeWindows timeWindows, final String time) {
        final long[] last = new long[1];
        timeWindows.forEachWindow(Instant.parse(time).toEpochMilli(), key -> last[0] = key);
        return last[0];
    }

    // Adds a row that is in a window and gives the function's value for it.
    private static Val eval(final Expression expression, final long windowKey) {
        final ValueReferenceIndex valueReferenceIndex = new ValueReferenceIndex();
        expression.addValueReferences(valueReferenceIndex);
        final Generator generator = expression.createGenerator();
        final StoredValues storedValues = valueReferenceIndex.createStoredValues();
        storedValues.setWindowKey(windowKey);
        generator.set(Val.emptyArray(), storedValues);
        return generator.eval(storedValues, null);
    }
}
