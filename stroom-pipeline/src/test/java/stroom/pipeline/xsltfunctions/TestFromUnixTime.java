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

import net.sf.saxon.om.Sequence;
import net.sf.saxon.value.Int64Value;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestFromUnixTime extends AbstractXsltFunctionTest<FromUnixTime> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestFromUnixTime.class);

    private final FromUnixTime fromUnixTime = new FromUnixTime();

    @Test
    void call() throws Exception {

        final Int64Value int64 = Int64Value.makeIntegerValue(1749727780400L);

        final Sequence sequence1 = callFunctionWithSimpleArgs(int64);

        final Optional<String> optDateTime = getAsDateTimeValue(sequence1);

        assertThat(optDateTime).isNotEmpty();

        assertThat(optDateTime.get()).isEqualTo("2025-06-12T11:29:40.4Z");
    }

    @Override
    FromUnixTime getXsltFunction() {
        return fromUnixTime;
    }

    @Override
    String getFunctionName() {
        return FromUnixTime.FUNCTION_NAME;
    }
}
