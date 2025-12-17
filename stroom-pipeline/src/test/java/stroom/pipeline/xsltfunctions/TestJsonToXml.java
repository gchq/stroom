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

import net.sf.saxon.Configuration;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class TestJsonToXml extends AbstractXsltFunctionTest<JsonToXml> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestJsonToXml.class);

    private JsonToXml jsonToXml;

    @BeforeEach
    void setUp() {
        jsonToXml = new JsonToXml();
    }

    @Test
    void call() {
        Mockito.when(getMockXPathContext().getConfiguration())
                .thenReturn(Configuration.newConfiguration());

        final String json = """
                {
                    "firstName": "Joe",
                    "secondName": "Bloggs"
                }
                """;
        final Sequence sequence = callFunctionWithSimpleArgs(json);

        assertThat(sequence)
                .isInstanceOf(NodeInfo.class);

        final String xml = getAsSerialisedXmlString(sequence)
                .orElseThrow();

        assertThat(xml)
                .isEqualTo("""
                        <map xmlns="http://www.w3.org/2013/XSL/json">
                           <string key="firstName">Joe</string>
                           <string key="secondName">Bloggs</string>
                        </map>
                        """);
    }

    @Test
    void call_emptyString() {

        final String json = "";
        final Sequence sequence = callFunctionWithSimpleArgs(json);

        assertThat(sequence)
                .isInstanceOf(EmptyAtomicSequence.class);
    }

    @Test
    void call_invalid() {
        Mockito.when(getMockXPathContext().getConfiguration())
                .thenReturn(Configuration.newConfiguration());

        final String json = """
                {
                    "firstName": "Joe"
                """;
        logLogCallsToDebug();
        final Sequence sequence = callFunctionWithSimpleArgs(json);

        assertThat(sequence)
                .isInstanceOf(EmptyAtomicSequence.class);

        final LogArgs logArgs = verifySingleLogCall();

        assertThat(logArgs.getSeverity())
                .isEqualTo(Severity.ERROR);
        assertThat(logArgs.getMessage())
                .containsIgnoringCase("error parsing json");
    }

    @Override
    JsonToXml getXsltFunction() {
        return jsonToXml;
    }

    @Override
    String getFunctionName() {
        return JsonToXml.FUNCTION_NAME;
    }
}
