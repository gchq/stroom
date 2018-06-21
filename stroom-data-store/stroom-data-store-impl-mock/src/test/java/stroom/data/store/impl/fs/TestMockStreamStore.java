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
import stroom.data.meta.api.FindStreamCriteria;
import stroom.data.meta.api.Stream;
import stroom.data.meta.api.StreamProperties;
import stroom.data.meta.impl.mock.MockStreamMetaService;
import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.StreamSource;
import stroom.data.store.api.StreamTarget;
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
        final MockStreamMetaService mockStreamMetaService = new MockStreamMetaService();
        final MockStreamStore mockStreamStore = new MockStreamStore(mockStreamMetaService);

        mockStreamStore.clear();

        final StreamProperties streamProperties = new StreamProperties.Builder()
                .feedName("TEST")
                .streamTypeName(StreamTypeNames.EVENTS)
                .build();

        final StreamTarget streamTarget = mockStreamStore.openStreamTarget(streamProperties);
        final Stream stream = streamTarget.getStream();

        try (final OutputStreamProvider outputStreamProvider = streamTarget.getOutputStreamProvider()) {
            try (final OutputStream outputStream = outputStreamProvider.next()) {
                outputStream.write("PARENT".getBytes(StreamUtil.DEFAULT_CHARSET));
            }
            try (final OutputStream outputStream = outputStreamProvider.next(StreamTypeNames.CONTEXT)) {
                outputStream.write("CHILD".getBytes(StreamUtil.DEFAULT_CHARSET));
            }
        }

        assertThat(mockStreamMetaService.find(FindStreamCriteria.createWithStream(stream)).size()).isEqualTo(0);

        mockStreamStore.closeStreamTarget(streamTarget);

        assertThat(mockStreamMetaService.find(FindStreamCriteria.createWithStream(stream)).size()).isEqualTo(1);

        final Stream reload = mockStreamMetaService.find(FindStreamCriteria.createWithStream(stream)).get(0);

        final StreamSource streamSource = mockStreamStore.openStreamSource(reload.getId());

        String testMe = StreamUtil.streamToString(streamSource.getInputStream());

        assertThat(testMe).isEqualTo("PARENT");

        testMe = StreamUtil.streamToString(streamSource.getChildStream(InternalStreamTypeNames.SEGMENT_INDEX).getInputStream());

        assertThat(testMe).isEqualTo("CHILD");
    }
}
