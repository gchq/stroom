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

package stroom.pipeline.xml.converter.datasplitter;


import stroom.pipeline.shared.TextConverterDoc.TextConverterType;
import stroom.pipeline.xml.F2XTestUtil;
import stroom.pipeline.xml.XMLValidator;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.util.pipeline.scope.PipelineScopeRunnable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestDataSplitter2 extends AbstractProcessIntegrationTest {

    @Inject
    private Provider<F2XTestUtil> f2XTestUtilProvider;
    @Inject
    private Provider<XMLValidator> xmlValidatorProvider;
    @Inject
    private PipelineScopeRunnable pipelineScopeRunnable;

    /**
     * Tests a multi line regex file.
     */
    @Test
    void testMultiLineRegex() {
        final String xml = runF2XTest(TextConverterType.DATA_SPLITTER, "TestDataSplitter/MultiLineRegex.ds",
                new ByteArrayInputStream("0123456789abcdeignore".getBytes()));

        final String example = "<?xml version=\"1.1\" encoding=\"UTF-8\"?>" + "<records " + "xmlns=\"records:2\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xsi:schemaLocation=\"records:2 file://records-v2.0.xsd\" " + "version=\"2.0\">" + "<record>"
                + "<data value=\"0123456789\"/>" + "<data value=\"abcde\"/>"
                + "</record></records>";

        assertThat(xml).isEqualTo(example);
    }

    private String runF2XTest(final TextConverterType textConverterType, final String textConverterLocation,
                              final InputStream inputStream) {
        return pipelineScopeRunnable.scopeResult(() -> {
            validate(textConverterType, textConverterLocation);

            final F2XTestUtil f2xTestUtil = f2XTestUtilProvider.get();
            final String xml = f2xTestUtil.runF2XTest(textConverterType, textConverterLocation, inputStream);
            return xml;
        });
    }

    private void validate(final TextConverterType textConverterType, final String textConverterLocation) {
        pipelineScopeRunnable.scopeRunnable(() -> {
            final XMLValidator xmlValidator = xmlValidatorProvider.get();
            // Start by validating the resource.
            if (textConverterType == TextConverterType.DATA_SPLITTER) {
                final String message = xmlValidator.getInvalidXmlResourceMessage(textConverterLocation, true);
                assertThat(message.length() == 0).as(message).isTrue();
            }
        });
    }
}
