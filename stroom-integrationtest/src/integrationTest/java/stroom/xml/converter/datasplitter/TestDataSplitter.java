/*
 * Copyright 2016 Crown Copyright
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

package stroom.xml.converter.datasplitter;


import org.junit.jupiter.api.Test;
import stroom.feed.shared.FeedDoc;
import stroom.lifecycle.StroomBeanStore;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.test.StroomPipelineTestFileUtil;
import stroom.xml.F2XTestUtil;
import stroom.xml.XMLValidator;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

// TODO : Add test data

class TestDataSplitter extends AbstractProcessIntegrationTest {
    @Inject
    private StroomBeanStore beanStore;

    /**
     * Tests a basic CSV file.
     */
    @Test
    void testBasicCSV() {
        final String xml = runF2XTest(TextConverterType.DATA_SPLITTER, "TestDataSplitter/CSVWithHeading.ds",
                new ByteArrayInputStream("h1,h2\ntoken1a,token1b\ntoken2a,token2b\n".getBytes()));

        final String example = "<?xml version=\"1.1\" encoding=\"UTF-8\"?>" + "<records " + "xmlns=\"records:2\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xsi:schemaLocation=\"records:2 file://records-v1.1.xsd\" " + "Version=\"1.1\">" + "<record>"
                + "<data name=\"h1\" value=\"token1a\"/>" + "<data name=\"h2\" value=\"token1b\"/>"
                + "</record><record>" + "<data name=\"h1\" value=\"token2a\"/>"
                + "<data name=\"h2\" value=\"token2b\"/>" + "</record></records>";

        assertThat(xml).isEqualTo(example);
    }

    /**
     * Tests a sample network monitoring CSV file.
     */
    @Test
    void testNetworkMonitoringSample() {
        final String xml = runF2XTest(TextConverterType.DATA_SPLITTER, "TestDataSplitter/CSVWithHeading.ds",
                StroomPipelineTestFileUtil.getInputStream("TestDataSplitter/NetworkMonitoringSample.in"));

        assertThat(xml.indexOf("<data name=\"IPAddress\" value=\"192.168.0.3\"/>") != -1).isTrue();
    }

    /**
     * Tests a sample network monitoring CSV file and tries to transform it with
     * XSL.
     */
    @Test
    void testNetworkMonitoringSampleWithXSL() {
        runFullTest(new FeedDoc("NetworkMonitoring-EVENTS"), TextConverterType.DATA_SPLITTER,
                "TestDataSplitter/SimpleCSVSplitter.ds", "TestDataSplitter/NetworkMonitoring.xsl",
                "TestDataSplitter/NetworkMonitoringSample.in", 0);
    }

    /**
     * Tests a sample network monitoring CSV file and tries to transform it with
     * XSL.
     *
     * @throws Exception Might be thrown while performing the test.
     */
    @Test
    void testDS3NetworkMonitoringSampleWithXSL() {
        runFullTest(new FeedDoc("NetworkMonitoring-EVENTS"), TextConverterType.DATA_SPLITTER,
                "TestDataSplitter/CSVWithHeading.ds", "TestDataSplitter/DS3NetworkMonitoring.xsl",
                "TestDataSplitter/NetworkMonitoringSample.in", 0);
    }

    /**
     * First stage ref data change.
     */
    @Test
    void testRefDataCSV() {
        final String xml = runF2XTest(TextConverterType.DATA_SPLITTER, "TestDataSplitter/SimpleCSVSplitter.ds",
                StroomPipelineTestFileUtil.getInputStream("TestDataSplitter/SampleRefData-HostNameToIP.in"));

        assertThat(xml.indexOf("<data name=\"IP Address\" value=\"192.168.0.10\"/>") != -1).isTrue();
    }

    /**
     * Tests a sample ref data CSV file and tries to transform it with XSL.
     */
    @Test
    void testRefDataCSVWithXSL() {
        final FeedDoc refFeed = new FeedDoc("HostNameToIP-REFERENCE");
        refFeed.setReference(true);
        runFullTest(refFeed, TextConverterType.DATA_SPLITTER, "TestDataSplitter/SimpleCSVSplitter.ds",
                "TestDataSplitter/SampleRefData-HostNameToIP.xsl", "TestDataSplitter/SampleRefData-HostNameToIP.in", 0);
    }

    private String runF2XTest(final TextConverterType textConverterType, final String textConverterLocation,
                              final InputStream inputStream) {
        validate(textConverterType, textConverterLocation);

        final F2XTestUtil f2xTestUtil = beanStore.getInstance(F2XTestUtil.class);
        final String xml = f2xTestUtil.runF2XTest(textConverterType, textConverterLocation, inputStream);
        return xml;
    }

    private String runFullTest(final FeedDoc feed, final TextConverterType textConverterType,
                               final String textConverterLocation, final String xsltLocation, final String dataLocation,
                               final int expectedWarnings) {
        validate(textConverterType, textConverterLocation);

        final F2XTestUtil f2xTestUtil = beanStore.getInstance(F2XTestUtil.class);
        final String xml = f2xTestUtil.runFullTest(feed, textConverterType, textConverterLocation, xsltLocation,
                dataLocation, expectedWarnings);
        return xml;
    }

    private void validate(final TextConverterType textConverterType, final String textConverterLocation) {
        final XMLValidator xmlValidator = beanStore.getInstance(XMLValidator.class);
        // Start by validating the resource.
        if (textConverterType == TextConverterType.DATA_SPLITTER) {
            final String message = xmlValidator.getInvalidXmlResourceMessage(textConverterLocation, true);
            assertThat(message.length() == 0).as(message).isTrue();
        }
    }
}
