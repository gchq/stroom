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
import stroom.pipeline.shared.TextConverter.TextConverterType;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.util.spring.StroomBeanStore;
import stroom.util.task.TaskScopeContextHolder;
import stroom.xml.F2XTestUtil;
import stroom.xml.XMLValidator;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;

public class TestXMLFragmentWrapper extends AbstractProcessIntegrationTest {
    @Resource
    private StroomBeanStore beanStore;

    /**
     * Tests a basic XML fragment.
     *
     * @throws Exception Might be thrown while performing the test.
     */
    @Test
    public void testBasicFragment() throws Exception {
        final TextConverterType textConverterType = TextConverterType.XML_FRAGMENT;
        final String textConverterLocation = "TestXMLFragmentWrapper/XMLFragmentWrapper.xml";

        // Start by validating the resource.
        if (textConverterType == TextConverterType.DATA_SPLITTER) {
            try {
                TaskScopeContextHolder.addContext();
                final XMLValidator xmlValidator = beanStore.getBean(XMLValidator.class);
                final String message = xmlValidator.getInvalidXmlResourceMessage(textConverterLocation, true);
                Assert.assertTrue(message, message.length() == 0);
            } finally {
                TaskScopeContextHolder.removeContext();
            }
        }

        try {
            TaskScopeContextHolder.addContext();
            final F2XTestUtil f2xTestUtil = beanStore.getBean(F2XTestUtil.class);
            final String xml = f2xTestUtil.runF2XTest(textConverterType, textConverterLocation,
                    new ByteArrayInputStream(
                            "<record><data name=\"Test Name\" value=\"Test value\"/></record>".getBytes()));

            final String example = "<?xml version=\"1.1\" encoding=\"UTF-8\"?>" + "<records " + "xmlns=\"records:2\" "
                    + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                    + "xsi:schemaLocation=\"records:2 file://records-v2.0.xsd\" " + "version=\"2.0\">"
                    + "<record><data name=\"Test Name\" value=\"Test value\"/></record>" + "</records>";

            Assert.assertEquals(example, xml);
        } finally {
            TaskScopeContextHolder.removeContext();
        }
    }
}
