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

import stroom.data.store.api.DataException;
import stroom.data.store.api.DataService;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestManifest extends AbstractXsltFunctionTest<Manifest> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestManifest.class);

    @Mock
    private Meta mockMeta;
    @Mock
    private DataService mockDataService;
    @Mock
    private MetaHolder mockMetaHolder;
    @InjectMocks
    private Manifest manifest;

    @Test
    void call() {
        Mockito.when(getMockXPathContext().getConfiguration()).thenReturn(Configuration.newConfiguration());

        Mockito.when(mockMeta.getId()).thenReturn(1234L);
        Mockito.when(mockMetaHolder.getMeta()).thenReturn(mockMeta);

        Mockito.when(mockDataService.metaAttributes(1234L)).thenReturn(
                Map.of("key1", "value1", "key2", "value2"));

        final Sequence sequence = callFunctionWithSimpleArgs();
        assertThat(sequence).isNotNull();

        final String xml = getAsSerialisedXmlString(sequence).orElseThrow();

        assertThat(xml).isEqualToIgnoringWhitespace("""
                        <manifest>
                            <string key="key1">value1</string>
                            <string key="key2">value2</string>
                        </manifest>
                        """);
    }

    @Test
    void call_dataService_exception() {
        Mockito.when(getMockXPathContext().getConfiguration()).thenReturn(Configuration.newConfiguration());

        Mockito.when(mockMeta.getId()).thenReturn(1234L);
        Mockito.when(mockMetaHolder.getMeta()).thenReturn(mockMeta);

        Mockito.when(mockDataService.metaAttributes(1234L)).thenThrow(new DataException("error"));

        final Sequence sequence = callFunctionWithSimpleArgs();
        assertThat(sequence).isNotNull();

        final String xml = getAsSerialisedXmlString(sequence).orElseThrow();

        assertThat(xml).isEqualToIgnoringWhitespace("""
                <manifest/>
                """);
    }

    @Override
    Manifest getXsltFunction() {
        return manifest;
    }

    @Override
    String getFunctionName() {
        return Manifest.FUNCTION_NAME_NO_ARGS;
    }
}
