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

import stroom.data.store.api.AttributeMapFactory;
import stroom.meta.api.AttributeMap;
import stroom.meta.shared.Meta;
import stroom.pipeline.state.MetaHolder;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.Sequence;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class TestMetaStream extends AbstractXsltFunctionTest<MetaStream> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestMetaStream.class);

    @Mock
    private Meta mockMeta;
    @Mock
    private AttributeMapFactory attributeMapFactory;
    @Mock
    private MetaHolder mockMetaHolder;
    @InjectMocks
    private MetaStream metaStream;

    @Test
    void call() throws Exception {
        Mockito.when(getMockXPathContext().getConfiguration()).thenReturn(Configuration.newConfiguration());

        Mockito.when(mockMeta.getId()).thenReturn(1L);
        Mockito.when(mockMetaHolder.getPartIndex()).thenReturn(0L);
        Mockito.when(mockMetaHolder.getMeta()).thenReturn(mockMeta);

        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("Feed", "myFeed");
        attributeMap.put("Action", "myAction");

        Mockito.when(attributeMapFactory.getAttributeMapForPart(1L, 0L)).thenReturn(attributeMap);

        final Sequence sequence = callFunctionWithSimpleArgs();
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
    MetaStream getXsltFunction() {
        return metaStream;
    }

    @Override
    String getFunctionName() {
        return MetaStream.FUNCTION_NAME_NO_ARGS;
    }
}
