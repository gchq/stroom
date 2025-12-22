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

import java.util.Optional;

public class TestOutcome<I, O> {

    private final TestCase<I, O> testCase;
    private final O actualOutput;
    private final Throwable actualThrowable;

    TestOutcome(final TestCase<I, O> testCase,
                final O actualOutput,
                final Throwable actualThrowable) {
        this.testCase = testCase;
        this.actualOutput = actualOutput;
        this.actualThrowable = actualThrowable;
    }

    public I getInput() {
        return testCase.getInput();
    }

    public O getExpectedOutput() {
        return testCase.getExpectedOutput();
    }

    public O getActualOutput() {
        return actualOutput;
    }

    public Class<? extends Throwable> getExpectedThrowableType() {
        return testCase.getExpectedThrowableType();
    }

    public String getName() {
        return testCase.getName();
    }

    public Optional<Throwable> getActualThrowable() {
        return Optional.ofNullable(actualThrowable);
    }

    public boolean isExpectedToThrow() {
        return testCase.getExpectedThrowableType() != null;
    }

    public String buildFailMessage() {
        return buildFailMessage(this);
    }

    public static <I, O> String buildFailMessage(final TestOutcome<I, O> testOutcome) {
        return LogUtil.message("Expected {} but got actual {} for {}",
                TestCase.valueToString("output", testOutcome.testCase.getExpectedOutput()),
                TestCase.valueToString("output", testOutcome.actualOutput),
                TestCase.valueToString("input", testOutcome.testCase.getInput()));
    }


    @Override
    public String toString() {
        return "TestOutcome{" +
                "testCase=" + testCase +
                ", actualOutput=" + actualOutput +
                ", throwable=" + actualThrowable +
                '}';
    }
}
