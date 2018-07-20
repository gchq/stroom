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

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.offheapstore.RefDataProcessingInfo;

import java.nio.ByteBuffer;

public class TestRefDataProcessingInfoSerde extends AbstractSerdeTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestRefDataProcessingInfoSerde.class);

    @Test
    public void testSerializeDeserialize() {

        final RefDataProcessingInfo refDataProcessingInfo = new RefDataProcessingInfo(
                1234567890L,
                345678901L,
                56789012L,
                RefDataProcessingInfo.ProcessingState.COMPLETE);

        doSerialisationDeserialisationTest(refDataProcessingInfo, RefDataProcessingInfoSerde::new);
    }

    @Test
    public void testUpdateState() {
        final RefDataProcessingInfo input = new RefDataProcessingInfo(
                1,
                1,
                1,
                RefDataProcessingInfo.ProcessingState.LOAD_IN_PROGRESS);

        final RefDataProcessingInfo expectedOutput = new RefDataProcessingInfo(
                1,
                1,
                1,
                RefDataProcessingInfo.ProcessingState.COMPLETE);

        doByteBufferModificationTest(
                input,
                expectedOutput,
                RefDataProcessingInfoSerde::new,
                (serde, byteBuffer) ->
                        ((RefDataProcessingInfoSerde) serde).updateState(
                                byteBuffer,
                                RefDataProcessingInfo.ProcessingState.COMPLETE));

    }

    @Test
    public void testUpdateLastAccessedTime() {
        final RefDataProcessingInfo input = new RefDataProcessingInfo(
                1,
                1,
                1,
                RefDataProcessingInfo.ProcessingState.LOAD_IN_PROGRESS);

        final RefDataProcessingInfo expectedOutput = new RefDataProcessingInfo(
                1,
                123,
                1,
                RefDataProcessingInfo.ProcessingState.LOAD_IN_PROGRESS);

        doByteBufferModificationTest(
                input,
                expectedOutput,
                RefDataProcessingInfoSerde::new,
                (serde, byteBuffer) ->
                        ((RefDataProcessingInfoSerde) serde).updateLastAccessedTime(
                                byteBuffer,
                                123));
    }

    @Test
    public void testUpdateLastAccessedTimeAndState() {
        final RefDataProcessingInfo input = new RefDataProcessingInfo(
                1,
                1,
                1,
                RefDataProcessingInfo.ProcessingState.LOAD_IN_PROGRESS);

        final RefDataProcessingInfo expectedOutput = new RefDataProcessingInfo(
                1,
                123,
                1,
                RefDataProcessingInfo.ProcessingState.COMPLETE);

        doByteBufferModificationTest(
                input,
                expectedOutput,
                RefDataProcessingInfoSerde::new,
                (serde, byteBuffer) -> {
                    RefDataProcessingInfoSerde refDataProcessingInfoSerde = (RefDataProcessingInfoSerde) serde;
                    refDataProcessingInfoSerde.updateLastAccessedTime(byteBuffer, 123);
                    refDataProcessingInfoSerde.updateState(byteBuffer, RefDataProcessingInfo.ProcessingState.COMPLETE);
                });
    }

    @Test
    public void wasAccessedAfter() {

        RefDataProcessingInfoSerde serde = new RefDataProcessingInfoSerde();
        RefDataProcessingInfo refDataProcessingInfo = new RefDataProcessingInfo(
                0L,
                1000L,
                100L,
                RefDataProcessingInfo.ProcessingState.COMPLETE);


        ByteBuffer valueBuffer = ByteBuffer.allocate(30);
        serde.serialize(valueBuffer, refDataProcessingInfo);

        doAccessTest(1000L, valueBuffer, false);
        doAccessTest(999L, valueBuffer, true);
        doAccessTest(1001L, valueBuffer, false);


    }

    private void doAccessTest(final long timeUnderTestMs, final ByteBuffer valueBuffer, final boolean expectedResult) {
        ByteBuffer timeBuffer = ByteBuffer.allocate(Long.BYTES);
        timeBuffer.putLong(timeUnderTestMs);
        timeBuffer.flip();
        boolean result = RefDataProcessingInfoSerde.wasAccessedAfter(valueBuffer, timeBuffer);
        Assertions.assertThat(result).isEqualTo(expectedResult);
    }




}