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
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// `lag` and `lead`, which give the value an aggregate has in another window.
class TestLag {

    private static final ExpressionContext WINDOW_CONTEXT = ExpressionContext
            .builder()
            .window(new HoppingWindow("EventTime", "1d", null, null, "Z"))
            .build();
    private static final ExpressionContext PLAIN_CONTEXT = ExpressionContext.builder().build();

    @Test
    void functionsCarryTheWindowsOfTheQuery() throws ParseException {
        final String parsed = parse(WINDOW_CONTEXT, "count() - lag(count(), 1)").toString();

        assertThat(parsed).isEqualTo("count()-lag(count(), 1, 'window:1d|||Z')");
        // The stored text compiles again where nothing is known of the window clause.
        assertThat(parse(PLAIN_CONTEXT, parsed).toString()).isEqualTo(parsed);
        assertThat(parse(WINDOW_CONTEXT, "lead(sum(${Bytes}), 2, 0)").toString())
                .isEqualTo("lead(sum(${Bytes}), 2, 0, 'window:1d|||Z')");
    }

    @Test
    void lagSaysHowFarRowsMustBeCopied() throws ParseException {
        final ValueReferenceIndex valueReferenceIndex = new ValueReferenceIndex();
        parse(WINDOW_CONTEXT, "lag(count(), 1) + lag(count(), 1) + lag(count(), 7) + lead(count(), 2)")
                .addValueReferences(valueReferenceIndex);

        // A row is copied later for lag and earlier for lead, once for each distinct distance.
        assertThat(valueReferenceIndex.getWindowShifts()).containsExactly(-2, 1, 7);
        assertThat(valueReferenceIndex.usesWindows()).isTrue();
    }

    @Test
    void aRowInItsOwnWindowIsNotCountedByLag() throws ParseException {
        final Row row = new Row("count() - lag(count(), 1)");

        row.add(0);
        row.add(0);
        row.add(0);

        assertThat(row.eval().toLong()).isEqualTo(3);
    }

    @Test
    void aRowCopiedFromAnotherWindowIsCountedOnlyByTheFunctionThatReadsThatWindow() throws ParseException {
        final Row row = new Row("concat(count(), ' ', lag(count(), 1), ' ', lag(count(), 2), ' ', lead(count(), 1))");

        // Three rows of this window, two copied from the window before, one from two windows before
        // and four from the window after.
        row.add(0);
        row.add(0);
        row.add(0);
        row.add(1);
        row.add(1);
        row.add(2);
        row.add(-1);
        row.add(-1);
        row.add(-1);
        row.add(-1);

        assertThat(row.eval()).isEqualTo(ValString.create("3 2 1 4"));
    }

    @Test
    void defaultIsGivenWhenTheOtherWindowHasNoValue() throws ParseException {
        final Row withDefault = new Row("lag(max(${Bytes}), 1, -1)");
        final Row withNone = new Row("lag(max(${Bytes}), 1)");

        withDefault.add(0);
        withNone.add(0);

        assertThat(withDefault.eval().toLong()).isEqualTo(-1);
        assertThat(withNone.eval()).isEqualTo(ValNull.INSTANCE);
    }

    @Test
    void windowOutsideTheRangeOfTheDataHasNoValue() throws ParseException {
        final Row row = new Row("lag(count(), 1, -1)");
        final TimeWindows timeWindows = TimeWindows.create(new HoppingWindow("EventTime", "1d", null, null, "Z"));
        final long first = 0;
        final long second = timeWindows.shift(first, 1);

        // The data starts in the first window, so the window before it is outside the range, while
        // the first window is inside it though this group has no rows there.
        row.valueReferenceIndex.getWindowRange().set(first, second);

        row.addTo(first);
        assertThat(row.eval().toLong()).isEqualTo(-1);

        final Row later = new Row("lag(count(), 1, -1)");
        later.valueReferenceIndex.getWindowRange().set(first, second);
        later.addTo(second);
        assertThat(later.eval().toLong()).isZero();
    }

    @Test
    void lagIsKeptForAGroupedRow() throws ParseException {
        assertThat(parse(WINDOW_CONTEXT, "lag(count(), 1)").hasAggregate()).isTrue();
    }

