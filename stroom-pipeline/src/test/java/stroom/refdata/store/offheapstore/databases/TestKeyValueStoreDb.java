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

package stroom.refdata.store.offheapstore.databases;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.store.offheapstore.lmdb.LmdbUtils;
import stroom.refdata.util.ByteBufferPool;
import stroom.refdata.util.ByteBufferUtils;
import stroom.refdata.store.offheapstore.KeyValueStoreKey;
import stroom.refdata.store.offheapstore.UID;
import stroom.refdata.store.offheapstore.ValueStoreKey;
import stroom.refdata.store.offheapstore.serdes.KeyValueStoreKeySerde;
import stroom.refdata.store.offheapstore.serdes.ValueStoreKeySerde;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class TestKeyValueStoreDb extends AbstractLmdbDbTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestKeyValueStoreDb.class);

    private KeyValueStoreDb keyValueStoreDb = null;

    @Before
    @Override
    public void setup() {
        super.setup();

        keyValueStoreDb = new KeyValueStoreDb(
                lmdbEnv,
                new ByteBufferPool(),
                new KeyValueStoreKeySerde(),
                new ValueStoreKeySerde());
    }

    @Test
    public void forEachEntry() {

        final UID uid1 = UID.of(1,0,0,1);
        final UID uid2 = UID.of(2,0,0,2);
        final UID uid3 = UID.of(3,0,0,3);

        final KeyValueStoreKey keyValueStoreKey11 = new KeyValueStoreKey(uid1, "key11");
        final KeyValueStoreKey keyValueStoreKey21 = new KeyValueStoreKey(uid2, "key21");
        final KeyValueStoreKey keyValueStoreKey22 = new KeyValueStoreKey(uid2, "key22");
        final KeyValueStoreKey keyValueStoreKey31 = new KeyValueStoreKey(uid3, "key31");

        final ValueStoreKey valueStoreKey11 = new ValueStoreKey(11, (short) 11);
        final ValueStoreKey valueStoreKey21 = new ValueStoreKey(21, (short) 21);
        final ValueStoreKey valueStoreKey22 = new ValueStoreKey(22, (short) 22);
        final ValueStoreKey valueStoreKey31 = new ValueStoreKey(31, (short) 31);

        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            keyValueStoreDb.put(writeTxn, keyValueStoreKey11, valueStoreKey11, false);
            keyValueStoreDb.put(writeTxn, keyValueStoreKey21, valueStoreKey21, false);
            keyValueStoreDb.put(writeTxn, keyValueStoreKey22, valueStoreKey22, false);
            keyValueStoreDb.put(writeTxn, keyValueStoreKey31, valueStoreKey31, false);

            assertThat(keyValueStoreDb.getEntryCount(writeTxn)).isEqualTo(4);
        });

        doForEachTest(uid1, 1);
        doForEachTest(uid2, 2);
        doForEachTest(uid3, 1);
    }

    private void doForEachTest(final UID uid, final int expectedEntryCount) {
        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            AtomicInteger cnt = new AtomicInteger(0);
            keyValueStoreDb.deleteMapEntries(writeTxn, uid, (keyBuf, valBuf) -> {
                cnt.incrementAndGet();
                LOGGER.info("{} {}", ByteBufferUtils.byteBufferInfo(keyBuf), ByteBufferUtils.byteBufferInfo(valBuf));
            });

            assertThat(cnt).hasValue(expectedEntryCount);
        });
    }
}