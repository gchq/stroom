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

import stroom.query.api.token.TokenException;
import stroom.query.language.functions.ref.KryoDataReader;
import stroom.query.language.functions.ref.KryoDataWriter;
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.ParseException;
import java.util.HashMap;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestCountDistinct {

    private static final String COUNT = "countDistinct(${val})";
    // A stored field value, which is the same size for all longs.
    private static final int FIELD_SIZE = 1 + Long.BYTES;

    @Test
    void testCounts() throws ParseException {
        final Compiled compiled = new Compiled(COUNT, 10);
        assertThat(compiled.eval()).isEqualTo(ValLong.create(0));
        compiled.set(ValString.create("a"));
        compiled.set(ValString.create("a"));
        assertThat(compiled.eval()).isEqualTo(ValLong.create(1));
        compiled.set(ValString.create("A"));
        compiled.set(ValString.create(""));
        assertThat(compiled.eval()).isEqualTo(ValLong.create(3));
        // Values of different types are different, as they are to countUnique().
        compiled.set(ValInteger.create(1));
        compiled.set(ValLong.create(1));
        compiled.set(ValString.create("1"));
        assertThat(compiled.eval()).isEqualTo(ValLong.create(6));
    }

    @Test
    void testNullsAndErrorsAreNotCounted() throws ParseException {
        final Compiled compiled = new Compiled(COUNT, 1);
        compiled.set(ValNull.INSTANCE);
        compiled.set(ValErr.create("bad"));
        assertThat(compiled.eval()).isEqualTo(ValLong.create(0));
        compiled.set(ValString.create("a"));
        compiled.set(ValNull.INSTANCE);
        assertThat(compiled.eval()).isEqualTo(ValLong.create(1));

        // Rows with nothing to count are merged too.
        final Compiled other = compiled.another();
        other.set(ValNull.INSTANCE);
        compiled.merge(other);
        assertThat(compiled.eval()).isEqualTo(ValLong.create(1));
    }

    @Test
    void testEstimateOverLimit() throws ParseException {
        final Compiled compiled = new Compiled(COUNT, 10_000);
        setAll(compiled, 0, 10_000);
        setAll(compiled, 0, 10_000);
        assertThat(compiled.eval()).isEqualTo(ValLong.create(10_000));
        setAll(compiled, 10_000, 30_000);
        assertSameAsDCount(compiled, 30_000);
    }

    @Test
    void testMergeOverLimit() throws ParseException {
        final Compiled compiled = new Compiled(COUNT, 10_000);
        setAll(compiled, 0, 7_000);
        final Compiled toLimit = compiled.another();
        setAll(toLimit, 4_000, 10_000);
        compiled.merge(toLimit);
        assertThat(compiled.eval()).isEqualTo(ValLong.create(10_000));

        final Compiled over = compiled.another();
        setAll(over, 9_000, 25_000);
        compiled.merge(over);
        assertSameAsDCount(compiled, 25_000);

        // Merged both ways round with a row that is under.
        toLimit.merge(compiled);
        assertSameAsDCount(toLimit, 25_000);
        compiled.merge(compiled.another());
        assertSameAsDCount(compiled, 25_000);
    }

    // As the LMDB store does, reading, merging and writing the stored values for each event.
    @Test
    void testMergedOneRowAtATime() throws ParseException {
        final Compiled compiled = new Compiled(COUNT, 2_000);
        byte[] stored = null;
        for (int i = 0; i < 4_001; i++) {
            final Compiled row = compiled.another();
            row.set(ValString.create("user" + i / 2));
            final Compiled existing = stored == null
                    ? compiled.another()
                    : compiled.read(stored);
            existing.merge(row);
            stored = existing.write();
            if (i == 3_999) {
                assertThat(compiled.read(stored).eval()).isEqualTo(ValLong.create(2_000));
            }
        }
        assertThat(compiled.read(stored).eval().toLong()).isBetween(1_990L, 2_012L);
    }

    @Test
    void testMergeOfStores() throws ParseException {
        assertThat(mergeStores(ValLong::create)).isEqualTo(300);
        // With the same values on every node.
        assertThat(mergeStores(i -> ValLong.create(i % 100))).isEqualTo(100);
        // With half of the values on another node.
        assertThat(mergeStores(i -> ValLong.create(i / 100 * 50 + i % 100))).isEqualTo(200);
    }

    private long mergeStores(final IntFunction<Val> function) throws ParseException {
        final Compiled compiled = new Compiled(COUNT, 300);
        for (int node = 0; node < 3; node++) {
            final Compiled other = compiled.another();
            for (int i = 0; i < 100; i++) {
                other.set(function.apply(node * 100 + i));
            }
            compiled.merge(compiled.read(other.write()));
        }
        return compiled.eval().toLong();
    }

    // Merging must not join the two rows together, so that a later change to one changes the other.
    @Test
    void testMergedRowsAreSeparate() throws ParseException {
        final Compiled compiled = new Compiled(COUNT, 10);
        final Compiled other = compiled.another();
        setAll(other, 0, 2);
        compiled.merge(other);
        setAll(compiled, 2, 3);
        assertThat(compiled.eval()).isEqualTo(ValLong.create(3));
        assertThat(other.eval()).isEqualTo(ValLong.create(2));
    }

    // There can be very many rows, so they must be as small as possible.
    @Test
    void testRowSize() throws ParseException {
        final Compiled compiled = new Compiled(COUNT, 10_000);
        final Compiled empty = compiled.another();
        empty.set(ValNull.INSTANCE);
        assertThat(empty.write()).hasSize(2);

        setAll(compiled, 0, 1);
        assertThat(compiled.write()).hasSize(FIELD_SIZE + 1 + Long.BYTES);
        setAll(compiled, 0, 10_000);
        assertThat(compiled.write()).hasSize(FIELD_SIZE + 2 + 10_000 * Long.BYTES);
        // A byte and then the sketch.
        setAll(compiled, 10_000, 10_001);
        assertThat(compiled.write()).hasSize(FIELD_SIZE + 1 + 12_290);
    }

    // A rule's rows can have been stored with another limit.
    @Test
    void testChangedLimit() throws ParseException {
        final Compiled high = new Compiled(COUNT, 20_000);
        final Compiled low = new Compiled(COUNT, 10_000);
        setAll(high, 0, 20_000);
        final byte[] exact = high.write();
        assertSameAsDCount(low.read(exact), 20_000);
        assertThat(high.read(exact).eval()).isEqualTo(ValLong.create(20_000));

        // It stays an estimate.
        setAll(low, 0, 15_000);
        assertSameAsDCount(high.read(low.write()), 15_000);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void testInvalidLimit(final int limit) throws ParseException {
        final Compiled compiled = new Compiled(COUNT, limit);
        setAll(compiled, 0, 3);
        assertThat(compiled.read(compiled.write()).eval()).isEqualTo(ValLong.create(3));
    }

    @Test
    void testDefaultLimit() throws ParseException {
        assertThat(new ExpressionContext().getMaxCountDistinctValues()).isEqualTo(10_000);
        assertThat(ExpressionContext.builder().build().getMaxCountDistinctValues()).isEqualTo(10_000);
        assertThat(ExpressionContext.builder().maxCountDistinctValues(5).build().copy().build())
                .isEqualTo(ExpressionContext.builder().maxCountDistinctValues(5).build())
                .isNotEqualTo(ExpressionContext.builder().build());

        final CountDistinct countDistinct = new CountDistinct(null, CountDistinct.NAME);
        countDistinct.setParams(new Param[]{ValString.create("a")});
        final ValueReferenceIndex index = new ValueReferenceIndex();
        countDistinct.addValueReferences(index);
        assertThat(countDistinct.createGenerator().eval(index.createStoredValues(), null))
                .isEqualTo(ValLong.create(1));
    }

    @Test
    void testShiftedRowsAreNotCounted() throws ParseException {
        final Compiled compiled = new Compiled(COUNT, 10);
        compiled.set(ValLong.create(1));
        compiled.generator.setShifted(Val.of(ValLong.create(2)), compiled.storedValues);
        assertThat(compiled.eval()).isEqualTo(ValLong.create(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"countDistinct('a')", "countDistinct(5)"})
    void testStaticValue(final String expression) throws ParseException {
        final Compiled compiled = new Compiled(expression, 10);
        compiled.set(ValLong.create(1));
        compiled.set(ValLong.create(2));
        assertThat(compiled.eval()).isEqualTo(ValLong.create(1));
        assertThat(compiled.write()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "countDistinct() | Invalid number of parameters",
            "countDistinct(${val}, 1) | Invalid number of parameters",
            "countDistinct(count()) | countDistinct cannot be applied to aggregating function"})
    void testBadParams(final String expression, final String message) {
        assertThatThrownBy(() -> new Compiled(expression, 10))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining(message);
    }

    private void setAll(final Compiled compiled, final int from, final int to) {
        for (int i = from; i < to; i++) {
            compiled.set(ValLong.create(i));
        }
    }

    private void assertSameAsDCount(final Compiled compiled, final int values) throws ParseException {
        final Compiled dcount = new Compiled("dcount(${val})", 1);
        setAll(dcount, 0, values);
        assertThat(compiled.eval()).isEqualTo(dcount.eval());
        // Within 3 standard errors.
        assertThat(compiled.eval().toLong() / (double) values).isBetween(0.9757, 1.0243);
    }


    // --------------------------------------------------------------------------------


    private static class Compiled {

        private final ValueReferenceIndex index;
        private final Generator generator;
        private final StoredValues storedValues;

        private Compiled(final String text, final int limit) throws ParseException {
            final FieldIndex fieldIndex = new FieldIndex();
            fieldIndex.create("val");
            final Expression expression = new ExpressionParser(new ParamFactory(new HashMap<>()))
                    .parse(ExpressionContext.builder().maxCountDistinctValues(limit).build(), fieldIndex, text);
            index = new ValueReferenceIndex();
            expression.addValueReferences(index);
            generator = expression.createGenerator();
            storedValues = index.createStoredValues();
        }

        private Compiled(final ValueReferenceIndex index,
                         final Generator generator,
                         final StoredValues storedValues) {
            this.index = index;
            this.generator = generator;
            this.storedValues = storedValues;
        }

        private Compiled another() {
            return new Compiled(index, generator, index.createStoredValues());
        }

        private Compiled read(final byte[] bytes) {
            return new Compiled(index, generator, index.read(new KryoDataReader(new Input(bytes))));
        }

        private void set(final Val val) {
            generator.set(Val.of(val), storedValues);
        }

        private void merge(final Compiled other) {
            generator.merge(storedValues, other.storedValues);
        }

        private Val eval() {
            return generator.eval(storedValues, null);
        }

        private byte[] write() {
            final Output output = new Output(64, -1);
            index.write(storedValues, new KryoDataWriter(output));
            return output.toBytes();
        }
    }
}
