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

package stroom.refdata.lmdb;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import stroom.refdata.offheapstore.ByteBufferPool;
import stroom.refdata.offheapstore.PooledByteBuffer;
import stroom.refdata.offheapstore.databases.AbstractLmdbDbTest;
import stroom.refdata.offheapstore.serdes.StringSerde;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class TestBasicLmdbDb extends AbstractLmdbDbTest {

    private BasicLmdbDb<String, String> basicLmdbDb;

    @Before
    @Override
    public void setup() {
        super.setup();

        basicLmdbDb = new BasicLmdbDb<>(
                lmdbEnv,
                new ByteBufferPool(),
                new StringSerde(),
                new StringSerde(),
                "MyBasicLmdb");
    }

    @Test
    public void testBufferMutationAfterPut() {

        ByteBuffer keyBuffer = ByteBuffer.allocateDirect(50);
        ByteBuffer valueBuffer = ByteBuffer.allocateDirect(50);

        basicLmdbDb.getKeySerde().serialize(keyBuffer, "MyKey");
        basicLmdbDb.getValueSerde().serialize(valueBuffer, "MyValue");

        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            basicLmdbDb.put(writeTxn, keyBuffer, valueBuffer, false);
        });
        assertThat(basicLmdbDb.getEntryCount()).isEqualTo(1);

        // it is ok to mutate the buffers used in the put outside of the txn
        basicLmdbDb.getKeySerde().serialize(keyBuffer, "XX");
        basicLmdbDb.getValueSerde().serialize(valueBuffer, "YY");

        // now get the value again and it should be correct
        String val = basicLmdbDb.get("MyKey").get();

        assertThat(val).isEqualTo("MyValue");
    }

    /**
     * This test is an example of how to abuse LMDB. If run it will crash the JVM
     * as a direct bytebuffer returned from a put() was mutated outside the transaction.
     */
    @Ignore // see javadoc above
    @Test
    public void testBufferMutationAfterGet() {

        basicLmdbDb.put("MyKey", "MyValue", false);

        // the buffers have been returned to the pool and cleared
        assertThat(basicLmdbDb.getByteBufferPool().getCurrentPoolSize()).isEqualTo(2);

        assertThat(basicLmdbDb.getEntryCount()).isEqualTo(1);

        ByteBuffer keyBuffer = ByteBuffer.allocateDirect(50);
        basicLmdbDb.getKeySerde().serialize(keyBuffer, "MyKey");

        AtomicReference<ByteBuffer> valueBufRef = new AtomicReference<>();
        // now get the value again and it should be correct
        LmdbUtils.getWithReadTxn(lmdbEnv, txn -> {
            ByteBuffer valueBuffer = basicLmdbDb.getAsBytes(txn, keyBuffer).get();

            // hold on to the buffer for later
            valueBufRef.set(valueBuffer);

            String val = basicLmdbDb.getValueSerde().deserialize(valueBuffer);

            assertThat(val).isEqualTo("MyValue");

            return val;
        });

        // now mutate the value buffer outside any txn

        assertThat(valueBufRef.get().position()).isEqualTo(0);


        // This line will crash the JVM as we are mutating a directly allocated ByteBuffer
        // (that points to memory managed by LMDB) outside of a txn.
        basicLmdbDb.getValueSerde().serialize(valueBufRef.get(), "XXX");

    }

    @Test
    public void testGetAsBytes() {

        basicLmdbDb.put("key1", "value1", false);
        basicLmdbDb.put("key2", "value2", false);
        basicLmdbDb.put("key3", "value3", false);

        LmdbUtils.doWithReadTxn(lmdbEnv, txn -> {
            Optional<ByteBuffer> optKeyBuffer = basicLmdbDb.getAsBytes(txn, "key2");

            assertThat(optKeyBuffer).isNotEmpty();
        });
    }

    @Test
    public void testGetAsBytes2() {

        basicLmdbDb.put("key1", "value1", false);
        basicLmdbDb.put("key2", "value2", false);
        basicLmdbDb.put("key3", "value3", false);

        LmdbUtils.doWithReadTxn(lmdbEnv, txn -> {

            try (PooledByteBuffer pooledKeyBuffer = basicLmdbDb.getPooledKeyBuffer()) {
                ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
                basicLmdbDb.serializeKey(keyBuffer, "key2");
                Optional<ByteBuffer> optValueBuffer = basicLmdbDb.getAsBytes(txn, keyBuffer);

                assertThat(optValueBuffer).isNotEmpty();
                String val = basicLmdbDb.deserializeKey(optValueBuffer.get());
                assertThat(val).isEqualTo("value2");
            }
        });
    }

    @Test
    public void testStreamAllEntries() {
        IntStream.rangeClosed(1, 20).forEach(i -> {
            basicLmdbDb.put("key" + i, "value" + i, false);

        });

        List<String> entries = LmdbUtils.getWithReadTxn(lmdbEnv, txn ->
                basicLmdbDb.streamAllEntries(txn, stream ->
                        stream.map(kvTuple ->
                                kvTuple._1() + "-" + kvTuple._2())
                                .collect(Collectors.toList())));

        assertThat(entries).hasSize(20);
    }

    @Test
    public void testStreamAllEntriesWithFilter() {
        IntStream.rangeClosed(1, 20).forEach(i -> {
            basicLmdbDb.put( Integer.toString(i), "value" + i, false);

        });

        List<String> entries = LmdbUtils.getWithReadTxn(lmdbEnv, txn ->
                basicLmdbDb.streamAllEntries(txn, stream ->
                        stream.filter(kvTuple -> {
                            int i = Integer.parseInt(kvTuple._1());
                            return i > 10 && i <= 15;
                        })
                        .map(kvTuple ->
                                kvTuple._1() + "-" + kvTuple._2())
                                .collect(Collectors.toList())));

        assertThat(entries).hasSize(5);
    }
}