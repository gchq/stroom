/*
 * Copyright 2016-2026 Crown Copyright
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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the jq() function where the arguments are not all constants, i.e. the path taken when the JSON comes from a
 * field, as it does in a dashboard.
 */
class TestExpressionParserJq extends AbstractExpressionParserTest {

    private static final String JSON = "{\"foo\":\"bar\", \"arr\":[1,2,3]}";

    @Test
    void testSingleMatch() {
        compute("jq(${val1}, '.foo')",
                Val.of(JSON),
                ValAssertions.valString("bar"));
    }

    @Test
    void testMultipleMatchesAreConcatenated() {
        compute("jq(${val1}, '.arr[]')",
                Val.of(JSON),
                ValAssertions.valString("123"));
    }

    @Test
    void testMultipleMatchesWithDelimiter() {
        compute("jq(${val1}, '.arr[]', ', ')",
                Val.of(JSON),
                ValAssertions.valString("1, 2, 3"));
    }

    @Test
    void testNoMatch() {
        compute("jq(${val1}, '.missing')",
                Val.of(JSON),
                ValAssertions.valNull());
    }

    @Test
    void testDynamicExpression() {
        compute("jq(${val1}, ${val2})",
                2,
                Val.of(JSON, ".arr[1]"),
                ValAssertions.valString("2"));
    }

    @Test
    void testInvalidJson() {
        compute("jq(${val1}, '.foo')",
                Val.of("{unclosed"),
                ValAssertions.valErrContainsIgnoreCase("was expecting double-quote to start field name"));
    }

    /**
     * The generator is created once per column per search but evaluated once per row, so a constant expression must
     * be compiled when the generator is created and never again.
     */
    @Test
    void testConstantExpressionIsCompiledOnce() {
        createGenerator("jq(${val1}, '.arr[]')", (gen, storedValues) -> {
            assertThat(field(gen, "constant"))
                    .as("The expression should have been compiled when the generator was created")
                    .isNotNull();

            for (int i = 0; i < 3; i++) {
                gen.set(Val.of(JSON), storedValues);
                assertThat(gen.eval(storedValues, null).toString()).isEqualTo("123");
            }

            assertThat(field(gen, "lastCompiled"))
                    .as("A constant expression should never be re-compiled during eval")
                    .isNull();
        });
    }

    /**
     * An expression that comes from a field can't be compiled up front, but it must only be compiled when it
     * actually changes rather than for every row.
     */
    @Test
    void testDynamicExpressionIsCompiledOncePerDistinctValue() {
        createGenerator("jq(${val1}, ${val2})", 2, (gen, storedValues) -> {
            assertThat(field(gen, "constant"))
                    .as("A non constant expression cannot be compiled up front")
                    .isNull();

            gen.set(Val.of(JSON, ".arr[0]"), storedValues);
            assertThat(gen.eval(storedValues, null).toString()).isEqualTo("1");
            final Object firstCompiled = field(gen, "lastCompiled");
            assertThat(firstCompiled).isNotNull();

            // The same expression again, so the previous compilation should be reused.
            for (int i = 0; i < 3; i++) {
                gen.set(Val.of(JSON, ".arr[0]"), storedValues);
                assertThat(gen.eval(storedValues, null).toString()).isEqualTo("1");
                assertThat(field(gen, "lastCompiled"))
                        .as("An unchanged expression should not be re-compiled")
                        .isSameAs(firstCompiled);
            }

            // A different expression must be compiled.
            gen.set(Val.of(JSON, ".arr[1]"), storedValues);
            assertThat(gen.eval(storedValues, null).toString()).isEqualTo("2");
            assertThat(field(gen, "lastCompiled")).isNotSameAs(firstCompiled);
        });
    }

    private static Object field(final Generator generator, final String name) {
        try {
            final java.lang.reflect.Field field = generator.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(generator);
        } catch (final NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Unable to read field '" + name + "' from " + generator.getClass(), e);
        }
    }
}
