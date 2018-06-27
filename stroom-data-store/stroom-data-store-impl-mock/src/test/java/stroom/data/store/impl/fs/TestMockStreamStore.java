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
import stroom.data.meta.api.FindDataCriteria;
import stroom.data.meta.api.Data;
import stroom.data.meta.api.DataProperties;
import stroom.data.meta.impl.mock.MockDataMetaService;
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
        final MockDataMetaService mockStreamMetaService = new MockDataMetaService();
        final MockStreamStore mockStreamStore = new MockStreamStore(mockStreamMetaService);

        mockStreamStore.clear();

        final DataProperties streamProperties = new DataProperties.Builder()
                .feedName("TEST")
                .typeName(StreamTypeNames.EVENTS)
                .build();

        final StreamTarget streamTarget = mockStreamStore.openStreamTarget(streamProperties);
        final Data stream = streamTarget.getStream();

        try (final OutputStreamProvider outputStreamProvider = streamTarget.getOutputStreamProvider()) {
            try (final OutputStream outputStream = outputStreamProvider.next()) {
                outputStream.write("PARENT".getBytes(StreamUtil.DEFAULT_CHARSET));
            }
            try (final OutputStream outputStream = outputStreamProvider.next(StreamTypeNames.CONTEXT)) {
                outputStream.write("CHILD".getBytes(StreamUtil.DEFAULT_CHARSET));
            }
        }

        assertThat(mockStreamMetaService.find(FindDataCriteria.createWithData(stream)).size()).isEqualTo(0);

        mockStreamStore.closeStreamTarget(streamTarget);

        assertThat(mockStreamMetaService.find(FindDataCriteria.createWithData(stream)).size()).isEqualTo(1);

        final Data reload = mockStreamMetaService.find(FindDataCriteria.createWithData(stream)).get(0);

        final StreamSource streamSource = mockStreamStore.openStreamSource(reload.getId());

        String testMe = StreamUtil.streamToString(streamSource.getInputStream());

        assertThat(testMe).isEqualTo("PARENT");

        testMe = StreamUtil.streamToString(streamSource.getChildStream(InternalStreamTypeNames.SEGMENT_INDEX).getInputStream());

        assertThat(testMe).isEqualTo("CHILD");
    }
}
