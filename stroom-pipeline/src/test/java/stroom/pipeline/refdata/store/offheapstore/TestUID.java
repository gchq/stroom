package stroom.pipeline.refdata.store.offheapstore;

import stroom.pipeline.refdata.util.ByteBufferUtils;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

class TestUID {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestUID.class);

    @Test
    void incrementUid() {
        final UID uid1 = UID.of(getNewUidBuffer(), 0, 0, 0, 1);
        LOGGER.info("uid1 {}", ByteBufferUtils.byteBufferInfo(uid1.getBackingBuffer()));
        final UID uid2 = UID.of(getNewUidBuffer(), 0, 0, 0, 2);
        LOGGER.info("uid2 {}", ByteBufferUtils.byteBufferInfo(uid2.getBackingBuffer()));

        UID.incrementUid(uid1.getBackingBuffer());

        Assertions.assertThat(uid1.getBackingBuffer())
                .isEqualByComparingTo(uid2.getBackingBuffer());
    }

    @Test
    void testEquals() {
        final UID uid1 = UID.of(getNewUidBuffer(), 0, 0, 0, 5);

        // Compare two buffers of different capacities
        final ByteBuffer byteBuffer = getNewUidBuffer();
        UID uid2 = UID.of(5, byteBuffer);

        LOGGER.info("uid1: {}", uid1);
        LOGGER.info("uid2: {}", uid2);

        Assertions.assertThat(uid2.getBackingBuffer())
                .isEqualByComparingTo(uid1.getBackingBuffer());

        Assertions.assertThat(uid2)
                .isEqualTo(uid1);
    }

    @Test
    void minimumUid() {
        final UID uid1 = UID.of(getNewUidBuffer(), 0, 0, 0, 0);
        final UID uid2 = UID.of(0, getNewUidBuffer());
        final UID uid3 = UID.minimumValue(getNewUidBuffer());

        Assertions.assertThat(uid2.getBackingBuffer())
                .isEqualByComparingTo(uid1.getBackingBuffer());

        Assertions.assertThat(uid3.getBackingBuffer())
                .isEqualByComparingTo(uid2.getBackingBuffer());
    }

    @Test
    void testClone() {
        final UID uid1 = UID.of(getNewUidBuffer(), 1, 2, 3, 4);
        final UID uid2 = uid1.cloneToBuffer(getNewUidBuffer());

        Assertions.assertThat(uid2.getBackingBuffer())
                .isEqualByComparingTo(uid1.getBackingBuffer());
    }

    ByteBuffer getNewUidBuffer() {
        // Don't use UID capacity for more realistic testing as we are normally using
        // pooled buffers of random sizes
        return ByteBuffer.allocateDirect(10);
    }
}