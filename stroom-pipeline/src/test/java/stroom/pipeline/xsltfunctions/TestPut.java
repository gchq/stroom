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

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestPut extends AbstractXsltFunctionTest<Put> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestPut.class);

    private TaskScopeMap taskScopeMap;
    private Put put;

    @BeforeEach
    void setUp() {
        taskScopeMap = new TaskScopeMap();
        put = new Put(taskScopeMap);
    }

    @Test
    void call() {
        final String key1 = "key1";
        final String val1 = "val1";
        final String key2 = "key2";
        final String val2 = "val2";
        final Sequence sequence1 = callFunctionWithSimpleArgs(key1, val1);

        assertThat(sequence1)
                .isInstanceOf(EmptyAtomicSequence.class);

        final Sequence sequence2 = callFunctionWithSimpleArgs(key2, val2);

        assertThat(sequence2)
                .isInstanceOf(EmptyAtomicSequence.class);

        assertThat(taskScopeMap.get(key1))
                .isEqualTo(val1);
        assertThat(taskScopeMap.get(key2))
                .isEqualTo(val2);

        verifyNoLogCalls();
    }

    @Override
    Put getXsltFunction() {
        return put;
    }

    @Override
    String getFunctionName() {
        return Put.FUNCTION_NAME;
    }
}
