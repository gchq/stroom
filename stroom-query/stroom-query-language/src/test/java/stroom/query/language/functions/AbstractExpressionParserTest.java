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

import stroom.bytebuffer.ByteArrayUtils;
import stroom.query.language.functions.ref.KryoDataReader;
import stroom.query.language.functions.ref.KryoDataWriter;
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferOutput;
import org.assertj.core.data.Offset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractExpressionParserTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractExpressionParserTest.class);

    protected final ExpressionParser parser = new ExpressionParser(new ParamFactory(new HashMap<>()));

    protected static Supplier<ChildData> createChildDataSupplier(final List<StoredValues> values) {
        return () -> new ChildData() {
            @Override
            public StoredValues first() {
                return values.getFirst();
            }

            @Override
            public StoredValues last() {
                return values.getLast();
            }

            @Override
            public StoredValues nth(final int pos) {
                return values.get(pos);
            }

            @Override
            public Iterable<StoredValues> top(final int limit) {
                return join(limit, false);
            }

            @Override
            public Iterable<StoredValues> bottom(final int limit) {
                return join(limit, true);
            }

            @Override
            public long count() {
                return values.size();
            }

            private Iterable<StoredValues> join(final int limit, final boolean trimTop) {
                final int start;
                final int end;
                if (trimTop) {
                    end = values.size() - 1;
                    start = Math.max(0, values.size() - limit);
                } else {
                    end = Math.min(limit, values.size()) - 1;
                    start = 0;
                }

                final List<StoredValues> list = new ArrayList<>();
                for (int i = start; i <= end; i++) {
                    final StoredValues val = values.get(i);
                    list.add(val);
                }
                return list;
            }
        };
    }

    protected static void testKryo(final ValueReferenceIndex valueReferenceIndex,
                                   final Generator generator,
                                   final StoredValues storedValues) {
        final Val val = generator.eval(storedValues, null);

        final ByteBuffer buffer;
        try (final UnsafeByteBufferOutput output = new UnsafeByteBufferOutput(1024, -1)) {
            try (final KryoDataWriter writer = new KryoDataWriter(output)) {
                valueReferenceIndex.write(storedValues, writer);
            }
            buffer = output.getByteBuffer();
        }
        buffer.flip();
        print(buffer);

        final StoredValues newStoredValues;
        try (final KryoDataReader reader = new KryoDataReader(new ByteBufferInput(buffer))) {
            newStoredValues = valueReferenceIndex.read(reader);
        }

        final Val newVal = generator.eval(newStoredValues, null);

        assertThat(newVal).isEqualTo(val);
    }

    protected static void print(final ByteBuffer byteBuffer) {
        final ByteBuffer copy = byteBuffer.duplicate();
        final byte[] bytes = new byte[copy.limit()];
        for (int i = 0; i < copy.limit(); i++) {
            bytes[i] = copy.get();
        }
        LOGGER.info("{} (hex) | {} (dec) | [{}] (string)",
                ByteArrayUtils.byteArrayToHex(bytes),
                Arrays.toString(bytes),
                new String(bytes));
    }

    protected static String valToString(final Val val) {
        return val.getClass().getSimpleName() + "(" + val + ")";
    }

