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

package stroom.util;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.UUID;

class TestUuidUtil {

    @Test
    void test1() {
        final String uuid1 = UUID.randomUUID().toString();
        final ByteBuffer buffer = ByteBuffer.allocate(20);
        UuidUtil.writeUuid(uuid1, buffer);
        buffer.flip();
        final UUID uuid2 = UuidUtil.readUuid(buffer);
        Assertions.assertThat(uuid2.toString())
                .isEqualTo(uuid1);
    }

    @Test
    void test2() {
        final String uuid1 = UUID.randomUUID().toString();
        final ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.put(new byte[3]); // Put some random bytes
        UuidUtil.writeUuid(uuid1, buffer);
        buffer.flip();
        final UUID uuid2 = UuidUtil.readUuid(buffer, 3);
        Assertions.assertThat(uuid2.toString())
                .isEqualTo(uuid1);
    }

    @Test
    void test3() {
        final String uuid1 = UUID.randomUUID().toString();
        final ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.put(new byte[17]); // Put some random bytes, not enough room left for UUID
        Assertions.assertThatThrownBy(
                        () -> UuidUtil.writeUuid(uuid1, buffer))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void test4() {
        final String uuid1 = UUID.randomUUID().toString();
        final ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.put(new byte[17]); // Put some random bytes, not enough room left for UUID
        buffer.limit(20);
        Assertions.assertThatThrownBy(
                        () -> UuidUtil.readUuid(buffer, 17))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void test5() {
        final String uuid1 = UUID.randomUUID().toString();
        final byte[] bytes = UuidUtil.toByteArray(uuid1);
        final UUID uuid2 = UuidUtil.fromByteArray(bytes, 0);
        Assertions.assertThat(uuid2.toString())
                .isEqualTo(uuid1);
    }
}
