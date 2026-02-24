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

package stroom.planb.impl.db;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.serde.hash.Hash;
import stroom.planb.impl.serde.hash.HashClashCount;
import stroom.planb.impl.serde.hash.HashFactory;
import stroom.planb.impl.serde.hash.IntegerHashFactory;
import stroom.planb.impl.serde.hash.LongHashFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TestHashLookupDb {

    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(new ByteBufferFactoryImpl());
    static final Long DEFAULT_MAX_STORE_SIZE = 10737418240L;

    @Test
    void testIntegerHashFactory(@TempDir final Path tempDir) {
        final HashFactory hashFactory = new IntegerHashFactory();
        test(tempDir, hashFactory, 1000000, 116);
    }

    @Test
    void testLongHashFactory(@TempDir final Path tempDir) {
        final HashFactory hashFactory = new LongHashFactory();
        test(tempDir, hashFactory, 1000000, 0);
    }

    @Test
    void testDeliberateClashes(@TempDir final Path tempDir) {
        final HashFactory hashFactory = new BadHashFactory();
        test(tempDir, hashFactory, 1000, 999);
    }

    @Test
    void testIntegerHashClash(@TempDir final Path tempDir) {
        final HashFactory hashFactory = new IntegerHashFactory();
        try (final PlanBEnv env = new PlanBEnv(
                tempDir,
                DEFAULT_MAX_STORE_SIZE,
                1,
                false,
                new HashClashCommitRunnable())) {
            // Define some things we know cause a hash clash.
            final String string1 = "test59126";
            final String string2 = "test102406";
            final byte[] bytes1 = string1.getBytes(StandardCharsets.UTF_8);
            final byte[] bytes2 = string2.getBytes(StandardCharsets.UTF_8);
            final byte[] hash1 = new byte[]{95, 22, 35, -42};
            final byte[] hash2 = new byte[]{95, 22, 35, -42, 1};

            final AtomicInteger count = new AtomicInteger();
            final HashClashCount hashClashCount = count::getAndIncrement;
            final HashLookupDb db = new HashLookupDb(env, BYTE_BUFFERS, hashFactory, hashClashCount, "keys");
            env.write(writer -> {
                db.put(writer.getWriteTxn(), bytes1, idByteBuffer -> null);
                db.put(writer.getWriteTxn(), bytes2, idByteBuffer -> null);
            });

            env.read(readTxn -> {
                db.get(readTxn, bytes1, optionalIdByteBuffer -> {
                    assertThat(optionalIdByteBuffer).isPresent();
                    assertThat(ByteBufferUtils.toBytes(optionalIdByteBuffer.get()))
                            .isEqualTo(hash1);
                    return null;
                });
                db.get(readTxn, bytes2, optionalIdByteBuffer -> {
                    assertThat(optionalIdByteBuffer).isPresent();
                    assertThat(ByteBufferUtils.toBytes(optionalIdByteBuffer.get()))
                            .isEqualTo(hash2);
                    return null;
                });
                BYTE_BUFFERS.useBytes(hash1, byteBuffer -> {
                    final ByteBuffer value = db.getValue(readTxn, byteBuffer);
                    assertThat(value).isNotNull();
                    assertThat(ByteBufferUtils.toString(value)).isEqualTo(string1);
                });
                BYTE_BUFFERS.useBytes(hash2, byteBuffer -> {
                    final ByteBuffer value = db.getValue(readTxn, byteBuffer);
                    assertThat(value).isNotNull();
                    assertThat(ByteBufferUtils.toString(value)).isEqualTo(string2);
                });

                return null;
            });
        }
    }

    private void test(final Path tempDir,
                      final HashFactory hashFactory,
                      final int iterations,
                      final int expectedHashClashes) {
        try (final PlanBEnv env = new PlanBEnv(
                tempDir,
                DEFAULT_MAX_STORE_SIZE,
                1,
                false,
                new HashClashCommitRunnable())) {
            final AtomicInteger count = new AtomicInteger();
            final HashClashCount hashClashCount = count::getAndIncrement;
            final HashLookupDb db = new HashLookupDb(env, BYTE_BUFFERS, hashFactory, hashClashCount, "keys");
            env.write(writer -> {
                for (int i = 0; i < iterations; i++) {
                    final String key = "test" + i;
                    final byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
                    db.put(writer.getWriteTxn(), bytes, idByteBuffer -> null);
                }
            });

            env.read(readTxn -> {
                for (int i = 0; i < iterations; i++) {
                    final byte[] bytes = ("test" + i).getBytes(StandardCharsets.UTF_8);
                    db.get(readTxn, bytes, optionalIdByteBuffer -> {
                        assertThat(optionalIdByteBuffer).isPresent();
                        final ByteBuffer value = db.getValue(readTxn, optionalIdByteBuffer.get());
                        assertThat(value).isNotNull();
                        assertThat(ByteBufferUtils.toBytes(value)).isEqualTo(bytes);
                        return null;
                    });
                }
                return null;
            });

            assertThat(count.get()).isEqualTo(expectedHashClashes);
        }
    }

    private static class BadHashFactory implements HashFactory {

        @Override
        public Hash create(final byte[] bytes) {
            return new BadHash();
        }

        @Override
        public Hash create(final ByteBuffer byteBuffer) {
            return new BadHash();
        }

        @Override
        public int hashLength() {
            return 1;
        }
    }

    private static class BadHash implements Hash {

        @Override
        public void write(final ByteBuffer byteBuffer) {
            byteBuffer.put((byte) 0);
        }

        @Override
        public int len() {
            return 1;
        }
    }
}
