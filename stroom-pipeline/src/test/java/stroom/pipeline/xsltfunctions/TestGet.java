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
import net.sf.saxon.value.StringValue;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestGet extends AbstractXsltFunctionTest<Get> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestGet.class);

    private TaskScopeMap taskScopeMap;
    private Get get;

    @BeforeEach
    void setUp() {
        taskScopeMap = new TaskScopeMap();
        get = new Get(taskScopeMap);
    }

    @Test
    void call_hit() {
        final String key1 = "key1";
        final String val1 = "val1";
        final String key2 = "key2";
        final String val2 = "val2";

        taskScopeMap.put(key1, val1);
        taskScopeMap.put(key2, val2);

        final Sequence sequence1 = callFunctionWithSimpleArgs(key1);

        Assertions.assertThat(sequence1)
                .isInstanceOf(StringValue.class);
        Assertions.assertThat(getAsStringValue(sequence1))
                .hasValue(val1);

        final Sequence sequence2 = callFunctionWithSimpleArgs(key2);

        Assertions.assertThat(sequence2)
                .isInstanceOf(StringValue.class);
        Assertions.assertThat(getAsStringValue(sequence2))
                .isPresent()
                .hasValue(val2);

        verifyNoLogCalls();
    }

    @Test
    void call_miss() {
        final String key1 = "key1";

        Assertions.assertThat(taskScopeMap.get(key1))
                        .isNull();

        final Sequence sequence1 = callFunctionWithSimpleArgs(key1);

        Assertions.assertThat(sequence1)
                .isInstanceOf(EmptyAtomicSequence.class);

        verifyNoLogCalls();
    }

    @Override
    Get getXsltFunction() {
        return get;
    }

    @Override
    String getFunctionName() {
        return Get.FUNCTION_NAME;
    }
}
