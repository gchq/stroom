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

package stroom.pipeline.refdata.store.offheapstore.databases;

import stroom.bytebuffer.PooledByteBuffer;
import stroom.lmdb.PutOutcome;
import stroom.pipeline.refdata.store.offheapstore.UID;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * This is a common interface for the different DBs containing the reference data entries
 */
public interface EntryStoreDb<K> {

    void serializeKey(final ByteBuffer keyBuffer, K key);

    /**
     * @param writeTxn
     * @param keyBuffer
     * @param valueBuffer
     * @param overwriteExisting
     * @param isAppending Only set to true if you are sure that the key will be at the end of the db
     * @return
     */
    PutOutcome put(final Txn<ByteBuffer> writeTxn,
                   final ByteBuffer keyBuffer,
                   final ByteBuffer valueBuffer,
                   final boolean overwriteExisting,
                   final boolean isAppending);

    boolean delete(final Txn<ByteBuffer> writeTxn, final ByteBuffer keyBuffer);

    Optional<ByteBuffer> getAsBytes(Txn<ByteBuffer> txn, final ByteBuffer keyBuffer);

    Optional<UID> getMaxUid(final Txn<ByteBuffer> txn, PooledByteBuffer pooledByteBuffer);
}
