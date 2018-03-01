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

package stroom.xml.converter.datasplitter;

import org.junit.Assert;
import org.junit.Test;
import stroom.pipeline.shared.TextConverter.TextConverterType;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.util.spring.StroomBeanStore;
import stroom.util.task.TaskScopeContextHolder;
import stroom.xml.F2XTestUtil;
import stroom.xml.XMLValidator;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class TestDataSplitter2 extends AbstractProcessIntegrationTest {
    @Inject
    private StroomBeanStore beanStore;

    /**
     * Tests a multi line regex file.
     *
     * @throws Exception Might be thrown while performing the test.
     */
    @Test
    public void testMultiLineRegex() {
        final String xml = runF2XTest(TextConverterType.DATA_SPLITTER, "TestDataSplitter/MultiLineRegex.ds",
                new ByteArrayInputStream("0123456789abcdeignore".getBytes()));

        final String example = "<?xml version=\"1.1\" encoding=\"UTF-8\"?>" + "<records " + "xmlns=\"records:2\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xsi:schemaLocation=\"records:2 file://records-v2.0.xsd\" " + "version=\"2.0\">" + "<record>"
                + "<data value=\"0123456789\"/>" + "<data value=\"abcde\"/>"
                + "</record></records>";

        Assert.assertEquals(example, xml);
    }

    private String runF2XTest(final TextConverterType textConverterType, final String textConverterLocation,
                              final InputStream inputStream) {
        validate(textConverterType, textConverterLocation);

        final F2XTestUtil f2xTestUtil = beanStore.getBean(F2XTestUtil.class);
        final String xml = f2xTestUtil.runF2XTest(textConverterType, textConverterLocation, inputStream);
        return xml;
    }

    private void validate(final TextConverterType textConverterType, final String textConverterLocation) {
        try {
            TaskScopeContextHolder.addContext();
            final XMLValidator xmlValidator = beanStore.getBean(XMLValidator.class);
            // Start by validating the resource.
            if (textConverterType == TextConverterType.DATA_SPLITTER) {
                final String message = xmlValidator.getInvalidXmlResourceMessage(textConverterLocation, true);
                Assert.assertTrue(message, message.length() == 0);
            }
        } finally {
            TaskScopeContextHolder.removeContext();
        }
    }
}
