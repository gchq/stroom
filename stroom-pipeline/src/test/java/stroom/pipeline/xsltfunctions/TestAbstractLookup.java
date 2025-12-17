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

package stroom.pipeline.xsltfunctions;

import stroom.task.api.TaskContext;
import stroom.test.common.TestUtil;
import stroom.util.shared.Severity;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.mockito.Mockito;

import java.util.stream.Stream;

class TestAbstractLookup {

    @TestFactory
    Stream<DynamicTest> testShouldLog() {

        record Inputs(Severity severity, boolean isTrace, boolean isIgnoreWarnings, boolean isTerminated) {
        }

        return TestUtil.buildDynamicTestStream()
                .withInputType(Inputs.class)
                .withOutputType(Boolean.class)
                .withTestFunction(testCase -> {
                    final TaskContext taskContext = Mockito.mock(TaskContext.class);
                    Mockito.when(taskContext.isTerminated())
                            .thenReturn(testCase.getInput().isTerminated);

                    return AbstractLookup.shouldLog(
                            taskContext,
                            testCase.getInput().severity,
                            testCase.getInput().isTrace,
                            testCase.getInput().isIgnoreWarnings);
                })
                .withSimpleEqualityAssertion()
                .addCase(new Inputs(Severity.INFO, false, true, false),
                        false)
                .addCase(new Inputs(Severity.INFO, true, true, false),
                        true)
                .addCase(new Inputs(Severity.INFO, false, false, false),
                        false)
                .addCase(new Inputs(Severity.WARNING, false, true, false),
                        false)
                .addCase(new Inputs(Severity.WARNING, false, false, false),
                        true)
                .addCase(new Inputs(Severity.WARNING, true, true, false),
                        true)
                .addCase(new Inputs(Severity.ERROR, false, true, false),
                        true)
                .addCase(new Inputs(Severity.ERROR, true, true, false),
                        true)
                .addCase(new Inputs(Severity.ERROR, true, false, false),
                        true)
                .addCase(new Inputs(Severity.ERROR, false, false, false),
                        true)

                .addCase(new Inputs(Severity.INFO, false, true, true),
                        false)
                .addCase(new Inputs(Severity.INFO, true, true, true),
                        false)
                .addCase(new Inputs(Severity.INFO, false, false, true),
                        false)
                .addCase(new Inputs(Severity.WARNING, false, true, true),
                        false)
                .addCase(new Inputs(Severity.WARNING, false, false, true),
                        false)
                .addCase(new Inputs(Severity.WARNING, true, true, true),
                        false)
                .addCase(new Inputs(Severity.ERROR, false, true, true),
                        false)
                .addCase(new Inputs(Severity.ERROR, true, true, true),
                        false)
                .addCase(new Inputs(Severity.ERROR, true, false, true),
                        false)
                .addCase(new Inputs(Severity.ERROR, false, false, true),
                        false)
                .build();
    }
}
