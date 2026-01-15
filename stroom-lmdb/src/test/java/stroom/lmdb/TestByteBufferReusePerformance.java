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


import stroom.bytebuffer.ByteBufferPoolFactory;
import stroom.bytebuffer.ByteBufferSupport;
import stroom.lmdb.serde.Serde;
import stroom.lmdb.serde.StringSerde;
import stroom.util.io.ByteSize;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.RepeatedTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

// tests are performance comparison only so are intended for manual runs only

@Disabled
class TestByteBufferReusePerformance extends AbstractLmdbDbTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestByteBufferReusePerformance.class);
    private static final LambdaLogger LAMBDA_LOGGER =
            LambdaLoggerFactory.getLogger(TestByteBufferReusePerformance.class);

    private static final ByteSize DB_MAX_SIZE = ByteSize.ofMebibytes(500);
    private static final int VALUE_BUFFER_SIZE = 1_000;
    private static final int REC_COUNT = 2_000_000;
    private static final int TEST_REPEAT_COUNT = 3;
    private final Serde<String> stringSerde = new StringSerde();
    private BasicLmdbDb<String, String> basicLmdbDb;

    @BeforeEach
    void setup() {
        basicLmdbDb = new BasicLmdbDb<>(
                lmdbEnv,
                new ByteBufferPoolFactory().getByteBufferPool(),
                stringSerde,
                stringSerde,
                "basicDb");

        //just to ensure the DB is created and ready to use
        basicLmdbDb.get("xxx");
    }

    @Override
    protected ByteSize getMaxSizeBytes() {
        return DB_MAX_SIZE;
    }


    @RepeatedTest(TEST_REPEAT_COUNT)
    void testNoReuse() {

        LAMBDA_LOGGER.logDurationIfDebugEnabled(() -> {

            lmdbEnv.doWithWriteTxn(writeTxn -> {
                for (int i = 0; i < REC_COUNT; i++) {
                    // Allocate new buffers each time
                    final ByteBuffer keyBuffer = ByteBuffer.allocateDirect(lmdbEnv.getMaxKeySize());
                    final ByteBuffer valueBuffer = ByteBuffer.allocateDirect(VALUE_BUFFER_SIZE);

                    stringSerde.serialize(keyBuffer, "key" + i);
                    stringSerde.serialize(valueBuffer, "value" + i);

                    basicLmdbDb.put(
                            writeTxn,
                            keyBuffer,
                            valueBuffer,
                            false);
                    // Destroy the buffers
                    ByteBufferSupport.unmap(keyBuffer);
                    ByteBufferSupport.unmap(valueBuffer);
                }
            });

        }, "testNoReuse-put");

        LAMBDA_LOGGER.logDurationIfDebugEnabled(() -> {

            for (int i = 0; i < REC_COUNT; i++) {
                // Allocate new buffers each time
                final ByteBuffer keyBuffer = ByteBuffer.allocateDirect(lmdbEnv.getMaxKeySize());

                stringSerde.serialize(keyBuffer, "key" + i);

                lmdbEnv.doWithReadTxn(txn -> {
                    final Optional<ByteBuffer> optValue = basicLmdbDb.getAsBytes(
                            txn,
                            keyBuffer);
//                        assertThat(optValue).isPresent();
                });
                // Destroy the buffer
                ByteBufferSupport.unmap(keyBuffer);
            }
        }, "testNoReuse-get");

        LOGGER.debug("Finished");
    }

    @RepeatedTest(TEST_REPEAT_COUNT)
    void testReuse() {

        LAMBDA_LOGGER.logDurationIfDebugEnabled(() -> {

            final ByteBuffer keyBuffer = ByteBuffer.allocateDirect(lmdbEnv.getMaxKeySize());
            final ByteBuffer valueBuffer = ByteBuffer.allocateDirect(VALUE_BUFFER_SIZE);

            lmdbEnv.doWithWriteTxn(writeTxn -> {
                for (int i = 0; i < REC_COUNT; i++) {
                    stringSerde.serialize(keyBuffer, "key" + i);
                    stringSerde.serialize(valueBuffer, "value" + i);

                    basicLmdbDb.put(writeTxn, keyBuffer, valueBuffer, false);
                    keyBuffer.clear();
                    valueBuffer.clear();
                }
            });

        }, "testReuse-put");

        LAMBDA_LOGGER.logDurationIfDebugEnabled(() -> {

            final ByteBuffer keyBuffer = ByteBuffer.allocateDirect(lmdbEnv.getMaxKeySize());
            for (int i = 0; i < REC_COUNT; i++) {
                stringSerde.serialize(keyBuffer, "key" + i);

                final int j = i;

                lmdbEnv.doWithReadTxn(txn -> {
                    final Optional<ByteBuffer> optValue = basicLmdbDb.getAsBytes(txn, keyBuffer);
//                    assertThat(optValue).isPresent();
//                    assertThat(optValue.map(bb -> stringSerde.deserialize(bb)).get()).isEqualTo("value" + j);
                    keyBuffer.clear();
                });
            }

        }, "testReuse-get");

        LOGGER.debug("Finished");

    }

    @RepeatedTest(TEST_REPEAT_COUNT)
    void testPooled() {

        LAMBDA_LOGGER.logDurationIfDebugEnabled(() -> {

            final BlockingQueue<ByteBuffer> keyPool = new LinkedBlockingQueue<>();
            final BlockingQueue<ByteBuffer> valuePool = new LinkedBlockingQueue<>();


            keyPool.add(ByteBuffer.allocateDirect(lmdbEnv.getMaxKeySize()));
            valuePool.add(ByteBuffer.allocateDirect(VALUE_BUFFER_SIZE));

            lmdbEnv.doWithWriteTxn(writeTxn -> {
                for (int i = 0; i < REC_COUNT; i++) {
                    ByteBuffer keyBuffer = null;
                    ByteBuffer valueBuffer = null;
                    try {
                        keyBuffer = keyPool.take();
                        valueBuffer = valuePool.take();
                    } catch (final InterruptedException e) {
                        throw new RuntimeException(String.format("Interrupted"), e);
                    }

                    stringSerde.serialize(keyBuffer, "key" + i);
                    stringSerde.serialize(valueBuffer, "value" + i);

                    basicLmdbDb.put(writeTxn, keyBuffer, valueBuffer, false);
                    keyBuffer.clear();
                    valueBuffer.clear();

                    try {
                        keyPool.put(keyBuffer);
                        valuePool.put(valueBuffer);
                    } catch (final InterruptedException e) {
                        throw new RuntimeException(String.format("Interrupted"), e);
                    }
                }
            });

        }, "testPooled-put");

        LAMBDA_LOGGER.logDurationIfDebugEnabled(() -> {
            final BlockingQueue<ByteBuffer> keyPool = new LinkedBlockingQueue<>();

            keyPool.add(ByteBuffer.allocateDirect(lmdbEnv.getMaxKeySize()));

            for (int i = 0; i < REC_COUNT; i++) {
                final int j = i;

                lmdbEnv.doWithReadTxn(txn -> {
                    ByteBuffer keyBuffer = null;
                    try {
                        keyBuffer = keyPool.take();
                    } catch (final InterruptedException e) {
                        throw new RuntimeException(String.format("Interrupted"), e);
                    }
                    stringSerde.serialize(keyBuffer, "key" + j);
                    final Optional<ByteBuffer> optValue = basicLmdbDb.getAsBytes(txn, keyBuffer);
//                    assertThat(optValue).isPresent();
                    keyBuffer.clear();
                    try {
                        keyPool.put(keyBuffer);
                    } catch (final InterruptedException e) {
                        throw new RuntimeException(String.format("Interrupted"), e);
                    }
                });

            }

        }, "testPooled-get");

        LOGGER.debug("Finished");

    }

    @RepeatedTest(TEST_REPEAT_COUNT)
    void testPooledOneTxn() {

        LAMBDA_LOGGER.logDurationIfDebugEnabled(() -> {

            final BlockingQueue<ByteBuffer> keyPool = new LinkedBlockingQueue<>();
            final BlockingQueue<ByteBuffer> valuePool = new LinkedBlockingQueue<>();


            keyPool.add(ByteBuffer.allocateDirect(lmdbEnv.getMaxKeySize()));
            valuePool.add(ByteBuffer.allocateDirect(VALUE_BUFFER_SIZE));

            lmdbEnv.doWithWriteTxn(writeTxn -> {
                for (int i = 0; i < REC_COUNT; i++) {
                    ByteBuffer keyBuffer = null;
                    ByteBuffer valueBuffer = null;
                    try {
                        keyBuffer = keyPool.take();
                        valueBuffer = valuePool.take();
                    } catch (final InterruptedException e) {
                        throw new RuntimeException(String.format("Interrupted"), e);
                    }

                    stringSerde.serialize(keyBuffer, "key" + i);
                    stringSerde.serialize(valueBuffer, "value" + i);

                    basicLmdbDb.put(writeTxn, keyBuffer, valueBuffer, false);
                    keyBuffer.clear();
                    valueBuffer.clear();

                    try {
                        keyPool.put(keyBuffer);
                        valuePool.put(valueBuffer);
                    } catch (final InterruptedException e) {
                        throw new RuntimeException(String.format("Interrupted"), e);
                    }
                }
            });

        }, "testPooledOneTxn-put");

        LAMBDA_LOGGER.logDurationIfDebugEnabled(() -> {
            final BlockingQueue<ByteBuffer> keyPool = new LinkedBlockingQueue<>();

            keyPool.add(ByteBuffer.allocateDirect(lmdbEnv.getMaxKeySize()));

            lmdbEnv.doWithReadTxn(txn -> {
                for (int i = 0; i < REC_COUNT; i++) {
                    final int j = i;

                    ByteBuffer keyBuffer = null;
                    try {
                        keyBuffer = keyPool.take();
                    } catch (final InterruptedException e) {
                        throw new RuntimeException(String.format("Interrupted"), e);
                    }
                    stringSerde.serialize(keyBuffer, "key" + j);
                    final Optional<ByteBuffer> optValue = basicLmdbDb.getAsBytes(txn, keyBuffer);
//                    assertThat(optValue).isPresent();
                    keyBuffer.clear();
                    try {
                        keyPool.put(keyBuffer);
                    } catch (final InterruptedException e) {
                        throw new RuntimeException(String.format("Interrupted"), e);
                    }

                }
            });

        }, "testPooledOneTxn-get");

        LOGGER.debug("Finished");

    }

    @RepeatedTest(TEST_REPEAT_COUNT)
    void testHashMap() {

        final Map<String, String> map = new HashMap<>();
        LAMBDA_LOGGER.logDurationIfDebugEnabled(() -> {

            for (int i = 0; i < REC_COUNT; i++) {
                map.put("key" + i, "value" + i);
            }

        }, "HashMap-put");

        LAMBDA_LOGGER.logDurationIfDebugEnabled(() -> {
            for (int i = 0; i < REC_COUNT; i++) {
                final String val = map.get("key" + i);
            }

        }, "HashMap-get");

    }
}
