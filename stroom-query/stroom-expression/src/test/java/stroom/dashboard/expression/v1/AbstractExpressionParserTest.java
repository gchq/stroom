package stroom.dashboard.expression.v1;

import com.esotericsoftware.kryo.io.ByteBufferInputStream;
import com.esotericsoftware.kryo.io.ByteBufferOutputStream;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractExpressionParserTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractExpressionParserTest.class);

    protected final ExpressionParser parser = new ExpressionParser(new ParamFactory());

    protected static Supplier<ChildData> createChildDataSupplier(final List<Val> values) {
        return () -> new ChildData() {
            @Override
            public Val first() {
                return values.get(0);
            }

            @Override
            public Val last() {
                return values.get(values.size() - 1);
            }

            @Override
            public Val nth(final int pos) {
                return values.get(pos);
            }

            @Override
            public Val top(final String delimiter, final int limit) {
                return join(delimiter, limit, false);
            }

            @Override
            public Val bottom(final String delimiter, final int limit) {
                return join(delimiter, limit, true);
            }

            @Override
            public Val count() {
                return ValLong.create(values.size());
            }

            private Val join(final String delimiter, final int limit, final boolean trimTop) {
                int start;
                int end;
                if (trimTop) {
                    end = values.size() - 1;
                    start = Math.max(0, values.size() - limit);
                } else {
                    end = Math.min(limit, values.size()) - 1;
                    start = 0;
                }

                final StringBuilder sb = new StringBuilder();
                for (int i = start; i <= end; i++) {
                    final Val val = values.get(i);
                    if (val.type().isValue()) {
                        if (sb.length() > 0) {
                            sb.append(delimiter);
                        }
                        sb.append(val);
                    }
                }
                return ValString.create(sb.toString());
            }
        };
    }

    protected static void testKryo(final Generator inputGenerator, final Generator outputGenerator) {
        final Val val = inputGenerator.eval(null);

        ByteBuffer buffer = ByteBuffer.allocateDirect(1000);

        try (final Output output = new Output(new ByteBufferOutputStream(buffer))) {
            inputGenerator.write(output);
        }

        buffer.flip();
        print(buffer);

        try (final Input input = new Input(new ByteBufferInputStream(buffer))) {
            outputGenerator.read(input);
        }

        final Val newVal = outputGenerator.eval(null);

        assertThat(newVal).isEqualTo(val);
    }

    protected static void print(final ByteBuffer byteBuffer) {
        final ByteBuffer copy = byteBuffer.duplicate();
        byte[] bytes = new byte[copy.limit()];
        for (int i = 0; i < copy.limit(); i++) {
            bytes[i] = copy.get();
        }
        LOGGER.info(Arrays.toString(bytes));
    }

    protected static String valToString(final Val val) {
        return val.getClass().getSimpleName() + "(" + val + ")";
    }

    protected static Val[] getVals(final String... str) {
        final Val[] result = new Val[str.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = ValString.create(str[i]);
        }
        return result;
    }

    protected static Val[] getVals(final double... d) {
        final Val[] result = new Val[d.length];
        for (int i = 0; i < d.length; i++) {
            result[i] = ValDouble.create(d[i]);
        }
        return result;
    }

    protected void test(final String expression) {
        createExpression(expression, exp ->
                System.out.println(exp.toString()));
    }

    protected void createGenerator(final String expression, final Consumer<Generator> consumer) {
        createGenerator(expression, 1, consumer);
    }

    protected void createExpression(final String expression, final Consumer<Expression> consumer) {
        createExpression(expression, 1, consumer);
    }

    protected void createGenerator(final String expression,
                                   final int valueCount,
                                   final Consumer<Generator> consumer) {
        createExpression(expression, valueCount, exp -> {
            final Generator gen = exp.createGenerator();
            consumer.accept(gen);

            final Generator generator2 = exp.createGenerator();
            testKryo(gen, generator2);
        });
    }

    protected void createExpression(final String expression,
                                    final int valueCount,
                                    final Consumer<Expression> consumer) {
        final FieldIndex fieldIndex = new FieldIndex();
        for (int i = 1; i <= valueCount; i++) {
            fieldIndex.create("val" + i);
        }

        Expression exp;
        try {
            exp = parser.parse(fieldIndex, expression);
        } catch (final ParseException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        final Map<String, String> mappedValues = new HashMap<>();
        mappedValues.put("testkey", "testvalue");
        exp.setStaticMappedValues(mappedValues);

        final String actual = exp.toString();
        assertThat(actual)
                .describedAs("Comparing the toString() of the parsed expression to the input")
                .isEqualTo(expression);

        consumer.accept(exp);
    }

    protected void assertThatItEvaluatesToValErr(final String expression, final Val... values) {
        createGenerator(expression, gen -> {
            gen.set(values);
            Val out = gen.eval(null);
            System.out.println(expression + " - " +
                    out.getClass().getSimpleName() + ": " +
                    out.toString() +
                    (out instanceof ValErr
                            ? (" - " + ((ValErr) out).getMessage())
                            : ""));
            assertThat(out).isInstanceOf(ValErr.class);
        });
    }

    protected void assertThatItEvaluatesTo(final String expression,
                                           final Val expectedOutput,
                                           final Val... inputValues) {
        createGenerator(expression, gen -> {
            if (inputValues != null && inputValues.length > 0) {
                gen.set(inputValues);
            }
            final Val out = gen.eval(null);
            System.out.println(expression + " - " +
                    out.getClass().getSimpleName() + ": " +
                    out.toString() +
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
        createGenerator(expression, 2, gen -> {
            gen.set(new Val[]{val1, val2});
            Val out = gen.eval(null);

            System.out.printf("[%s: %s] %s [%s: %s] => [%s: %s%s]%n",
                    val1.getClass().getSimpleName(), val1.toString(),
                    operator,
                    val2.getClass().getSimpleName(), val2.toString(),
                    out.getClass().getSimpleName(), out.toString(),
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
        createGenerator(expression, gen -> {
            Val out = gen.eval(null);

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
        createGenerator(expression, gen -> {
            gen.set(new Val[]{val1});
            Val out = gen.eval(null);

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
        createGenerator(expression, 2, gen -> {
            gen.set(new Val[]{val1});
            Val out = gen.eval(null);

            System.out.printf("%s([%s: %s]) => [%s: %s%s]%n",
                    function,
                    val1.getClass().getSimpleName(), val1.toString(),
                    out.getClass().getSimpleName(), out.toString(),
                    (out instanceof ValErr
                            ? (" - " + ((ValErr) out).getMessage())
                            : ""));

            if (!(expectedOutput instanceof ValErr)) {
                assertThat(out).isEqualTo(expectedOutput);
            }
            assertThat(out.getClass()).isEqualTo(expectedOutput.getClass());
        });
    }

    protected static class TestCase {

        protected final String expression;
        protected final Val expectedResult;
        protected final Val[] inputValues;

        TestCase(final String expression, final Val expectedResult, final Val... inputValues) {
            this.expression = expression;
            this.expectedResult = expectedResult;
            this.inputValues = inputValues;
        }

        static TestCase of(final String expression, final Val expectedResult, final Val... inputValues) {
            return new TestCase(expression, expectedResult, inputValues);
        }

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
