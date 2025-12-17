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

import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.FatalErrorReceiver;
import stroom.pipeline.util.ProcessorUtil;
import stroom.test.common.ComparisonHelper;
import stroom.test.common.StroomPipelineTestFileUtil;
import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestSplitFilter extends StroomUnitTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestSplitFilter.class);

    private static final String INPUT = "TestSplitFilter/input.xml";
    private static final String INPUT_ROOT_ONLY = "TestSplitFilter/input_root_only.xml";
    private static final String INPUT_ROOT_AND_CONTENT = "TestSplitFilter/input_root_and_content.xml";
    private static final String XML_OUTPUT_0 = "TestSplitFilter/output0.xml";
    private static final String SAX_OUTPUT_0 = "TestSplitFilter/output0.sax";
    private static final String XML_OUTPUT_1_x = "TestSplitFilter/output1_%s.xml";
    private static final String SAX_OUTPUT_1 = "TestSplitFilter/output1.sax";
    private static final String XML_OUTPUT_2_x = "TestSplitFilter/output2_%s.xml";
    private static final String SAX_OUTPUT_2 = "TestSplitFilter/output2.sax";
    private static final String XML_OUTPUT_3_x = "TestSplitFilter/output3_%s.xml";
    private static final String SAX_OUTPUT_3 = "TestSplitFilter/output3.sax";
    private static final String XML_OUTPUT_4 = "TestSplitFilter/output4.xml";
    private static final String SAX_OUTPUT_4 = "TestSplitFilter/output4.sax";
    private static final String XML_OUTPUT_5_x = "TestSplitFilter/output5_%s.xml";
    private static final String SAX_OUTPUT_5 = "TestSplitFilter/output5.sax";
    private static final String XML_OUTPUT_6 = "TestSplitFilter/output6.xml";
    private static final String SAX_OUTPUT_6 = "TestSplitFilter/output6.sax";
    private static final String XML_OUTPUT_7 = "TestSplitFilter/output7.xml";
    private static final String SAX_OUTPUT_7 = "TestSplitFilter/output7.sax";

    private static final int BUF_SIZE = 4096;
    private static final int THREE = 3;

    @Test
    void testXMLSplitterDepth0Count1() {
        final String expectedXml = getString(XML_OUTPUT_0);
        final String expectedSax = getString(SAX_OUTPUT_0);
        testXMLSplitter(0, 1, INPUT, expectedXml, expectedSax);
    }

    @Test
    void testXMLSplitterDepth1Count1() {
        final List<String> expectedXml = getStrings(XML_OUTPUT_1_x, 2);
        final String expectedSax = getString(SAX_OUTPUT_1);
        testXMLSplitter(1, 1, INPUT, expectedXml, expectedSax);
    }

    @Test
    void testXMLSplitterDepth2Count1() {
        final List<String> expectedXml = getStrings(XML_OUTPUT_2_x, 4);
        final String expectedSax = getString(SAX_OUTPUT_2);
        testXMLSplitter(2, 1, INPUT, expectedXml, expectedSax);
    }

    @Test
    void testXMLSplitterDepth3Count1() {
        final List<String> expectedXml = getStrings(XML_OUTPUT_3_x, 2);
        final String expectedSax = getString(SAX_OUTPUT_3);
        testXMLSplitter(THREE, 1, INPUT, expectedXml, expectedSax);
    }

    @Test
    void testXMLSplitterDepth3Count2() {
        final String expectedXml = getString(XML_OUTPUT_4);
        final String expectedSax = getString(SAX_OUTPUT_4);
        testXMLSplitter(THREE, 2, INPUT, expectedXml, expectedSax);
    }

    @Test
    void testXMLSplitterDepth3Count0() {
        final String expectedXml = getString(XML_OUTPUT_4);
        final String expectedSax = getString(SAX_OUTPUT_4);
        testXMLSplitter(THREE, 0, INPUT, expectedXml, expectedSax);
    }

    @Test
    void testXMLSplitterDepth2Count2() {
        final List<String> expectedXml = getStrings(XML_OUTPUT_5_x, 2);
        final String expectedSax = getString(SAX_OUTPUT_5);
        testXMLSplitter(2, 2, INPUT, expectedXml, expectedSax);
    }

    @Test
    void testXMLSplitterRootAndContent() {
        final String expectedXml = getString(XML_OUTPUT_6);
        final String expectedSax = getString(SAX_OUTPUT_6);
        testXMLSplitter(0, 0, INPUT_ROOT_AND_CONTENT, expectedXml, expectedSax);
    }

    @Test
    void testXMLSplitterRootOnly() {
        final String expectedXml = getString(XML_OUTPUT_7);
        final String expectedSax = getString(SAX_OUTPUT_7);
        testXMLSplitter(0, 0, INPUT_ROOT_ONLY, expectedXml, expectedSax);
    }

    private void testXMLSplitter(final int splitDepth,
                                 final int splitCount,
                                 final String inputPath,
                                 final String expectedXml,
                                 final String expectedSax) {
        testXMLSplitter(splitDepth,
                splitCount,
                inputPath,
                Collections.singletonList(expectedXml),
                expectedSax);
    }

    private void testXMLSplitter(final int splitDepth,
                                 final int splitCount,
                                 final String inputPath,
                                 final List<String> expectedXmlList,
                                 final String expectedSax) {
        final ByteArrayInputStream input = new ByteArrayInputStream(getString(inputPath).getBytes());

        final SplitFilter splitter = new SplitFilter();
        splitter.setSplitDepth(splitDepth);
        splitter.setSplitCount(splitCount);

        final TestFilter testFilter = new TestFilter(null, null);

        final TestSAXEventFilter testSAXEventFilter = new TestSAXEventFilter();

        splitter.setTarget(testFilter);
        testFilter.setTarget(testSAXEventFilter);

        ProcessorUtil.processXml(input, new ErrorReceiverProxy(new FatalErrorReceiver()), splitter,
                new LocationFactoryProxy());

        final List<String> actualXmlList = testFilter.getOutputs()
                .stream()
                .map(String::trim)
                .map(s -> s.replaceAll("\r", ""))
                .collect(Collectors.toList());
        final String actualSax = testSAXEventFilter.getOutput().trim();

        // Test to see if the output SAX is the same as the expected SAX.
        ComparisonHelper.compareStrings(expectedSax, actualSax, "Expected and actual SAX do not match at index: ");

        // Test to see if the output XML is the same as the expected XML.
        LOGGER.info(String.format("Expected List %d", expectedXmlList.size()));
        expectedXmlList.forEach(LOGGER::info);
        LOGGER.info(String.format("Actual List %d", actualXmlList.size()));
        actualXmlList.forEach(LOGGER::info);

        assertThat(actualXmlList).hasSize(expectedXmlList.size()); // first just check the size
        final Iterator<String> actualXmlIter = actualXmlList.iterator();
        for (final String expectedXml : expectedXmlList) {
            final String actualXml = actualXmlIter.next();
            ComparisonHelper.compareStrings(expectedXml, actualXml, "Expected and actual XML do not match at index: ");
        }
    }

    private List<String> getStrings(final String resourceName, final int count) {
        return IntStream.range(0, count)
                .mapToObj(x -> String.format(resourceName, x))
                .map(this::getString)
                .collect(Collectors.toList());
    }

    private String getString(final String resourceName) {
        try {
            final InputStream is = StroomPipelineTestFileUtil.getInputStream(resourceName);

            final byte[] buffer = new byte[BUF_SIZE];
            int len;
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }

            String str = baos.toString();
            str = str.replaceAll("\r", "");
            return str.trim();
        } catch (final IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}
