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

package stroom.refdata.store.offheapstore.lmdb;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.lmdbjava.KeyRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.util.ByteBufferPool;
import stroom.refdata.util.ByteBufferUtils;
import stroom.refdata.util.PooledByteBuffer;
import stroom.refdata.store.offheapstore.databases.AbstractLmdbDbTest;
import stroom.refdata.store.offheapstore.serdes.StringSerde;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class TestBasicLmdbDb extends AbstractLmdbDbTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestBasicLmdbDb.class);

    private BasicLmdbDb<String, String> basicLmdbDb;
    private BasicLmdbDb<String, String> basicLmdbDb2;

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

        basicLmdbDb2 = new BasicLmdbDb<>(
                lmdbEnv,
                new ByteBufferPool(),
                new StringSerde(),
                new StringSerde(),
                "MyBasicLmdb2");
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
    public void testStreamEntries() {
        populateDb();

        List<String> entries = LmdbUtils.getWithReadTxn(lmdbEnv, txn ->
                basicLmdbDb.streamEntries(txn, KeyRange.all(), stream ->
                        stream
                                .map(kvTuple ->
                                        kvTuple._1() + "-" + kvTuple._2())
                                .peek(LOGGER::info)
                                .collect(Collectors.toList())));

        assertThat(entries).hasSize(20);
    }

    @Test
    public void testStreamEntriesWithFilter() {
        populateDb();

        List<String> entries = LmdbUtils.getWithReadTxn(lmdbEnv, txn ->
                basicLmdbDb.streamEntries(txn, KeyRange.all(), stream ->
                        stream
                                .filter(kvTuple -> {
                                    int i = Integer.parseInt(kvTuple._1());
                                    return i > 10 && i <= 15;
                                })
                                .map(kvTuple ->
                                        kvTuple._1() + "-" + kvTuple._2())
                                .peek(LOGGER::info)
                                .collect(Collectors.toList())));

        assertThat(entries).hasSize(5);
    }


    @Test
    public void testStreamEntriesWithKeyRange() {
        populateDb();

        KeyRange<String> keyRange = KeyRange.closed("06", "10");
        List<String> entries = LmdbUtils.getWithReadTxn(lmdbEnv, txn ->
                basicLmdbDb.streamEntries(txn, keyRange, stream ->
                        stream
                                .map(kvTuple ->
                                        kvTuple._1() + "-" + kvTuple._2())
                                .peek(LOGGER::info)
                                .collect(Collectors.toList())));

        assertThat(entries).hasSize(5);
    }

    @Test
    public void testKeyReuse() {
        // key 1 => key2 => value2 & value 3
        basicLmdbDb.put("key1", "key2", false);
        basicLmdbDb.put("key2", "value2", false);

        // different DB with same key in it so we can test two lookups using same key
        basicLmdbDb2.put("key2", "value3", false);

        LmdbUtils.doWithReadTxn(lmdbEnv, txn -> {
            ByteBuffer keyBuffer = ByteBuffer.allocateDirect(100);
            basicLmdbDb.serializeKey(keyBuffer, "key1");
            ByteBuffer keyBufferCopy = keyBuffer.asReadOnlyBuffer();

            ByteBuffer valueBuffer = basicLmdbDb.getAsBytes(txn, keyBuffer).get();
            ByteBuffer keyBuffer2 = ByteBufferUtils.copyToDirectBuffer(valueBuffer);
            String value = basicLmdbDb.deserializeValue(valueBuffer);
            ByteBuffer valueBufferCopy = valueBuffer.asReadOnlyBuffer();

            assertThat(value).isEqualTo("key2");
            assertThat(keyBuffer).isEqualTo(keyBufferCopy);
            assertThat(valueBuffer).isEqualTo(valueBufferCopy);
            assertThat(valueBuffer).isEqualTo(keyBuffer2);

            // now use the value from the last get() as the key for a new get()
            ByteBuffer valueBuffer2 = basicLmdbDb.getAsBytes(txn, valueBuffer).get();

            String value2 = basicLmdbDb.deserializeValue(valueBuffer2);
            String valueBufferDeserialised = basicLmdbDb.deserializeKey(valueBuffer);

            assertThat(value2).isEqualTo("value2");

            // The second get() has overwritten our original valueBuffer with the value
            // of the second get(). This is because the txn essentially holds a cursor
            // whose position is updated by the get and that cursor is bound to the value
            // buffer returned by the get. The value from the get() can be used as a key
            // in another get() once, but that second get() will mutate it prevent it from
            // being used as a key in another get().
            assertThat(valueBufferDeserialised).isEqualTo("value2");
            assertThat(valueBuffer).isEqualTo(valueBuffer2);
            assertThat(valueBuffer).isNotEqualTo(valueBufferCopy);
            assertThat(keyBuffer2).isNotEqualTo(valueBuffer);

            // We can't use valueBuffer for our key here is it now points to "value2"
            ByteBuffer valueBuffer3 = basicLmdbDb2.getAsBytes(txn, keyBuffer2).get();

            String value3 = basicLmdbDb2.deserializeValue(valueBuffer3);

            assertThat(value3).isEqualTo("value3");
            assertThat(keyBuffer).isEqualTo(keyBufferCopy);
        });
    }

    @Test
    public void testValueReuse() {
        basicLmdbDb.put("key1", "value1", false);
        basicLmdbDb.put("key2", "value2", false);

        LmdbUtils.doWithReadTxn(lmdbEnv, txn -> {

            ByteBuffer keyBuffer1 = ByteBuffer.allocateDirect(100);
            basicLmdbDb.serializeKey(keyBuffer1, "key1");

            ByteBuffer valueBuffer1 = basicLmdbDb.getAsBytes(txn, keyBuffer1).get();
            ByteBuffer valueBuffer1Copy = valueBuffer1.asReadOnlyBuffer();
            assertThat(valueBuffer1Copy).isEqualTo(valueBuffer1);
            String value1 = basicLmdbDb.deserializeValue(valueBuffer1);

            assertThat(value1).isEqualTo("value1");

            // now do another get on a different key to get a different value
            ByteBuffer keyBuffer2 = ByteBuffer.allocateDirect(100);
            basicLmdbDb.serializeKey(keyBuffer2, "key2");
            ByteBuffer valueBuffer2 = basicLmdbDb.getAsBytes(txn, keyBuffer2).get();

            String value2 = basicLmdbDb.deserializeValue(valueBuffer2);
            assertThat(value2).isEqualTo("value2");

            // The valueBuffer1 is tied to the txn's cursor which has now moved to key2 => value2,
            // thus valueBuffer1 now contains "value2".
            value1 = basicLmdbDb.deserializeValue(valueBuffer1);
            assertThat(value1).isEqualTo("value2");
            assertThat(valueBuffer1Copy).isNotEqualTo(valueBuffer1);
        });
    }

    private void populateDb() {
        // pad the keys to a fixed length so they sort in number order
        IntStream.rangeClosed(1, 20).forEach(i -> {
            basicLmdbDb.put(buildKey(i), buildValue(i), false);

        });

        basicLmdbDb.logDatabaseContents(LOGGER::info);
    }

    private String buildKey(int i) {
        return String.format("%02d",i);
    }

    private String buildValue(int i) {
        return "value" + i;
    }

}