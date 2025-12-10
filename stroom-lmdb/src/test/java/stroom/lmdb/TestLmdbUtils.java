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

package stroom.lmdb;


import stroom.bytebuffer.ByteBufferUtils;
import stroom.lmdb.stream.LmdbIterable;
import stroom.util.io.ByteSize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TestLmdbUtils {

    @Test
    void copyDirectBuffer() {
        final ByteBuffer sourceBuffer = ByteBuffer.allocateDirect(Integer.BYTES);
        sourceBuffer.putInt(5);
        sourceBuffer.flip();
        final ByteBuffer outputBuffer = ByteBufferUtils.copyToDirectBuffer(sourceBuffer);
        assertThat(outputBuffer.getInt()).isEqualTo(5);
    }

    @Test
    void testIterator(@TempDir final Path path) {
        final int iterations = 100;
        try (final Env<ByteBuffer> env = Env.create()
                .setMapSize(ByteSize.ofMebibytes(1).getBytes())
                .open(path.toFile())) {
            final Dbi<ByteBuffer> dbi = setupDb(env, iterations);

            final AtomicInteger count = new AtomicInteger();
            try (final Txn<ByteBuffer> txn = env.txnRead()) {
                LmdbIterable.iterate(txn, dbi, (key, val) -> {
                    count.incrementAndGet();
                });
            }

            assertThat(count.get()).isEqualTo(iterations * iterations);
        }
    }

    @Test
    void testNativeIterator(@TempDir final Path path) {
        final int iterations = 100;
        try (final Env<ByteBuffer> env = Env.create()
                .setMapSize(ByteSize.ofMebibytes(1).getBytes())
                .open(path.toFile())) {
            final Dbi<ByteBuffer> dbi = setupDb(env, iterations);

            final AtomicInteger count = new AtomicInteger();
            try (final Txn<ByteBuffer> txn = env.txnRead()) {
                for (final KeyVal<ByteBuffer> kv : dbi.iterate(txn)) {
                    count.incrementAndGet();
                }
            }

            assertThat(count.get()).isEqualTo(iterations * iterations);
        }
    }

//    @Test
//    void testPrefixIterator(@TempDir final Path path) {
//        final int iterations = 100;
//        try (final Env<ByteBuffer> env = Env.create()
//                .setMapSize(ByteSize.ofMebibytes(1).getBytes())
//                .open(path.toFile())) {
//            final Dbi<ByteBuffer> dbi = setupDb(env, iterations);
//
//            final AtomicInteger count = new AtomicInteger();
//            final ByteBuffer prefix = ByteBuffer.allocateDirect(Integer.BYTES);
//            for (int i = 0; i < iterations; i++) {
//                prefix.putInt(10);
//                prefix.flip();
//            }
//
//            try (final Txn<ByteBuffer> txn = env.txnRead()) {
//                LmdbIterableSupport.iteratePrefix(txn, dbi, prefix, (key, val) -> {
//                    count.incrementAndGet();
//                });
//            }
//
//            assertThat(count.get()).isEqualTo(iterations);
//        }
//    }

    private Dbi<ByteBuffer> setupDb(final Env<ByteBuffer> env,
                                    final int iterations) {
        final Dbi<ByteBuffer> dbi = env.openDbi("test".getBytes(StandardCharsets.UTF_8), DbiFlags.MDB_CREATE);
        try (final Txn<ByteBuffer> txn = env.txnWrite()) {
            final ByteBuffer key = ByteBuffer.allocateDirect(Integer.BYTES + Integer.BYTES);
            final ByteBuffer value = ByteBuffer.allocateDirect(0);
            for (int i = 0; i < iterations; i++) {
                for (int j = 0; j < iterations; j++) {
                    key.putInt(i);
                    key.putInt(j);
                    key.flip();
                    dbi.put(txn, key, value);
                }
            }
            txn.commit();
        }
        return dbi;
    }
}
