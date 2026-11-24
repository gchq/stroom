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

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestCountUnique {

    private static final String MESSAGE = "countUnique() stopped counting at its limit of 3 unique values for one " +
                                          "or more groups. Use countUniqueApprox() to estimate higher counts.";

    private final List<String> warnings = new ArrayList<>();
    private final ValueReferenceIndex index = new ValueReferenceIndex();

    @Test
    void testNoWarningAtLimit() throws ParseException {
        final Generator generator = compile(3);
        final StoredValues storedValues = row(generator, "a", "b", "c", "a", "b", "c");
        assertThat(generator.eval(storedValues, null)).isEqualTo(ValInteger.create(3));
        assertThat(warnings).isEmpty();
    }

    @Test
    void testStopsAtLimit() throws ParseException {
        final Generator generator = compile(3);
        final StoredValues storedValues = row(generator, "a", "b", "c", "d", "e", "a");
        assertThat(generator.eval(storedValues, null)).isEqualTo(ValInteger.create(3));
        assertThat(warnings).containsExactly(MESSAGE);
    }

    @Test
    void testOneWarningForManyRows() throws ParseException {
        final Generator generator = compile(3);
        row(generator, "a", "b", "c", "d");
        row(generator, "e", "f", "g", "h");
        assertThat(warnings).containsExactly(MESSAGE);
    }

    @Test
    void testMergeStopsAtLimit() throws ParseException {
        final Generator generator = compile(3);
        final StoredValues existing = row(generator, "a", "b");
        generator.merge(existing, row(generator, "b", "c", "d"));
        assertThat(generator.eval(existing, null)).isEqualTo(ValInteger.create(3));
        assertThat(warnings).containsExactly(MESSAGE);

        // The values that were counted are the first ones.
        generator.merge(existing, row(generator, "a", "b", "c"));
        assertThat(generator.eval(existing, null)).isEqualTo(ValInteger.create(3));
    }

    @Test
    void testMergeToLimit() throws ParseException {
        final Generator generator = compile(3);
        final StoredValues existing = row(generator, "a", "b");
        generator.merge(existing, row(generator, "b", "c"));
        generator.merge(existing, row(generator, "c", "a"));
        assertThat(generator.eval(existing, null)).isEqualTo(ValInteger.create(3));
        assertThat(warnings).isEmpty();
    }

    // A row can have been stored with a higher limit.
    @Test
    void testRowOverLimitKeepsItsValues() throws ParseException {
        final Generator higher = compile(5);
        final StoredValues storedValues = row(higher, "a", "b", "c", "d", "e");

        final Generator generator = compile(3, new ValueReferenceIndex());
        generator.merge(storedValues, row(generator, "e"));
        assertThat(generator.eval(storedValues, null)).isEqualTo(ValInteger.create(5));
        assertThat(warnings).isEmpty();

        generator.merge(storedValues, row(generator, "f"));
        assertThat(generator.eval(storedValues, null)).isEqualTo(ValInteger.create(5));
        assertThat(warnings).containsExactly(MESSAGE);
    }

    @Test
    void testNullsDoNotCountTowardsLimit() throws ParseException {
        final Generator generator = compile(3);
        final StoredValues storedValues = row(generator, "a", "b", "c");
        generator.set(Val.of(ValNull.INSTANCE), storedValues);
        assertThat(generator.eval(storedValues, null)).isEqualTo(ValInteger.create(3));
        assertThat(warnings).isEmpty();
    }

    @Test
    void testDefaultLimit() {
        assertThat(new ExpressionContext().getMaxCountUniqueValues()).isEqualTo(10_000);
        assertThat(ExpressionContext.builder().build().getMaxCountUniqueValues()).isEqualTo(10_000);
        assertThat(ExpressionContext.builder().maxCountUniqueValues(5).build().copy().build())
                .isEqualTo(ExpressionContext.builder().maxCountUniqueValues(5).build())
                .isNotEqualTo(ExpressionContext.builder().build());
    }

    @Test
    void testNoContext() throws ParseException {
        final CountUnique countUnique = new CountUnique(null, CountUnique.NAME);
        countUnique.setParams(new Param[]{ValString.create("a")});
        countUnique.addValueReferences(index);
        assertThat(countUnique.createGenerator().eval(index.createStoredValues(), null))
                .isEqualTo(ValInteger.create(1));
    }

    private Generator compile(final int maxValues) throws ParseException {
        return compile(maxValues, index);
    }

    private Generator compile(final int maxValues, final ValueReferenceIndex index) throws ParseException {
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create("val");
        final Expression expression = new ExpressionParser(new ParamFactory(new HashMap<>())).parse(
                ExpressionContext.builder().maxCountUniqueValues(maxValues).build(),
                fieldIndex,
                "countUnique(${val})");
        expression.addValueReferences(index);
        index.getFunctionWarnings().setConsumer(warnings::add);
        return expression.createGenerator();
    }

    private StoredValues row(final Generator generator, final String... values) {
        final StoredValues storedValues = index.createStoredValues();
        for (final String value : values) {
            generator.set(Val.of(ValString.create(value)), storedValues);
        }
        return storedValues;
    }
}
