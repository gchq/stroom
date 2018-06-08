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

package stroom.refdata.offheapstore.databases;

import com.google.inject.assistedinject.Assisted;
import org.lmdbjava.Env;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.lmdb.AbstractLmdbDb;
import stroom.refdata.offheapstore.ValueStoreKey;
import stroom.refdata.offheapstore.serdes.IntegerSerde;
import stroom.refdata.offheapstore.serdes.ValueStoreKeySerde;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import java.nio.ByteBuffer;

public class ValueReferenceCountDb extends AbstractLmdbDb<ValueStoreKey, Integer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValueReferenceCountDb.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(ValueReferenceCountDb.class);

    private static final String DB_NAME = "ValueReferenceCountStore";

    private final ValueStoreKeySerde keySerde;
    private final IntegerSerde valueSerde;

    @Inject
    public ValueReferenceCountDb(
            @Assisted final Env<ByteBuffer> lmdbEnvironment,
            final ValueStoreKeySerde keySerde,
            final IntegerSerde valueSerde) {

        super(lmdbEnvironment, keySerde, valueSerde, DB_NAME);
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
    }

    /**
     * increments the reference count by one for the key represented by the valueStoreKeyBuf.
     */
    public void incrementReferenceCount(final Txn<ByteBuffer> writeTxn, final ByteBuffer keyBuffer) {

        updateValue(writeTxn, keyBuffer, valueSerde::increment);
    }

    /**
     * Decrements the reference count by one for the key represented by the valueStoreKeyBuf.
     */
    public void decrementReferenceCount(final Txn<ByteBuffer> writeTxn, final ByteBuffer keyBuffer) {

        updateValue(writeTxn, keyBuffer, valueSerde::decrement);
    }

    public interface Factory {
        ValueReferenceCountDb create(final Env<ByteBuffer> lmdbEnvironment);
    }
}
