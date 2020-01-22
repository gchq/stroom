/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.pipeline.refdata.store.offheapstore.databases;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.pipeline.refdata.store.offheapstore.lmdb.BasicLmdbDb;
import stroom.pipeline.refdata.store.offheapstore.lmdb.LmdbUtils;
import stroom.pipeline.refdata.store.offheapstore.lmdb.serde.Serde;
import stroom.pipeline.refdata.store.offheapstore.serdes.StringSerde;
import stroom.pipeline.refdata.util.ByteBufferPool;
import stroom.util.io.ByteSizeUnit;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.IOException;
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
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(TestByteBufferReusePerformance.class);

    private static final long DB_MAX_SIZE = ByteSizeUnit.MEBIBYTE.longBytes(100);
    private static final int VALUE_BUFFER_SIZE = 1_000;
    private final int recCount = 1_000_000;
    private Serde<String> stringSerde = new StringSerde();
    private BasicLmdbDb<String, String> basicLmdbDb;

    @BeforeEach
    @Override
    public void setup() throws IOException {
        super.setup();

        basicLmdbDb = new BasicLmdbDb<>(lmdbEnv, new ByteBufferPool(), stringSerde, stringSerde, "basicDb");

        //just to ensure the DB is created and ready to use
        basicLmdbDb.get("xxx");
    }

    @Override
    protected long getMaxSizeBytes() {
        return DB_MAX_SIZE;
    }


    @Test
    void testNoReuse() {

        LAMBDA_LOGGER.logDurationIfDebugEnabled(() -> {

            LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
                for (int i = 0; i < recCount; i++) {
                    final ByteBuffer keyBuffer = stringSerde.serialize("key" + i);
                    final ByteBuffer valueBuffer = stringSerde.serialize("value" + i);

                    basicLmdbDb.put(writeTxn, keyBuffer, valueBuffer, false);
                }
            });

        }, "testNoReuse-put");

        LAMBDA_LOGGER.logDurationIfDebugEnabled(() -> {

            for (int i = 0; i < recCount; i++) {
                final ByteBuffer keyBuffer = stringSerde.serialize("key" + i);

                LmdbUtils.doWithReadTxn(lmdbEnv, txn -> {
                    Optional<ByteBuffer> optValue = basicLmdbDb.getAsBytes(txn, keyBuffer);
//                        assertThat(optValue).isPresent();
                });
            }

        }, "testNoReuse-get");

        LOGGER.debug("Finished");
    }

    @Test
    void testReuse() {

        LAMBDA_LOGGER.logDurationIfDebugEnabled(() -> {

            final ByteBuffer keyBuffer = ByteBuffer.allocateDirect(lmdbEnv.getMaxKeySize());
            final ByteBuffer valueBuffer = ByteBuffer.allocateDirect(VALUE_BUFFER_SIZE);

            LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
                for (int i = 0; i < recCount; i++) {
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
            for (int i = 0; i < recCount; i++) {
                stringSerde.serialize(keyBuffer, "key" + i);

                final int j = i;

                LmdbUtils.doWithReadTxn(lmdbEnv, txn -> {
                    Optional<ByteBuffer> optValue = basicLmdbDb.getAsBytes(txn, keyBuffer);
//                    assertThat(optValue).isPresent();
//                    assertThat(optValue.map(bb -> stringSerde.deserialize(bb)).get()).isEqualTo("value" + j);
                    keyBuffer.clear();
                });
            }

        }, "testReuse-get");

        LOGGER.debug("Finished");

    }

    @Test
    void testPooled() {

        LAMBDA_LOGGER.logDurationIfDebugEnabled(() -> {

            BlockingQueue<ByteBuffer> keyPool = new LinkedBlockingQueue<>();
            BlockingQueue<ByteBuffer> valuePool = new LinkedBlockingQueue<>();


            keyPool.add(ByteBuffer.allocateDirect(lmdbEnv.getMaxKeySize()));
            valuePool.add(ByteBuffer.allocateDirect(VALUE_BUFFER_SIZE));

            LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
                for (int i = 0; i < recCount; i++) {
                    ByteBuffer keyBuffer = null;
                    ByteBuffer valueBuffer = null;
                    try {
                        keyBuffer = keyPool.take();
                        valueBuffer = valuePool.take();
                    } catch (InterruptedException e) {
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
                    } catch (InterruptedException e) {
                        throw new RuntimeException(String.format("Interrupted"), e);
                    }
                }
            });

        }, "testPooled-put");

        LAMBDA_LOGGER.logDurationIfDebugEnabled(() -> {
            BlockingQueue<ByteBuffer> keyPool = new LinkedBlockingQueue<>();

            keyPool.add(ByteBuffer.allocateDirect(lmdbEnv.getMaxKeySize()));

            for (int i = 0; i < recCount; i++) {
                final int j = i;

                LmdbUtils.doWithReadTxn(lmdbEnv, txn -> {
                    ByteBuffer keyBuffer = null;
                    try {
                        keyBuffer = keyPool.take();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(String.format("Interrupted"), e);
                    }
                    stringSerde.serialize(keyBuffer, "key" + j);
                    Optional<ByteBuffer> optValue = basicLmdbDb.getAsBytes(txn, keyBuffer);
//                    assertThat(optValue).isPresent();
                    keyBuffer.clear();
                    try {
                        keyPool.put(keyBuffer);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(String.format("Interrupted"), e);
                    }
                });

            }

        }, "testPooled-get");

        LOGGER.debug("Finished");

    }

    @Test
    void testPooledOneTxn() {

        LAMBDA_LOGGER.logDurationIfDebugEnabled(() -> {

            BlockingQueue<ByteBuffer> keyPool = new LinkedBlockingQueue<>();
            BlockingQueue<ByteBuffer> valuePool = new LinkedBlockingQueue<>();


            keyPool.add(ByteBuffer.allocateDirect(lmdbEnv.getMaxKeySize()));
            valuePool.add(ByteBuffer.allocateDirect(VALUE_BUFFER_SIZE));

            LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
                for (int i = 0; i < recCount; i++) {
                    ByteBuffer keyBuffer = null;
                    ByteBuffer valueBuffer = null;
                    try {
                        keyBuffer = keyPool.take();
                        valueBuffer = valuePool.take();
                    } catch (InterruptedException e) {
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
                    } catch (InterruptedException e) {
                        throw new RuntimeException(String.format("Interrupted"), e);
                    }
                }
            });

        }, "testPooledOneTxn-put");

        LAMBDA_LOGGER.logDurationIfDebugEnabled(() -> {
            BlockingQueue<ByteBuffer> keyPool = new LinkedBlockingQueue<>();

            keyPool.add(ByteBuffer.allocateDirect(lmdbEnv.getMaxKeySize()));

            LmdbUtils.doWithReadTxn(lmdbEnv, txn -> {
                for (int i = 0; i < recCount; i++) {
                    final int j = i;

                    ByteBuffer keyBuffer = null;
                    try {
                        keyBuffer = keyPool.take();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(String.format("Interrupted"), e);
                    }
                    stringSerde.serialize(keyBuffer, "key" + j);
                    Optional<ByteBuffer> optValue = basicLmdbDb.getAsBytes(txn, keyBuffer);
//                    assertThat(optValue).isPresent();
                    keyBuffer.clear();
                    try {
                        keyPool.put(keyBuffer);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(String.format("Interrupted"), e);
                    }

                }
            });

        }, "testPooledOneTxn-get");

        LOGGER.debug("Finished");

    }

    @Test
    void testHashMap() {

        Map<String, String> map = new HashMap<>();
        LAMBDA_LOGGER.logDurationIfDebugEnabled(() -> {

            for (int i = 0; i < recCount; i++) {
                map.put("key" + i, "value" + i);
            }

        }, "HashMap-put");

        LAMBDA_LOGGER.logDurationIfDebugEnabled(() -> {
            for (int i = 0; i < recCount; i++) {
                String val = map.get("key" + i);
            }

        }, "HashMap-get");

    }
}
