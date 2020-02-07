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

package stroom.pipeline;


import org.junit.jupiter.api.Test;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.api.MetaService;
import stroom.util.io.ByteCountInputStream;
import stroom.util.io.StreamUtil;

import javax.inject.Inject;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestStreamAppender extends AbstractStreamAppenderTest {
    @Inject
    private MetaService dataMetaService;
    @Inject
    private Store streamStore;

    @Test
    void testXML() throws Exception {
        test("TestStreamAppender", "XML");
        validateOuptut("TestStreamAppender/TestStreamAppender_XML.out", "XML");
    }

    @Test
    void testXMLRolling() throws Exception {
        test("TestStreamAppender", "XML_Rolling");

        final List<Meta> list = dataMetaService.find(new FindMetaCriteria());
        final long id = list.get(0).getId();
        try (final Source streamSource = streamStore.openSource(id)) {
            try (final InputStreamProvider inputStreamProvider = streamSource.get(0)) {
                final ByteCountInputStream byteCountInputStream = new ByteCountInputStream(inputStreamProvider.get());
                StreamUtil.streamToString(byteCountInputStream);
                assertThat(byteCountInputStream.getCount()).isEqualTo(1198);
            }
        }
    }

    @Test
    void testText() throws Exception {
        test("TestStreamAppender", "Text");
        validateOuptut("TestStreamAppender/TestStreamAppender_Text.out", "Text");
    }
}