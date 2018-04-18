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

package stroom.xml.converter.xmlfragment;

import org.junit.Assert;
import org.junit.Test;
import stroom.guice.PipelineScopeRunnable;
import stroom.pipeline.shared.TextConverter.TextConverterType;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.xml.F2XTestUtil;
import stroom.xml.XMLValidator;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.ByteArrayInputStream;

public class TestXMLFragmentWrapper extends AbstractProcessIntegrationTest {
    @Inject
    private Provider<F2XTestUtil> f2XTestUtilProvider;
    @Inject
    private Provider<XMLValidator> xmlValidatorProvider;
    @Inject
    private PipelineScopeRunnable pipelineScopeRunnable;

    /**
     * Tests a basic XML fragment.
     */
    @Test
    public void testBasicFragment() {
        pipelineScopeRunnable.scopeRunnable(() -> {
            final TextConverterType textConverterType = TextConverterType.XML_FRAGMENT;
            final String textConverterLocation = "TestXMLFragmentWrapper/XMLFragmentWrapper.xml";

            // Start by validating the resource.
            if (textConverterType == TextConverterType.DATA_SPLITTER) {
                final XMLValidator xmlValidator = xmlValidatorProvider.get();
                final String message = xmlValidator.getInvalidXmlResourceMessage(textConverterLocation, true);
                Assert.assertTrue(message, message.length() == 0);
            }

            final F2XTestUtil f2xTestUtil = f2XTestUtilProvider.get();
            final String xml = f2xTestUtil.runF2XTest(textConverterType, textConverterLocation,
                    new ByteArrayInputStream(
                            "<record><data name=\"Test Name\" value=\"Test value\"/></record>".getBytes()));

            final String example = "<?xml version=\"1.1\" encoding=\"UTF-8\"?>" + "<records " + "xmlns=\"records:2\" "
                    + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                    + "xsi:schemaLocation=\"records:2 file://records-v2.0.xsd\" " + "version=\"2.0\">"
                    + "<record><data name=\"Test Name\" value=\"Test value\"/></record>" + "</records>";

            Assert.assertEquals(example, xml);
        });
    }
}
