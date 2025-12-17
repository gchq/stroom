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

package stroom.pipeline.refdata.store.offheapstore.lmdb;


import stroom.bytebuffer.ByteBufferUtils;
import stroom.pipeline.refdata.store.ProcessingState;
import stroom.pipeline.refdata.store.RefDataProcessingInfo;
import stroom.pipeline.refdata.store.offheapstore.serdes.RefDataProcessingInfoSerde;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

class TestLmdbUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestLmdbUtils.class);

    @Test
    void copyDirectBuffer() {
        final ByteBuffer sourceBuffer = ByteBuffer.allocateDirect(50);


        final RefDataProcessingInfo refDataProcessingInfo = new RefDataProcessingInfo(
                1234567890L,
                345678901L,
                56789012L,
                ProcessingState.COMPLETE);

        final RefDataProcessingInfoSerde serde = new RefDataProcessingInfoSerde();

        serde.serialize(sourceBuffer, refDataProcessingInfo);

        LOGGER.debug(ByteBufferUtils.byteBufferInfo(sourceBuffer));

        final ByteBuffer outputBuffer = ByteBufferUtils.copyToDirectBuffer(sourceBuffer);

        LOGGER.debug(ByteBufferUtils.byteBufferInfo(outputBuffer));

        final RefDataProcessingInfo refDataProcessingInfo2 = serde.deserialize(outputBuffer);

        assertThat(refDataProcessingInfo2).isEqualTo(refDataProcessingInfo);

    }
}
