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

package stroom.pipeline.refdata;

import stroom.pipeline.refdata.ReferenceDataResult.LazyMessage;
import stroom.test.common.TestUtil;
import stroom.util.shared.Severity;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

class TestReferenceDataResult {

    private final LookupIdentifier lookupIdentifier = LookupIdentifier.of(
            "map", "key", 123L);

    @TestFactory
    Stream<DynamicTest> testLog2() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, Object[].class)
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    final ReferenceDataResult referenceDataResult = new ReferenceDataResult(
                            lookupIdentifier, true, false);
                    final String template = testCase.getInput()._1;
                    final Object[] args = testCase.getInput()._2;

                    referenceDataResult.logSimpleTemplate(
                            Severity.INFO,
                            template,
                            args);

                    final List<LazyMessage> messages = referenceDataResult.getMessages();

                    Assertions.assertThat(messages)
                            .hasSize(1);
                    final LazyMessage lazyMessage = messages.get(0);
                    Assertions.assertThat(lazyMessage.getSeverity())
                            .isEqualTo(Severity.INFO);
                    return lazyMessage.getMessage();
                })
                .withSimpleEqualityAssertion()
                .addNamedCase("Simple string",
                        Tuple.of("Hello", (Object[]) null),
                        "Hello")
                .addNamedCase("One arg",
                        Tuple.of("Hello {}", new Object[]{"Bob"}),
                        "Hello Bob")
                .addNamedCase("Two args",
                        Tuple.of("{} {}", new Object[]{"foo", "bar"}),
                        "foo bar")
                .addNamedCase("No args",
                        Tuple.of("Hello {}", null),
                        "Hello {}")
                .addNamedCase("Missing arg",
                        Tuple.of("Hello {} {}", new Object[]{"foo"}),
                        "Hello foo {}")
                .addNamedCase("Too many args",
                        Tuple.of("Hello {}", new Object[]{"foo", "bar"}),
                        "Hello foo")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testLog2_supplier() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<String, Supplier<List<Object>>>>(){})
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    final ReferenceDataResult referenceDataResult = new ReferenceDataResult(
                            lookupIdentifier, true, false);
                    final String template = testCase.getInput()._1;
                    final Supplier<List<Object>> argsSupplier = testCase.getInput()._2;

                    referenceDataResult.logLazyTemplate(
                            Severity.INFO,
                            template,
                            argsSupplier);

                    final List<LazyMessage> messages = referenceDataResult.getMessages();

                    Assertions.assertThat(messages)
                            .hasSize(1);
                    final LazyMessage lazyMessage = messages.get(0);
                    Assertions.assertThat(lazyMessage.getSeverity())
                            .isEqualTo(Severity.INFO);
                    return lazyMessage.getMessage();
                })
                .withSimpleEqualityAssertion()
                .addNamedCase("Simple string",
                        Tuple.of("Hello", null),
                        "Hello")
                .addNamedCase("One arg",
                        Tuple.of("Hello {}", () -> List.of("Bob")),
                        "Hello Bob")
                .addNamedCase("Two args",
                        Tuple.of("{} {}", () -> List.of("foo", "bar")),
                        "foo bar")
                .addNamedCase("No args",
                        Tuple.of("Hello {}", null),
                        "Hello {}")
                .addNamedCase("Missing arg",
                        Tuple.of("Hello {} {}", () -> List.of("foo")),
                        "Hello foo {}")
                .addNamedCase("Too many args",
                        Tuple.of("Hello {}", () -> List.of("foo", "bar")),
                        "Hello foo")
                .build();
    }

    @Test
    void testThrowable() {
        final ReferenceDataResult referenceDataResult = new ReferenceDataResult(
                lookupIdentifier, true, false);

        referenceDataResult.logTemplate(
                Severity.ERROR,
                null,
                null,
                "Hello",
                new RuntimeException("doh"));

        final List<LazyMessage> messages = referenceDataResult.getMessages();

        Assertions.assertThat(messages)
                .hasSize(1);
        final LazyMessage lazyMessage = messages.get(0);
        Assertions.assertThat(lazyMessage.getSeverity())
                .isEqualTo(Severity.ERROR);

        final String message = lazyMessage.getMessage();
        Assertions.assertThat(message)
                .isEqualTo("Hello");
    }

    @Test
    void testThrowable2() {
        final ReferenceDataResult referenceDataResult = new ReferenceDataResult(
                lookupIdentifier, true, false);

        referenceDataResult.logTemplate(
                Severity.ERROR,
                null,
                null,
                "Hello {}",
                new RuntimeException("doh"),
                "Bob");

        final List<LazyMessage> messages = referenceDataResult.getMessages();

        Assertions.assertThat(messages)
                .hasSize(1);
        final LazyMessage lazyMessage = messages.get(0);
        Assertions.assertThat(lazyMessage.getSeverity())
                .isEqualTo(Severity.ERROR);

        final String message = lazyMessage.getMessage();
        Assertions.assertThat(message)
                .isEqualTo("Hello Bob");
    }
}
