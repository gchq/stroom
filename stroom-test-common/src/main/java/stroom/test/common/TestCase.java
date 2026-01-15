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

package stroom.test.common;

import stroom.util.logging.LogUtil;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;

/**
 * Useful class for holding input and expected output values for a test case.
 *
 * @param <I> The type of the test case input. If there are multiple inputs then
 *            {@code I} may be a {@link Tuple} of input values.
 * @param <O> The type of the test case expected output. If there are multiple outputs then
 *            {@code O} may be a {@link Tuple} of input values.
 */
public class TestCase<I, O> {

    private final I input;
    private final O expectedOutput;
    private final Class<? extends Throwable> expectedThrowableType;
    private final String name;

    TestCase(final I input,
             final O expectedOutput,
             final Class<? extends Throwable> expectedThrowableType,
             final String name) {

        this.input = input;
        this.expectedOutput = expectedOutput;
        this.expectedThrowableType = expectedThrowableType;
        this.name = name;
    }

    public static <I, O> TestCase<I, O> of(final I input,
                                           final O expectedOutput) {
        return new TestCase<>(input, expectedOutput, null, null);
    }

    public static <I, O> TestCase<I, O> of(final String name,
                                           final I input,
                                           final O expectedOutput) {
        return new TestCase<>(input, expectedOutput, null, name);
    }

    public static <I> TestCase<I, ?> throwing(final I input,
                                              final Class<? extends Throwable> expectedThrowable) {
        return new TestCase<>(input, null, expectedThrowable, null);
    }

    public static <I> TestCase<I, ?> throwing(final String name,
                                              final I input,
                                              final Class<? extends Throwable> expectedThrowable) {
        return new TestCase<>(input, null, expectedThrowable, name);
    }

    public I getInput() {
        return input;
    }

    public O getExpectedOutput() {
        return expectedOutput;
    }

    public Class<? extends Throwable> getExpectedThrowableType() {
        return expectedThrowableType;
    }

    public boolean isExpectedToThrow() {
        return expectedThrowableType != null;
    }

    public String getName() {
        return name;
    }

    public static <T> String valueToString(final String name, final T value) {
        if (value instanceof Tuple2) {
            final Tuple2<?, ?> tuple2 = (Tuple2<?, ?>) value;
            return LogUtil.message("{}1: '{}', {}2: '{}'",
                    name, tuple2._1, name.toLowerCase(), tuple2._2);
        } else if (value instanceof Tuple3) {
            final Tuple3<?, ?, ?> tuple3 = (Tuple3<?, ?, ?>) value;
            return LogUtil.message("{}1: '{}', {}2: '{}', {}3: '{}'",
                    name, tuple3._1, name.toLowerCase(), tuple3._2, name.toLowerCase(), tuple3._3);
        } else {
            return LogUtil.message("{}: '{}'", name, value);
        }
    }

    @Override
    public String toString() {
        return "TestCase{" +
                "input=" + input +
                ", expectedOutput=" + expectedOutput +
                ", name='" + name + '\'' +
                '}';
    }
}
