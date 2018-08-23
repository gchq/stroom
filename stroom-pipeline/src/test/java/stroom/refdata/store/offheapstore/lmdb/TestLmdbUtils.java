package stroom.refdata.store.offheapstore.lmdb;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.util.ByteBufferUtils;
import stroom.refdata.store.ProcessingState;
import stroom.refdata.store.RefDataProcessingInfo;
import stroom.refdata.store.offheapstore.serdes.RefDataProcessingInfoSerde;

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

        ByteBuffer outputBuffer = ByteBufferUtils.copyToDirectBuffer(sourceBuffer);

        LOGGER.debug(ByteBufferUtils.byteBufferInfo(outputBuffer));

        final RefDataProcessingInfo refDataProcessingInfo2 = serde.deserialize(outputBuffer);

        assertThat(refDataProcessingInfo2).isEqualTo(refDataProcessingInfo);

    }

}