//    protected static Val[] getVals(final String... str) {
//        final Val[] result = new Val[str.length];
//        for (int i = 0; i < result.length; i++) {
//            result[i] = ValString.create(str[i]);
//        }
//        return Val.of(result);
//    }
//
//    protected static Val[] getVals(final double... d) {
//        final Val[] result = new Val[d.length];
//        for (int i = 0; i < d.length; i++) {
//            result[i] = ValDouble.create(d[i]);
//        }
//        return Val.of(result);
//    }

    protected void test(final String expression) {
        createExpression(expression, exp ->
                System.out.println(exp.toString()));
    }

    protected void createGenerator(final String expression, final BiConsumer<Generator, StoredValues> consumer) {
        createGenerator(expression, 1, consumer);
    }

    protected void createExpression(final String expression, final Consumer<Expression> consumer) {
        createExpression(expression, 1, consumer);
    }

    protected void createGenerator(final String expression,
                                   final int valueCount,
                                   final BiConsumer<Generator, StoredValues> consumer) {
        createExpression(expression, valueCount, exp -> {
            final ValueReferenceIndex valueReferenceIndex = new ValueReferenceIndex();
            exp.addValueReferences(valueReferenceIndex);
            final StoredValues storedValues = valueReferenceIndex.createStoredValues();
            final Generator gen = exp.createGenerator();
            consumer.accept(gen, storedValues);
            testKryo(valueReferenceIndex, gen, storedValues);
        });
    }

    protected void testSelectors(final String expression,
                                 final IntStream intStream,
                                 final Consumer<Val> valConsumer) {
        createExpression(expression, 1, exp -> {
            final ValueReferenceIndex valueReferenceIndex = new ValueReferenceIndex();
            exp.addValueReferences(valueReferenceIndex);
            final StoredValues storedValues = valueReferenceIndex.createStoredValues();
            final Generator gen = exp.createGenerator();

            gen.set(Val.of(300), storedValues);
            final Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(300, Offset.offset(0D));

            final List<StoredValues> childValues = new ArrayList<>();
            intStream
                    .mapToObj(ValLong::create)
                    .forEach(v -> {
                        final StoredValues values = valueReferenceIndex.createStoredValues();
                        gen.set(Val.of(v), values);
                        childValues.add(values);
                    });
            final Supplier<ChildData> childDataSupplier = createChildDataSupplier(childValues);
            valConsumer.accept(gen.eval(storedValues, childDataSupplier));

            testKryo(valueReferenceIndex, gen, storedValues);
        });
    }

    protected void compute(final String expression,
                           final Val[] values,
                           final ValAssertion consumer) {
        compute(expression, 1, values, consumer);
    }

    protected void compute(final String expression,
                           final ValAssertion consumer) {
        compute(expression, 1, consumer);
    }

    protected void compute(final String expression,
                           final int valueCount,
                           final ValAssertion consumer) {
        createExpression(expression, valueCount, exp -> {
            final ValueReferenceIndex valueReferenceIndex = new ValueReferenceIndex();
            exp.addValueReferences(valueReferenceIndex);
            final StoredValues storedValues = valueReferenceIndex.createStoredValues();
            final Generator gen = exp.createGenerator();
            final Val out = gen.eval(storedValues, null);
            consumer.actual(out);
            testKryo(valueReferenceIndex, gen, storedValues);
        });
    }

    protected void compute(final String expression,
                           final int valueCount,
                           final Val[] values,
                           final ValAssertion valAssertion) {
        createExpression(expression, valueCount, exp -> {
            final ValueReferenceIndex valueReferenceIndex = new ValueReferenceIndex();
            exp.addValueReferences(valueReferenceIndex);
            final StoredValues storedValues = valueReferenceIndex.createStoredValues();
            final Generator gen = exp.createGenerator();
            gen.set(values, storedValues);
            final Val out = gen.eval(storedValues, null);
            valAssertion.actual(out);
            testKryo(valueReferenceIndex, gen, storedValues);
        });
    }

    protected void createExpression(final String expression,
                                    final int valueCount,
                                    final Consumer<Expression> consumer) {
        LOGGER.debug("expression: [{}]", expression);
        final ExpressionContext expressionContext = new ExpressionContext();
        final FieldIndex fieldIndex = new FieldIndex();
        for (int i = 1; i <= valueCount; i++) {
            fieldIndex.create("val" + i);
        }

        final Expression exp;
        try {
            exp = parser.parse(expressionContext, fieldIndex, expression);
        } catch (final ParseException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        final Map<String, String> mappedValues = new HashMap<>();
        mappedValues.put("testkey", "testvalue");
        exp.setStaticMappedValues(mappedValues);

        final String actual = exp.toString();
//        assertThat(actual)
//                .describedAs("Comparing the toString() of the parsed expression to the input")
//                .isEqualTo(expression);

        consumer.accept(exp);
    }

    protected void assertThatItEvaluatesToValErr(final String expression, final Val... values) {
        createGenerator(expression, (gen, storedValues) -> {
            gen.set(Val.of(values), storedValues);
            final Val out = gen.eval(storedValues, null);
            System.out.println(expression + " - " +
                               out.getClass().getSimpleName() + ": " +
                               out +
                               (out instanceof ValErr
                                       ? (" - " + ((ValErr) out).getMessage())
                                       : ""));
            assertThat(out).isInstanceOf(ValErr.class);
        });
    }

    protected void assertThatItEvaluatesTo(final String expression,
                                           final Val expectedOutput,
                                           final Val[] inputValues) {
        createGenerator(expression, (gen, storedValues) -> {
            if (inputValues != null && inputValues.length > 0) {
                gen.set(inputValues, storedValues);
            }
            final Val out = gen.eval(storedValues, null);
            System.out.println(expression + " - " +
                               out.getClass().getSimpleName() + ": " +
                               out +
                               (out instanceof ValErr
                                       ? (" - " + ((ValErr) out).getMessage())
                                       : ""));
            assertThat(out)
                    .isEqualTo(expectedOutput);
        });
    }

    protected void assertBooleanExpression(final Val val1,
                                           final String operator,
                                           final Val val2,
                                           final Val expectedOutput) {
        final String expression = String.format("(${val1}%s${val2})", operator);
        createGenerator(expression, 2, (gen, storedValues) -> {
            gen.set(Val.of(val1, val2), storedValues);
            final Val out = gen.eval(storedValues, null);

            System.out.printf("[%s: %s] %s [%s: %s] => [%s: %s%s]%n",
                    val1.getClass().getSimpleName(), val1,
                    operator,
                    val2.getClass().getSimpleName(), val2,
                    out.getClass().getSimpleName(), out,
                    (out instanceof ValErr
                            ? (" - " + ((ValErr) out).getMessage())
                            : ""));

            if (!(expectedOutput instanceof ValErr)) {
                assertThat(out).isEqualTo(expectedOutput);
            }
            assertThat(out.getClass()).isEqualTo(expectedOutput.getClass());
        });
    }

    protected void assertTypeOf(final String expression, final String expectedType) {
        createGenerator(expression, (gen, storedValues) -> {
            final Val out = gen.eval(storedValues, null);

            System.out.printf("%s => [%s:%s%s]%n",
                    expression,
                    out.getClass().getSimpleName(), out.toString(),
                    (out instanceof ValErr
                            ? (" - " + ((ValErr) out).getMessage())
                            : ""));

            // The output type is always wrapped in a ValString
            assertThat(out.type().toString()).isEqualTo("string");

            assertThat(out).isInstanceOf(ValString.class);
            assertThat(out.toString()).isEqualTo(expectedType);
        });
    }

    protected void assertTypeOf(final Val val1, final String expectedType) {
        final String expression = "typeOf(${val1})";
        createGenerator(expression, (gen, storedValues) -> {
            gen.set(Val.of(val1), storedValues);
            final Val out = gen.eval(storedValues, null);

            System.out.printf("%s - [%s:%s] => [%s:%s%s]%n",
                    expression,
                    val1.getClass().getSimpleName(), val1.toString(),
                    out.getClass().getSimpleName(), out.toString(),
                    (out instanceof ValErr
                            ? (" - " + ((ValErr) out).getMessage())
                            : ""));

            // The output type is always wrapped in a ValString
            assertThat(out.type().toString()).isEqualTo("string");

            assertThat(out).isInstanceOf(ValString.class);
            assertThat(out.toString()).isEqualTo(expectedType);
        });
    }

    protected void assertIsExpression(final Val val1, final String function, final Val expectedOutput) {
        final String expression = String.format("%s(${val1})", function);
        createGenerator(expression, 2, (gen, storedValues) -> {
            gen.set(Val.of(val1), storedValues);
            final Val out = gen.eval(storedValues, null);

            System.out.printf("%s([%s: %s]) => [%s: %s%s]%n",
                    function,
                    val1.getClass().getSimpleName(), val1,
                    out.getClass().getSimpleName(), out,
                    (out instanceof ValErr
                            ? (" - " + ((ValErr) out).getMessage())
                            : ""));

            if (!(expectedOutput instanceof ValErr)) {
                assertThat(out).isEqualTo(expectedOutput);
            }
            assertThat(out.getClass()).isEqualTo(expectedOutput.getClass());
        });
    }


    // --------------------------------------------------------------------------------


    protected static class TestCase {

        protected final String expression;
        protected final Val expectedResult;
        protected final Val[] inputValues;

        TestCase(final String expression, final Val expectedResult, final Val[] inputValues) {
            this.expression = expression;
            this.expectedResult = expectedResult;
            this.inputValues = inputValues;
        }

        static TestCase of(final String expression, final Val expectedResult, final Val... inputValues) {
            return new TestCase(expression, expectedResult, Val.of(inputValues));
        }

//        static TestCase of(final String expression, final Val expectedResult, final Val[] inputValues) {
//            return new TestCase(expression, expectedResult, inputValues);
//        }

        @Override
        public String toString() {
            final String inputValuesStr = inputValues == null || inputValues.length == 0
                    ? ""
                    : Arrays.stream(inputValues)
                            .map(TestExpressionParser::valToString)
                            .collect(Collectors.joining(", "));
            return
                    "Expr: \"" + expression
                    + "\", inputs: ["
                    + inputValuesStr + "], expResult: "
                    + valToString(expectedResult);
        }
    }
}
