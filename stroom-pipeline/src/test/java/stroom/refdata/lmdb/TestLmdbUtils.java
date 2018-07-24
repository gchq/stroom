package stroom.refdata.lmdb;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.offheapstore.ByteBufferUtils;
import stroom.refdata.offheapstore.ProcessingState;
import stroom.refdata.offheapstore.RefDataProcessingInfo;
import stroom.refdata.offheapstore.serdes.RefDataProcessingInfoSerde;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

public class TestLmdbUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestLmdbUtils.class);

    @Test
    public void copyDirectBuffer() {
        ByteBuffer sourceBuffer = ByteBuffer.allocateDirect(50);


        final RefDataProcessingInfo refDataProcessingInfo = new RefDataProcessingInfo(
                1234567890L,
                345678901L,
                56789012L,
                ProcessingState.COMPLETE);

        final RefDataProcessingInfoSerde serde = new RefDataProcessingInfoSerde();

        serde.serialize(sourceBuffer, refDataProcessingInfo);

        LOGGER.debug(ByteBufferUtils.byteBufferInfo(sourceBuffer));

        ByteBuffer outputBuffer = LmdbUtils.copyDirectBuffer(sourceBuffer);

        LOGGER.debug(ByteBufferUtils.byteBufferInfo(outputBuffer));

        final RefDataProcessingInfo refDataProcessingInfo2 = serde.deserialize(outputBuffer);

        assertThat(refDataProcessingInfo2).isEqualTo(refDataProcessingInfo);

    }

}