    @Test
    void badParametersAreRefused() {
        assertRefused(WINDOW_CONTEXT, "lag(count())", "Invalid number of parameters");
        assertRefused(WINDOW_CONTEXT, "lag(count(), 1, 0, 0)", "Invalid number of parameters");
        assertRefused(WINDOW_CONTEXT, "lag(${Bytes}, 1)", "The first parameter of 'lag' must be an aggregate");
        assertRefused(WINDOW_CONTEXT, "lag(5, 1)", "The first parameter of 'lag' must be an aggregate");
        // Functions that would give a wrong value from the copies of another window's rows.
        assertRefused(WINDOW_CONTEXT, "lag(lag(count(), 1), 1)", "The first parameter of 'lag' must be");
        assertRefused(WINDOW_CONTEXT, "lag(windowStart(), 1)", "The first parameter of 'lag' must be");
        assertRefused(WINDOW_CONTEXT, "lag(first(${Bytes}), 1)", "The first parameter of 'lag' must be");
        assertRefused(WINDOW_CONTEXT, "lag(count() + 1, 1)", "The first parameter of 'lag' must be");
        assertRefused(WINDOW_CONTEXT, "lead(count(), 0)", "must be a whole number of windows from 1 to 10000");
        assertRefused(WINDOW_CONTEXT, "lead(count(), 1.5)", "must be a whole number of windows from 1 to 10000");
        assertRefused(WINDOW_CONTEXT, "lag(count(), 10001)", "must be a whole number of windows from 1 to 10000");
        assertRefused(WINDOW_CONTEXT, "lag(count(), ${Bytes})", "must be a whole number of windows");
        assertRefused(WINDOW_CONTEXT, "lag(count(), 1, count())", "The third parameter of 'lag' must be a fixed");
    }

    @Test
    void lagNeedsAWindowClause() {
        assertRefused(PLAIN_CONTEXT, "lag(count(), 1)",
                "'lag' can only be used in a query that has a window clause");
    }

    @Test
    void windowsOtherThanTheQuerysAreRefused() {
        assertRefused(WINDOW_CONTEXT, "lag(count(), 1, 'window:1h|||Z')",
                "uses the windows of the query's window clause");
        assertRefused(WINDOW_CONTEXT, "windowStart('window:1h|||Z')",
                "uses the windows of the query's window clause");
    }

    @Test
    void periodIsRefusedWithWhatToWriteInstead() {
        assertRefused(WINDOW_CONTEXT, "period(1, count())", "The 'period' function has been removed");
        assertRefused(WINDOW_CONTEXT, "period(1, count())", "lag(count(), 1)");
    }

    private static void assertRefused(final ExpressionContext expressionContext,
                                      final String expression,
                                      final String message) {
        assertThatThrownBy(() -> parse(expressionContext, expression))
                .as(expression)
                .isInstanceOf(TokenException.class)
                .hasMessageContaining(message);
    }

    private static Expression parse(final ExpressionContext expressionContext,
                                    final String expression) throws ParseException {
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create("Bytes");
        return new ExpressionParser(new ParamFactory(new HashMap<>()))
                .parse(expressionContext, fieldIndex, expression);
    }


    // --------------------------------------------------------------------------------


    // The row of one group in one window, as a result store gathers it: each row added is set on
    // values of its own, which are then merged into the row.
    private static final class Row {

        private final ValueReferenceIndex valueReferenceIndex = new ValueReferenceIndex();
        private final Generator generator;
        private StoredValues row;

        private Row(final String expression) throws ParseException {
            final Expression parsed = parse(WINDOW_CONTEXT, expression);
            parsed.addValueReferences(valueReferenceIndex);
            generator = parsed.createGenerator();
        }

        // Adds a row of a window to that window.
        private void addTo(final long windowKey) {
            add(0, windowKey);
        }

        private void add(final int windowShift) {
            add(windowShift, 0);
        }

        // Adds a row that is really a number of windows before this one, or in it if that is zero.
        private void add(final int windowShift, final long windowKey) {
            final StoredValues added = valueReferenceIndex.createStoredValues();
            added.setWindowKey(windowKey);
            added.setWindowShift(windowShift);
            final Val[] values = Val.of(ValNull.INSTANCE);
            if (windowShift == 0) {
                generator.set(values, added);
            } else {
                generator.setShifted(values, added);
            }
            if (row == null) {
                row = added;
            } else {
                generator.merge(row, added);
            }
        }

        private Val eval() {
            return generator.eval(row, null);
        }
    }
}
