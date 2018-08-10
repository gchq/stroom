/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.refdata.offheapstore.serdes;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.offheapstore.ProcessingState;
import stroom.refdata.offheapstore.RefDataProcessingInfo;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

public class TestRefDataProcessingInfoSerde extends AbstractSerdeTest<RefDataProcessingInfo, RefDataProcessingInfoSerde> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestRefDataProcessingInfoSerde.class);

    @Test
    public void testSerializeDeserialize() {

        final RefDataProcessingInfo refDataProcessingInfo = new RefDataProcessingInfo(
                1234567890L,
                345678901L,
                56789012L,
                ProcessingState.COMPLETE);

        doSerialisationDeserialisationTest(refDataProcessingInfo);
    }

    @Test
    public void testUpdateState() {
        final RefDataProcessingInfo input = new RefDataProcessingInfo(
                1,
                1,
                1,
                ProcessingState.LOAD_IN_PROGRESS);

        final RefDataProcessingInfo expectedOutput = new RefDataProcessingInfo(
                1,
                1,
                1,
                ProcessingState.COMPLETE);

        doByteBufferModificationTest(
                input,
                expectedOutput,
                (serde, byteBuffer) ->
                        serde.updateState(byteBuffer, ProcessingState.COMPLETE));

    }

    @Test
    public void testUpdateLastAccessedTime() {
        final RefDataProcessingInfo input = new RefDataProcessingInfo(
                1,
                1,
                1,
                ProcessingState.LOAD_IN_PROGRESS);

        final RefDataProcessingInfo expectedOutput = new RefDataProcessingInfo(
                1,
                123,
                1,
                ProcessingState.LOAD_IN_PROGRESS);

        doByteBufferModificationTest(
                input,
                expectedOutput,
                (serde, byteBuffer) ->
                        serde.updateLastAccessedTime(byteBuffer, 123));
    }

    @Test
    public void testUpdateLastAccessedTimeAndState() {
        final RefDataProcessingInfo input = new RefDataProcessingInfo(
                1,
                1,
                1,
                ProcessingState.LOAD_IN_PROGRESS);

        final RefDataProcessingInfo expectedOutput = new RefDataProcessingInfo(
                1,
                123,
                1,
                ProcessingState.COMPLETE);

        doByteBufferModificationTest(
                input,
                expectedOutput,
                (serde, byteBuffer) -> {
                    serde.updateLastAccessedTime(byteBuffer, 123);
                    serde.updateState(byteBuffer, ProcessingState.COMPLETE);
                });
    }

    @Test
    public void wasAccessedAfter() {

        RefDataProcessingInfo refDataProcessingInfo = new RefDataProcessingInfo(
                0L,
                1000L,
                100L,
                ProcessingState.COMPLETE);


        ByteBuffer valueBuffer = serialize(refDataProcessingInfo);

        doAccessTest(1000L, valueBuffer, false);
        doAccessTest(999L, valueBuffer, true);
        doAccessTest(1001L, valueBuffer, false);


    }

    private void doAccessTest(final long timeUnderTestMs, final ByteBuffer valueBuffer, final boolean expectedResult) {
        ByteBuffer timeBuffer = ByteBuffer.allocate(Long.BYTES);
        timeBuffer.putLong(timeUnderTestMs);
        timeBuffer.flip();
        boolean result = RefDataProcessingInfoSerde.wasAccessedAfter(valueBuffer, timeBuffer);
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void testExtractProcessingState() {

        for (ProcessingState processingState : ProcessingState.values()) {
            RefDataProcessingInfo refDataProcessingInfo = new RefDataProcessingInfo(
                    0L,
                    1000L,
                    100L,
                    processingState);

            ByteBuffer valueBuffer = serialize(refDataProcessingInfo);

            ProcessingState foundProcessingState = RefDataProcessingInfoSerde.extractProcessingState(valueBuffer);

            assertThat(foundProcessingState).isEqualTo(processingState);
        }
    }

    @Override
    Class<RefDataProcessingInfoSerde> getSerdeType() {
        return RefDataProcessingInfoSerde.class;
    }
}