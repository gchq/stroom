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

package stroom.pipeline.errorhandler;

import stroom.task.api.TaskTerminatedException;
import stroom.test.common.TestUtil;
import stroom.util.concurrent.UncheckedInterruptedException;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.nio.channels.ClosedByInterruptException;
import java.util.stream.Stream;
import javax.xml.transform.TransformerException;

import static org.assertj.core.api.Assertions.assertThat;

class TestProcessException {

    @Test
    void create1() {
        final ProcessException e = ProcessException.create("foo");
        assertThat(e)
                .isNotNull()
                .hasMessage("foo");
    }

    @Test
    void create1_null() {
        final ProcessException e = ProcessException.create(null);
        assertThat(e)
                .isNotNull()
                .hasMessage("");
    }

    @Test
    void create2() {
        final RuntimeException re = new RuntimeException("bar");
        final ProcessException e = ProcessException.create("foo", re);
        assertThat(e)
                .isNotNull();
    }

    @Test
    void create2_null() {
        final ProcessException e = ProcessException.create("foo", null);
        assertThat(e)
                .isNotNull();
    }

    @Test
    void wrap1() {
        final RuntimeException re = new RuntimeException("bar");
        final ProcessException e = ProcessException.wrap(re);
        assertThat(e)
                .isNotNull();
    }

    @Test
    void wrap1_null() {
        final ProcessException e = ProcessException.wrap(null);
        assertThat(e)
                .isNotNull();
    }

    @Test
    void wrap2() {
        final RuntimeException re = new RuntimeException("bar");
        final ProcessException e = ProcessException.wrap("foo", re);
        assertThat(e)
                .isNotNull();
    }

    @Test
    void wrap2_null() {
        final ProcessException e = ProcessException.wrap("foo", null);
        assertThat(e)
                .isNotNull();
    }

    @TestFactory
    Stream<DynamicTest> test() {
        final UncheckedInterruptedException uncheckedInterruptedEx = new UncheckedInterruptedException(
                new InterruptedException());

        return TestUtil.buildDynamicTestStream()
                .withInputType(Throwable.class)
                .withOutputType(boolean.class)
                .withSingleArgTestFunction(ProcessException::isTerminated)
                .withSimpleEqualityAssertion()
                .addCase(null, false)
                .addCase(new RuntimeException("foo"), false)
                .addCase(new TaskTerminatedException(), true)
                .addCase(new InterruptedException(), true)
                .addCase(new ClosedByInterruptException(), true)
                .addCase(uncheckedInterruptedEx, true)
                .addCase(nestThrowable(new RuntimeException("foo")), false)
                .addCase(nestThrowable(new TaskTerminatedException()), true)
                .addCase(nestThrowable(new InterruptedException()), true)
                .addCase(nestThrowable(new ClosedByInterruptException()), true)
                .addCase(nestThrowable(uncheckedInterruptedEx), true)
                .addCase(new TransformerException(new RuntimeException()), false)
                .addCase(new TransformerException(new TaskTerminatedException()), true)
                .addCase(new TransformerException(new InterruptedException()), true)
                .addCase(new TransformerException(new ClosedByInterruptException()), true)
                .addCase(new TransformerException(uncheckedInterruptedEx), true)
                .addCase(new TransformerException(nestThrowable(new RuntimeException())), false)
                .addCase(new TransformerException(nestThrowable(new TaskTerminatedException())), true)
                .addCase(new TransformerException(nestThrowable(new InterruptedException())), true)
                .addCase(new TransformerException(nestThrowable(new ClosedByInterruptException())), true)
                .addCase(new TransformerException(nestThrowable(uncheckedInterruptedEx)), true)
                .addCase(ProcessException.wrap(new RuntimeException()), false)
                .addCase(ProcessException.wrap(new TaskTerminatedException()), true)
                .addCase(ProcessException.wrap(new InterruptedException()), true)
                .addCase(ProcessException.wrap(new ClosedByInterruptException()), true)
                .addCase(ProcessException.wrap(uncheckedInterruptedEx), true)
                .addCase(ProcessException.wrap(nestThrowable(new RuntimeException())), false)
                .addCase(ProcessException.wrap(nestThrowable(new TaskTerminatedException())), true)
                .addCase(ProcessException.wrap(nestThrowable(new InterruptedException())), true)
                .addCase(ProcessException.wrap(nestThrowable(new ClosedByInterruptException())), true)
                .addCase(ProcessException.wrap(nestThrowable(uncheckedInterruptedEx)), true)
                .build();
    }

    private RuntimeException nestThrowable(final Throwable e) {
        return new RuntimeException(
                new RuntimeException(
                        new RuntimeException(e)));
    }
}
