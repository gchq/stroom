package stroom.pipeline.refdata.store.offheapstore;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

class TestUID {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestUID.class);

    @Test
    void nextUid() {
        final UID uid1 = UID.of(0, 0, 0, 1);
        final UID uid2 = uid1.nextUid();

        Assertions.assertThat(uid2.getBackingBuffer())
                .isEqualByComparingTo(UID.of(0,0,0,2).getBackingBuffer());
    }

    @Test
    void testEquals() {
        final UID uid1 = UID.of(0, 0, 0, 5);

        // Compare two buffers of different capacities
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(10);
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
        final UID uid1 = UID.of(0, 0, 0, 0);
        final UID uid2 = UID.of(0, ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH));
        final UID uid3 = UID.minimumValue(ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH));

        Assertions.assertThat(uid2.getBackingBuffer())
                .isEqualByComparingTo(uid1.getBackingBuffer());

        Assertions.assertThat(uid3.getBackingBuffer())
                .isEqualByComparingTo(uid2.getBackingBuffer());
    }

    @Test
    void testClone() {
        final UID uid1 = UID.of(1, 2, 3, 4);
        final UID uid2 = uid1.clone(ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH));

        Assertions.assertThat(uid2.getBackingBuffer())
                .isEqualByComparingTo(uid1.getBackingBuffer());
    }

}