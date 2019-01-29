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

package stroom.data.store.impl.fs;

import org.junit.jupiter.api.Test;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.Source;
import stroom.data.store.api.Target;
import stroom.meta.impl.mock.MockMetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaProperties;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.util.io.StreamUtil;

import java.io.IOException;
import java.io.OutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>
 * Test the mock as it is quite complicated.
 * </p>
 */

class TestMockStreamStore {
    @Test
    void testExample() throws IOException {
        final MockMetaService mockMetaService = new MockMetaService();
        final MockStreamStore mockStreamStore = new MockStreamStore(mockMetaService);

        mockStreamStore.clear();

        final MetaProperties metaProperties = new MetaProperties.Builder()
                .feedName("TEST")
                .typeName(StreamTypeNames.EVENTS)
                .build();

        Meta meta = null;
        try (final Target streamTarget = mockStreamStore.openStreamTarget(metaProperties)) {
            meta = streamTarget.getMeta();

            try (final OutputStreamProvider outputStreamProvider = streamTarget.next()) {
                try (final OutputStream outputStream = outputStreamProvider.get()) {
                    outputStream.write("PARENT".getBytes(StreamUtil.DEFAULT_CHARSET));
                }
                try (final OutputStream outputStream = outputStreamProvider.get(StreamTypeNames.CONTEXT)) {
                    outputStream.write("CHILD".getBytes(StreamUtil.DEFAULT_CHARSET));
                }
            }

            assertThat(mockMetaService.find(FindMetaCriteria.createFromMeta(meta)).size()).isEqualTo(0);
        }

        assertThat(mockMetaService.find(FindMetaCriteria.createFromMeta(meta)).size()).isEqualTo(1);

        final Meta reload = mockMetaService.find(FindMetaCriteria.createFromMeta(meta)).get(0);

        try (final Source streamSource = mockStreamStore.openStreamSource(reload.getId())) {
            try (final InputStreamProvider inputStreamProvider = streamSource.get(0)) {
                String testMe = StreamUtil.streamToString(inputStreamProvider.get());
                assertThat(testMe).isEqualTo("PARENT");
                testMe = StreamUtil.streamToString(inputStreamProvider.get(StreamTypeNames.CONTEXT));
                assertThat(testMe).isEqualTo("CHILD");
            }
        }
    }
}
