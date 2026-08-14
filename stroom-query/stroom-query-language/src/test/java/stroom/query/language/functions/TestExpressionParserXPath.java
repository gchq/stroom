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

import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;
import stroom.util.xml.XMLReaderPool;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests the xpath() function where the arguments are not all constants, i.e. the path taken when the XML comes from
 * a field, as it does in a dashboard.
 */
class TestExpressionParserXPath extends AbstractExpressionParserTest {

    private static final String XML = "<root><nested><item id=\"1\">item1</item>"
                                      + "<item id=\"2\">item2</item></nested></root>";
    private static final String NAMESPACED_XML =
            "<root xmlns=\"event-logging:3\"><item>item1</item><item>item2</item></root>";

    @Test
    void testSingleMatch() {
        compute("xpath(${val1}, '/root/nested/item[1]')",
                Val.of(XML),
                ValAssertions.valString("item1"));
    }

    @Test
    void testMultipleMatchesAreConcatenated() {
        compute("xpath(${val1}, '//item')",
                Val.of(XML),
                ValAssertions.valString("item1item2"));
    }

    @Test
    void testMultipleMatchesWithDelimiter() {
        compute("xpath(${val1}, '//item', '', ', ')",
                Val.of(XML),
                ValAssertions.valString("item1, item2"));
    }

    @Test
    void testNoMatch() {
        compute("xpath(${val1}, '/root/missing')",
                Val.of(XML),
                ValAssertions.valString(""));
    }

    @Test
    void testNamespacesIgnoredByDefault() {
        compute("xpath(${val1}, '//item', '', '|')",
                Val.of(NAMESPACED_XML),
                ValAssertions.valString("item1|item2"));
    }

    @Test
    void testNamespaceAware() {
        compute("xpath(${val1}, '//e:item', 'e:event-logging:3', '|')",
                Val.of(NAMESPACED_XML),
                ValAssertions.valString("item1|item2"));
    }

    @Test
    void testDefaultElementNamespace() {
        compute("xpath(${val1}, '//item', ':event-logging:3', '|')",
                Val.of(NAMESPACED_XML),
                ValAssertions.valString("item1|item2"));
    }

    @Test
    void testXPath2Expression() {
        compute("xpath(${val1}, 'string-join(//item/@id, \\'-\\')')",
                Val.of(XML),
                ValAssertions.valString("1-2"));
    }

    @Test
    void testDynamicXPath() {
        compute("xpath(${val1}, ${val2})",
                2,
                Val.of(XML, "//item[2]"),
                ValAssertions.valString("item2"));
    }

    @Test
    void testDynamicDelimiter() {
        compute("xpath(${val1}, '//item', '', ${val2})",
                2,
                Val.of(XML, "-"),
                ValAssertions.valString("item1-item2"));
    }

    @Test
    void testDynamicNamespaces() {
        compute("xpath(${val1}, '//e:item', ${val2}, '|')",
                2,
                Val.of(NAMESPACED_XML, "e:event-logging:3"),
                ValAssertions.valString("item1|item2"));
    }

