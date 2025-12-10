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

package stroom.pipeline.refdata.store.offheapstore;

import stroom.bytebuffer.ByteBufferUtils;

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
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(uid1.getBackingBuffer().capacity() * 2);
        final UID uid2 = UID.of(5, byteBuffer);

        LOGGER.info("uid1: {}", uid1);
        LOGGER.info("uid2: {}", uid2);

        Assertions.assertThat(uid2.getBackingBuffer())
                .isEqualByComparingTo(uid1.getBackingBuffer());

        Assertions.assertThat(uid2)
                .isEqualTo(uid1);

        Assertions.assertThat(uid2.hashCode())
                .isEqualTo(uid1.hashCode());
    }

    @Test
    void testEquals2() {
        final UID uid1 = UID.of(getNewUidBuffer(), 0, 0, 0, 5);

        // Compare two buffers of different capacities and positions
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(uid1.getBackingBuffer().capacity() * 2);
        byteBuffer.position(2);
        byteBuffer.put(new byte[]{0, 0, 0, 5});
        byteBuffer.flip();
        byteBuffer.position(2);
        final UID uid2 = UID.wrap(byteBuffer);

        LOGGER.info("uid1: {}", uid1);
        LOGGER.info("uid2: {}", uid2);

        Assertions.assertThat(uid2.getBackingBuffer())
                .isEqualByComparingTo(uid1.getBackingBuffer());

        Assertions.assertThat(uid2)
                .isEqualTo(uid1);

        Assertions.assertThat(uid2.hashCode())
                .isEqualTo(uid1.hashCode());
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

    @Test
    void testCompare_equal() {
        final UID uid1 = UID.of(42, getNewUidBuffer());
        final UID uid2 = UID.of(42, getNewUidBuffer());
        Assertions.assertThat(uid1.compareTo(uid2))
                .isEqualTo(0);
    }

    @Test
    void testCompare_lessThan() {
        final UID uid1 = UID.of(41, getNewUidBuffer());
        final UID uid2 = UID.of(42, getNewUidBuffer());
        Assertions.assertThat(uid1.compareTo(uid2))
                .isLessThan(0);
    }

    @Test
    void testCompare_greaterThan() {
        final UID uid1 = UID.of(43, getNewUidBuffer());
        final UID uid2 = UID.of(42, getNewUidBuffer());
        Assertions.assertThat(uid1.compareTo(uid2))
                .isGreaterThan(0);
    }

    ByteBuffer getNewUidBuffer() {
        // Don't use UID capacity for more realistic testing as we are normally using
        // pooled buffers of random sizes
        return ByteBuffer.allocateDirect(10);
    }


}
