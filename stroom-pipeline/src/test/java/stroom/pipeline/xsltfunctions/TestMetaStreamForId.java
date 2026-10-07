/*
 * Copyright 2025 Crown Copyright
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

import stroom.data.store.api.AttributeMapFactory;
import stroom.meta.api.AttributeMap;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.Sequence;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class TestMetaStreamForId extends AbstractXsltFunctionTest<MetaStreamForId> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestMetaStreamForId.class);

    @Mock
    private AttributeMapFactory attributeMapFactory;
    @InjectMocks
    private MetaStreamForId metaStream;

    @Test
    void call_withArgs() throws Exception {
        Mockito.when(getMockXPathContext().getConfiguration())
                .thenReturn(Configuration.newConfiguration());

        final long streamId = 123L;
        final long partNo = 1L;

        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("Feed", "myFeed");
        attributeMap.put("Action", "myAction");

        Mockito.when(attributeMapFactory.getAttributeMapForPart(streamId, partNo))
                .thenReturn(attributeMap);

        final Sequence sequence = callFunctionWithSimpleArgs(streamId, partNo);
        assertThat(sequence).isNotNull();

        final String xml = getAsSerialisedXmlString(sequence).orElseThrow();

        assertThat(xml).isEqualToIgnoringWhitespace("""
                <meta-stream>
                   <string key="Action">myAction</string>
                   <string key="Feed">myFeed</string>
                </meta-stream>
                """);
    }

    @Override
    MetaStreamForId getXsltFunction() {
        return metaStream;
    }

    @Override
    String getFunctionName() {
        return MetaStreamForId.FUNCTION_NAME;
    }
}