    /**
     * The generator is created once per column per search but evaluated once per row, so a constant expression
     * must be compiled when the generator is created and never again.
     */
    @Test
    void testConstantExpressionIsCompiledOnce() {
        createGenerator("xpath(${val1}, '//item')", (gen, storedValues) -> {
            assertThat(field(gen, "constant"))
                    .as("The expression should have been compiled when the generator was created")
                    .isNotNull();

            for (int i = 0; i < 3; i++) {
                gen.set(Val.of(XML), storedValues);
                assertThat(gen.eval(storedValues, null).toString()).isEqualTo("item1item2");
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
        createGenerator("xpath(${val1}, ${val2})", 2, (gen, storedValues) -> {
            assertThat(field(gen, "constant"))
                    .as("A non constant expression cannot be compiled up front")
                    .isNull();

            gen.set(Val.of(XML, "//item[1]"), storedValues);
            assertThat(gen.eval(storedValues, null).toString()).isEqualTo("item1");
            final Object firstCompiled = field(gen, "lastCompiled");
            assertThat(firstCompiled).isNotNull();

            // The same expression again, so the previous compilation should be reused.
            for (int i = 0; i < 3; i++) {
                gen.set(Val.of(XML, "//item[1]"), storedValues);
                assertThat(gen.eval(storedValues, null).toString()).isEqualTo("item1");
                assertThat(field(gen, "lastCompiled"))
                        .as("An unchanged expression should not be re-compiled")
                        .isSameAs(firstCompiled);
            }

            // A different expression must be compiled.
            gen.set(Val.of(XML, "//item[2]"), storedValues);
            assertThat(gen.eval(storedValues, null).toString()).isEqualTo("item2");
            assertThat(field(gen, "lastCompiled")).isNotSameAs(firstCompiled);
        });
    }

    /**
     * Parsers are expensive to create so they are pooled rather than created for every row.
     */
    @Test
    void testParserIsBorrowedAndReturned() {
        final XMLReaderPool pool = XMLReaderPool.getDefault();
        pool.clear();

        createGenerator("xpath(${val1}, '//item')", (gen, storedValues) -> {
            for (int i = 0; i < 3; i++) {
                gen.set(Val.of(XML), storedValues);
                assertThat(gen.eval(storedValues, null).toString()).isEqualTo("item1item2");

                assertThat(pool.size())
                        .as("The parser should have been returned to the pool for the next row to borrow")
                        .isEqualTo(1);
            }
        });
    }

    /**
     * A parser that failed shouldn't be put back in the pool as we don't know what state it is in.
     */
    @Test
    void testFailedParserIsNotPooled() {
        final XMLReaderPool pool = XMLReaderPool.getDefault();
        pool.clear();

        createGenerator("xpath(${val1}, '//item')", (gen, storedValues) -> {
            gen.set(Val.of("<root>unclosed"), storedValues);
            assertThat(gen.eval(storedValues, null)).isInstanceOf(ValErr.class);
            assertThat(pool.size()).isZero();
        });
    }

    /**
     * Pooled parsers must keep the hardening applied when they were created, i.e. we must never resolve external
     * entities in XML supplied to the function.
     */
    @Test
    void testExternalEntitiesAreDisabled() {
        final String xxe = "<?xml version=\"1.0\"?><!DOCTYPE root ["
                           + "<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><root><item>&xxe;</item></root>";

        createGenerator("xpath(${val1}, '//item')", (gen, storedValues) -> {
            for (int i = 0; i < 2; i++) {
                gen.set(Val.of(xxe), storedValues);
                final Val val = gen.eval(storedValues, null);
                assertThat(val).isInstanceOf(ValErr.class);
                assertThat(val.toString()).containsIgnoringCase("DOCTYPE is disallowed");
            }
        });
    }

    /**
     * A single generator is shared by all the threads adding rows to a data store, with the per row state held in
     * StoredValues, so the compiled expression is evaluated and parsers are borrowed from the pool concurrently.
     */
    @Test
    void testGeneratorCanBeSharedBetweenThreads() {
        // Namespace unaware so that every parse also goes through a namespace stripping filter.
        createExpression("xpath(${val1}, '//item', '', '-')", 1, exp -> {
            final ValueReferenceIndex valueReferenceIndex = new ValueReferenceIndex();
            exp.addValueReferences(valueReferenceIndex);
            // One generator, shared by every thread, as it is in a data store.
            final Generator generator = exp.createGenerator();

            final int threadCount = 8;
            final int iterations = 200;
            final ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            try {
                final CountDownLatch startLatch = new CountDownLatch(1);
                final List<Future<?>> futures = new ArrayList<>();
                for (int i = 0; i < threadCount; i++) {
                    futures.add(executorService.submit(() -> {
                        startLatch.await();
                        for (int j = 0; j < iterations; j++) {
                            // Each row gets its own values, as it does in a data store.
                            final StoredValues storedValues = valueReferenceIndex.createStoredValues();
                            generator.set(Val.of(NAMESPACED_XML), storedValues);
                            assertThat(generator.eval(storedValues, null).toString()).isEqualTo("item1-item2");
                        }
                        return null;
                    }));
                }

                startLatch.countDown();
                for (final Future<?> future : futures) {
                    // Any assertion failure or exception on a worker thread surfaces here.
                    assertThatCode(future::get).doesNotThrowAnyException();
                }
            } finally {
                executorService.shutdownNow();
            }

            // The pool is bounded so concurrent use can't have made it grow without limit.
            assertThat(XMLReaderPool.getDefault().size()).isLessThanOrEqualTo(threadCount);
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


    @Test
    void testInvalidXml() {
        compute("xpath(${val1}, '/root')",
                Val.of("<root>unclosed"),
                ValAssertions.valErrContainsIgnoreCase("must start and end within the same entity"));
    }

    @Test
    void testInvalidNamespaces() {
        compute("xpath(${val1}, '//item', ${val2})",
                2,
                Val.of(XML, "bad"),
                ValAssertions.valErrContainsIgnoreCase("prefix:uri"));
    }
}
