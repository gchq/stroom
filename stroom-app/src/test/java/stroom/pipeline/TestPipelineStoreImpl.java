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

package stroom.pipeline;

import stroom.docref.DocRef;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.common.TestUtil;

import com.google.inject.TypeLiteral;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestPipelineStoreImpl extends AbstractCoreIntegrationTest {

    protected static final String PIPE_1_NAME = "Pipe 1 a";
    protected static final String PIPE_2_NAME = "Pipe 2 aa";
    protected static final String PIPE_3_NAME = "Pipe 3 aaa";

    DocRef pipe1;
    DocRef pipe2;
    DocRef pipe3;

    @Inject
    private PipelineStore pipelineStore;

    @BeforeEach
    void setUp() {
        pipe1 = pipelineStore.createDocument(PIPE_1_NAME);
        pipe2 = pipelineStore.createDocument(PIPE_2_NAME);
        pipe3 = pipelineStore.createDocument(PIPE_3_NAME);
    }

    @TestFactory
    Stream<DynamicTest> findByName() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withWrappedOutputType(new TypeLiteral<List<DocRef>>() {
                })
                .withTestFunction(testCase -> {
                    final String nameFilter = testCase.getInput();
                    // Need to sort to ensure predictable order for tests
                    return pipelineStore.findByName(nameFilter, true)
                            .stream()
                            .sorted(Comparator.naturalOrder())
                            .collect(Collectors.toList());
                })
                .withSimpleEqualityAssertion()
                .addCase(null, Collections.emptyList())
                .addCase("Pipe", Collections.emptyList())
                .addCase(PIPE_1_NAME.toLowerCase(), Collections.emptyList())
                .addCase(PIPE_1_NAME, List.of(pipe1))
                .addCase("Pipe*", List.of(pipe1, pipe2, pipe3))
                .addCase("*a", List.of(pipe1, pipe2, pipe3))
                .addCase("*2*", List.of(pipe2))
                .addCase("* *aa*", List.of(pipe2, pipe3))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> findByName_no_wild_cards() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withWrappedOutputType(new TypeLiteral<List<DocRef>>() {
                })
                .withTestFunction(testCase -> {
                    final String nameFilter = testCase.getInput();
                    // Need to sort to ensure predictable order for tests
                    return pipelineStore.findByName(nameFilter, false)
                            .stream()
                            .sorted(Comparator.naturalOrder())
                            .collect(Collectors.toList());
                })
                .withSimpleEqualityAssertion()
                .addCase(null, Collections.emptyList())
                .addCase("Pipe", Collections.emptyList())
                .addCase(PIPE_1_NAME.toLowerCase(), Collections.emptyList())
                .addCase(PIPE_1_NAME, List.of(pipe1))
                .addCase("Pipe*", Collections.emptyList())
                .addCase("*a", Collections.emptyList())
                .addCase("*2*", Collections.emptyList())
                .addCase("* *aa*", Collections.emptyList())
                .build();
    }
}
