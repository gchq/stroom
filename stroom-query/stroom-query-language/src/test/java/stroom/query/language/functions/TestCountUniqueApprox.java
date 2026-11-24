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

class TestCountUniqueApprox {

    private static final String COUNT = "countUniqueApprox(${val})";
    // A stored field value, which is the same size for all longs.
    private static final int FIELD_SIZE = 1 + Long.BYTES;

    @ParameterizedTest
    @ValueSource(strings = {COUNT, "countUniqueApprox(${val}, 10)", "countUniqueApprox(${val}, 16)"})
    void testSmallCounts(final String expression) throws ParseException {
        final Compiled compiled = new Compiled(expression);
        assertThat(compiled.eval()).isEqualTo(ValLong.create(0));

        compiled.set(ValString.create("a"));
        assertThat(compiled.eval()).isEqualTo(ValLong.create(1));
        compiled.set(ValString.create("a"));
        assertThat(compiled.eval()).isEqualTo(ValLong.create(1));
        compiled.set(ValString.create("A"));
        assertThat(compiled.eval()).isEqualTo(ValLong.create(2));
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
        final Compiled compiled = new Compiled(COUNT);
        compiled.set(ValNull.INSTANCE);
        compiled.set(ValErr.create("bad"));
        assertThat(compiled.eval()).isEqualTo(ValLong.create(0));

        // Rows with nothing to count are merged too.
        final Compiled other = compiled.another();
        other.set(ValNull.INSTANCE);
        compiled.merge(other);
        assertThat(compiled.eval()).isEqualTo(ValLong.create(0));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "countUniqueApprox(${val}) | 0.0081",
            "countUniqueApprox(${val}, 10) | 0.0325",
            "countUniqueApprox(${val}, 12) | 0.0163",
            "countUniqueApprox(${val}, 16) | 0.0041"})
    void testLargeCountsMergedOneRowAtATime(final String expression, final double standardError)
            throws ParseException {
        // As the LMDB store does, reading, merging and writing the stored values for each event.
        final Compiled compiled = new Compiled(expression);
        byte[] stored = null;
        for (int i = 0; i < 30_000; i++) {
            final Compiled row = compiled.another();
            row.set(ValString.create("user" + i));
            if (stored == null) {
                stored = row.write();
            } else {
                final Compiled existing = compiled.read(stored);
                existing.merge(row);
                stored = existing.write();
            }
        }
        assertThat(compiled.read(stored).eval().toLong() / 30_000D)
                .isBetween(1 - 3 * standardError, 1 + 3 * standardError);
    }

    // There can be very many rows, so they must be as small as possible.
    @Test
    void testRowSize() throws ParseException {
        final Compiled compiled = new Compiled(COUNT);
        compiled.set(ValLong.create(0));
        // A length and then at most 4 bytes for a value.
        assertThat(compiled.write()).hasSizeLessThanOrEqualTo(FIELD_SIZE + 5);
        compiled.set(ValLong.create(1));
        assertThat(compiled.write()).hasSizeLessThanOrEqualTo(FIELD_SIZE + 9);
        for (int i = 0; i < 100; i++) {
            compiled.set(ValLong.create(i));
        }
        assertThat(compiled.write()).hasSizeLessThan(FIELD_SIZE + 2 + 100 * 4);

        // No value, but with a value for the field.
        final Compiled empty = compiled.another();
        empty.set(ValNull.INSTANCE);
        assertThat(empty.write()).hasSize(2);
    }

    @ParameterizedTest
    @CsvSource({"10,770", "11,1538", "12,3074", "13,6146", "14,12290", "15,24579", "16,49155"})
    void testLargeRowSize(final int precision, final int size) throws ParseException {
        final Compiled compiled = new Compiled("countUniqueApprox(${val}, " + precision + ")");
        for (int i = 0; i < 100_000; i++) {
            compiled.set(ValLong.create(i));
        }
        assertThat(compiled.write()).hasSize(FIELD_SIZE + size);
    }

    @Test
    void testMergeOfStores() throws ParseException {
        // As when rows come from other nodes.
        assertThat(mergeStores(ValLong::create)).isEqualTo(300);
        // With the same values on every node.
        assertThat(mergeStores(i -> ValLong.create(i % 100))).isEqualTo(100);
    }

    private long mergeStores(final IntFunction<Val> function) throws ParseException {
        final Compiled compiled = new Compiled(COUNT);
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
        final Compiled compiled = new Compiled(COUNT);
        final Compiled other = compiled.another();
        other.set(ValLong.create(1));
        compiled.merge(other);
        compiled.set(ValLong.create(2));
        assertThat(compiled.eval()).isEqualTo(ValLong.create(2));
        assertThat(other.eval()).isEqualTo(ValLong.create(1));
    }

    @Test
    void testShiftedRowsAreNotCounted() throws ParseException {
        final Compiled compiled = new Compiled(COUNT);
        compiled.set(ValLong.create(1));
        compiled.generator.setShifted(Val.of(ValLong.create(2)), compiled.storedValues);
        assertThat(compiled.eval()).isEqualTo(ValLong.create(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"countUniqueApprox('a')", "countUniqueApprox(5)", "countUniqueApprox('a', 12)"})
    void testStaticValue(final String expression) throws ParseException {
        final Compiled compiled = new Compiled(expression);
        compiled.set(ValLong.create(1));
        compiled.set(ValLong.create(2));
        assertThat(compiled.eval()).isEqualTo(ValLong.create(1));
        assertThat(compiled.write()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "countUniqueApprox() | Invalid number of parameters",
            "countUniqueApprox(${val}, 12, 1) | Invalid number of parameters",
            "countUniqueApprox(${val}, 9) | Whole number from 10 to 16 expected as second argument",
            "countUniqueApprox(${val}, 17) | Whole number from 10 to 16 expected as second argument",
            "countUniqueApprox(${val}, 10.5) | Whole number from 10 to 16 expected as second argument",
            "countUniqueApprox(${val}, 16.9) | Whole number from 10 to 16 expected as second argument",
            "countUniqueApprox('a', 9) | Whole number from 10 to 16 expected as second argument",
            "countUniqueApprox(${val}, 0) | Positive number expected as second argument",
            "countUniqueApprox(${val}, -12) | Positive number expected as second argument",
            "countUniqueApprox(${val}, 'x') | Number expected as second argument",
            "countUniqueApprox(${val}, ${val}) | Number expected as second argument",
            "countUniqueApprox(count()) | countUniqueApprox cannot be applied to aggregating function"})
    void testBadParams(final String expression, final String message) {
        assertThatThrownBy(() -> new Compiled(expression))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining(message);
    }


    // --------------------------------------------------------------------------------


    private static class Compiled {

        private final ValueReferenceIndex index;
        private final Generator generator;
        private final StoredValues storedValues;

        private Compiled(final String text) throws ParseException {
            final FieldIndex fieldIndex = new FieldIndex();
            fieldIndex.create("val");
            final Expression expression = new ExpressionParser(new ParamFactory(new HashMap<>()))
                    .parse(new ExpressionContext(), fieldIndex, text);
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
