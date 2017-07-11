/*
 * Copyright 2017 Crown Copyright
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

package stroom.pipeline.server.filter;

import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.pipeline.server.LocationFactoryProxy;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.FatalErrorReceiver;
import stroom.pipeline.server.util.ProcessorUtil;
import stroom.test.ComparisonHelper;
import stroom.test.StroomProcessTestFileUtil;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Unit test class for <code>XMLTransformer</code>.
 */
@RunWith(StroomJUnit4ClassRunner.class)
public class TestSplitFilter extends StroomUnitTest {
    private static final String INPUT = "TestSplitFilter/input.xml";
    private static final String INPUT_ROOT_ONLY = "TestSplitFilter/input_root_only.xml";
    private static final String INPUT_ROOT_AND_CONTENT = "TestSplitFilter/input_root_and_content.xml";
    private static final String XML_OUTPUT_0 = "TestSplitFilter/output0.xml";
    private static final String SAX_OUTPUT_0 = "TestSplitFilter/output0.sax";
    private static final String XML_OUTPUT_1 = "TestSplitFilter/output1.nxml";
    private static final String SAX_OUTPUT_1 = "TestSplitFilter/output1.sax";
    private static final String XML_OUTPUT_2 = "TestSplitFilter/output2.nxml";
    private static final String SAX_OUTPUT_2 = "TestSplitFilter/output2.sax";
    private static final String XML_OUTPUT_3 = "TestSplitFilter/output3.nxml";
    private static final String SAX_OUTPUT_3 = "TestSplitFilter/output3.sax";
    private static final String XML_OUTPUT_4 = "TestSplitFilter/output4.nxml";
    private static final String SAX_OUTPUT_4 = "TestSplitFilter/output4.sax";
    private static final String XML_OUTPUT_5 = "TestSplitFilter/output5.nxml";
    private static final String SAX_OUTPUT_5 = "TestSplitFilter/output5.sax";
    private static final String XML_OUTPUT_6 = "TestSplitFilter/output6.nxml";
    private static final String SAX_OUTPUT_6 = "TestSplitFilter/output6.sax";
    private static final String XML_OUTPUT_7 = "TestSplitFilter/output7.nxml";
    private static final String SAX_OUTPUT_7 = "TestSplitFilter/output7.sax";

    private static final int BUF_SIZE = 4096;
    private static final int THREE = 3;

    @Test
    public void testXMLSplitterDepth0Count1() throws Exception {
        final String expectedXml = getString(XML_OUTPUT_0);
        final String expectedSax = getString(SAX_OUTPUT_0);
        testXMLSplitter(0, 1, INPUT, expectedXml.toString(), expectedSax.toString());
    }

    @Test
    public void testXMLSplitterDepth1Count1() throws Exception {
        final String expectedXml = getString(XML_OUTPUT_1);
        final String expectedSax = getString(SAX_OUTPUT_1);
        testXMLSplitter(1, 1, INPUT, expectedXml.toString(), expectedSax.toString());
    }

    @Test
    public void testXMLSplitterDepth2Count1() throws Exception {
        final String expectedXml = getString(XML_OUTPUT_2);
        final String expectedSax = getString(SAX_OUTPUT_2);
        testXMLSplitter(2, 1, INPUT, expectedXml.toString(), expectedSax.toString());
    }

    @Test
    public void testXMLSplitterDepth3Count1() throws Exception {
        final String expectedXml = getString(XML_OUTPUT_3);
        final String expectedSax = getString(SAX_OUTPUT_3);
        testXMLSplitter(THREE, 1, INPUT, expectedXml.toString(), expectedSax.toString());
    }

    @Test
    public void testXMLSplitterDepth3Count2() throws Exception {
        final String expectedXml = getString(XML_OUTPUT_4);
        final String expectedSax = getString(SAX_OUTPUT_4);
        testXMLSplitter(THREE, 2, INPUT, expectedXml.toString(), expectedSax.toString());
    }

    @Test
    public void testXMLSplitterDepth3Count0() throws Exception {
        final String expectedXml = getString(XML_OUTPUT_4);
        final String expectedSax = getString(SAX_OUTPUT_4);
        testXMLSplitter(THREE, 0, INPUT, expectedXml.toString(), expectedSax.toString());
    }

    @Test
    public void testXMLSplitterDepth2Count2() throws Exception {
        final String expectedXml = getString(XML_OUTPUT_5);
        final String expectedSax = getString(SAX_OUTPUT_5);
        testXMLSplitter(2, 2, INPUT, expectedXml.toString(), expectedSax.toString());
    }

    @Test
    public void testXMLSplitterRootAndContent() throws Exception {
        final String expectedXml = getString(XML_OUTPUT_6);
        final String expectedSax = getString(SAX_OUTPUT_6);
        testXMLSplitter(0, 0, INPUT_ROOT_AND_CONTENT, expectedXml.toString(), expectedSax.toString());
    }

    @Test
    public void testXMLSplitterRootOnly() throws Exception {
        final String expectedXml = getString(XML_OUTPUT_7);
        final String expectedSax = getString(SAX_OUTPUT_7);
        testXMLSplitter(0, 0, INPUT_ROOT_ONLY, expectedXml.toString(), expectedSax.toString());
    }

    private void testXMLSplitter(final int splitDepth, final int splitCount, final String inputPath,
            final String expectedXml, final String expectedSax) throws Exception {
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

        final String actualXml = testFilter.getOutput().trim().replaceAll("\r", "");
        final String actualSax = testSAXEventFilter.getOutput().trim();

        // Test to see if the output SAX is the same as the expected SAX.
        ComparisonHelper.compareStrings(expectedSax, actualSax, "Expected and actual SAX do not match at index: ");
        // Test to see if the output XML is the same as the expected XML.
        ComparisonHelper.compareStrings(expectedXml, actualXml, "Expected and actual XML do not match at index: ");
    }

    private String getString(final String resourceName) {
        try {
            final InputStream is = StroomProcessTestFileUtil.getInputStream(resourceName);

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
