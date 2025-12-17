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

package stroom.pipeline.filter;

import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.FatalErrorReceiver;
import stroom.pipeline.util.ProcessorUtil;
import stroom.test.common.StroomPipelineTestFileUtil;
import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import static org.assertj.core.api.Assertions.assertThat;

class TestMergeFilter extends StroomUnitTest {

    private static final String INPUT_XML = "TestMergeFilter/input.xml";
    private static final String EXPECTED_OUTPUT_DEPTH3 = "TestMergeFilter/output_depth_3.xml";

    private MergeFilter mergeFilter;
    private ErrorReceiverProxy errorReceiverProxy;

    @BeforeEach
    void setUp() {
        mergeFilter = new MergeFilter();
        errorReceiverProxy = new ErrorReceiverProxy(new FatalErrorReceiver());
    }

    @Test
    void testMergeDepth3() throws Exception {
        final String expectedXml = getString(EXPECTED_OUTPUT_DEPTH3);
        final ByteArrayInputStream streamInput = new ByteArrayInputStream(getString(INPUT_XML).getBytes());
        mergeFilter.setMergeDepth(3);

        final TestFilter testFilter = new TestFilter(null, null);

        final TestSAXEventFilter testSAXEventFilter = new TestSAXEventFilter();

        mergeFilter.setTarget(testFilter);
        testFilter.setTarget(testSAXEventFilter);
        // Act
        ProcessorUtil.processXml(streamInput, errorReceiverProxy, mergeFilter, null);
        final String actualXml = prettyPrintXML(testFilter.getOutput()); // Get actual XML from mergeFilter

        // Assert
        assertThat(prettyPrintXML(actualXml)).isEqualTo(prettyPrintXML(expectedXml));
    }

    private String getString(final String resourceName) {
        return StroomPipelineTestFileUtil.getString(resourceName);
    }

    public static String prettyPrintXML(final String xmlString) throws Exception {
        final Document document = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new InputSource(new java.io.StringReader(xmlString)));

        final TransformerFactory transformerFactory = TransformerFactory.newInstance();

        final Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        final StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));

        return writer.toString();
    }
}
