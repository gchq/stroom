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

package stroom.pipeline.refdata.store.offheapstore.serdes;


import stroom.pipeline.refdata.store.ProcessingState;
import stroom.pipeline.refdata.store.RefDataProcessingInfo;

import com.google.inject.TypeLiteral;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestRefDataProcessingInfoSerde extends AbstractSerdeTest<RefDataProcessingInfo, RefDataProcessingInfoSerde> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestRefDataProcessingInfoSerde.class);

    @Test
    void testSerializeDeserialize() {

        final RefDataProcessingInfo refDataProcessingInfo = new RefDataProcessingInfo(
                1234567890L,
                345678901L,
                56789012L,
                ProcessingState.COMPLETE);

        doSerialisationDeserialisationTest(refDataProcessingInfo);
    }

    @Test
    void testUpdateState() {
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
    void testUpdateLastAccessedTime() {
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
    void testUpdateLastAccessedTimeAndState() {
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
    void wasAccessedAfter() {

        final RefDataProcessingInfo refDataProcessingInfo = new RefDataProcessingInfo(
                0L,
                1000L,
                100L,
                ProcessingState.COMPLETE);

        final ByteBuffer valueBuffer = serialize(refDataProcessingInfo);

        doAccessTest(refDataProcessingInfo.getLastAccessedTimeEpochMs(), valueBuffer, false);
        doAccessTest(refDataProcessingInfo.getLastAccessedTimeEpochMs() - 1, valueBuffer, true);
        doAccessTest(refDataProcessingInfo.getLastAccessedTimeEpochMs() + 1, valueBuffer, false);
    }

    private void doAccessTest(final long timeUnderTestMs, final ByteBuffer valueBuffer, final boolean expectedResult) {
        final ByteBuffer timeBuffer = ByteBuffer.allocate(Long.BYTES);
        timeBuffer.putLong(timeUnderTestMs);
        timeBuffer.flip();
        final boolean result = RefDataProcessingInfoSerde.wasAccessedAfter(valueBuffer, timeBuffer);
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    void testExtractProcessingState() {

        for (final ProcessingState processingState : ProcessingState.values()) {
            final RefDataProcessingInfo refDataProcessingInfo = new RefDataProcessingInfo(
                    0L,
                    1000L,
                    100L,
                    processingState);

            final ByteBuffer valueBuffer = serialize(refDataProcessingInfo);

            final ProcessingState foundProcessingState = RefDataProcessingInfoSerde.extractProcessingState(valueBuffer);

            assertThat(foundProcessingState).isEqualTo(processingState);
        }
    }



    @Test
    void testCreateProcessingStatePredicate() {
        final RefDataProcessingInfo refDataProcessingInfo1 = new RefDataProcessingInfo(
                0L,
                1000L,
                100L,
                ProcessingState.COMPLETE);

        final RefDataProcessingInfo refDataProcessingInfo2 = new RefDataProcessingInfo(
                0L,
                1000L,
                100L,
                ProcessingState.FAILED);

        final RefDataProcessingInfo refDataProcessingInfo3 = new RefDataProcessingInfo(
                0L,
                1000L,
                100L,
                ProcessingState.TERMINATED);

        final Predicate<ByteBuffer> processingStatePredicate =
                RefDataProcessingInfoSerde.createProcessingStatePredicate(
                        ProcessingState.COMPLETE,
                        ProcessingState.FAILED);

        Assertions.assertThat(Stream.of(refDataProcessingInfo1, refDataProcessingInfo2, refDataProcessingInfo3)
                        .map(this::serialize)
                        .filter(processingStatePredicate)
                        .count())
                .isEqualTo(2);
    }

    @Override
    TypeLiteral<RefDataProcessingInfoSerde> getSerdeType() {
        return new TypeLiteral<RefDataProcessingInfoSerde>(){};
    }
}
