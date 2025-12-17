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
import stroom.util.shared.Severity;

import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestLog extends AbstractXsltFunctionTest<Log> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestLog.class);

    private Log log;

    @BeforeEach
    void setUp() {
        log = new Log();
    }

    @Test
    void call() {

        logLogCallsToDebug();
        final Sequence sequence = callFunctionWithSimpleArgs(Severity.INFO.toString().toLowerCase(), "My msg");

        assertThat(sequence)
                .isInstanceOf(EmptyAtomicSequence.class);

        final List<LogArgs> logArgsList = verifyLogCalls(1);

        assertThat(logArgsList)
                .first()
                .extracting(LogArgs::getSeverity)
                .isEqualTo(Severity.INFO);

        assertThat(logArgsList)
                .first()
                .extracting(LogArgs::getMessage)
                .isEqualTo("My msg");
    }

    @Test
    void call_badSeverity() {

        logLogCallsToDebug();

        final Sequence sequence = callFunctionWithSimpleArgs("foo", "My msg");

        assertThat(sequence)
                .isInstanceOf(EmptyAtomicSequence.class);

        final LogArgs logArgs = verifySingleLogCall();

        assertThat(logArgs.getSeverity())
                .isEqualTo(Severity.ERROR);

        assertThat(logArgs.getMessage())
                .containsIgnoringCase("unknown severity")
                .containsIgnoringCase("foo");
    }

    @Test
    void call_nullSeverity() {

        logLogCallsToDebug();

        final Sequence sequence = callFunctionWithSimpleArgs(null, "My msg");

        assertThat(sequence)
                .isInstanceOf(EmptyAtomicSequence.class);

        final List<LogArgs> logArgsList = verifyLogCalls(2);

        assertThat(logArgsList.get(0).getSeverity())
                .isEqualTo(Severity.WARNING);
        assertThat(logArgsList.get(0).getMessage())
                .containsIgnoringCase("non string argument");

        assertThat(logArgsList.get(1).getSeverity())
                .isEqualTo(Severity.ERROR);
        assertThat(logArgsList.get(1).getMessage())
                .containsIgnoringCase("unknown severity");
    }

    @Override
    Log getXsltFunction() {
        return log;
    }

    @Override
    String getFunctionName() {
        return Log.FUNCTION_NAME;
    }
}
