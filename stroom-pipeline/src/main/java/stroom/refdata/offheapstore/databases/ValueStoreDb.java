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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.lmdb.AbstractLmdbDb;
import stroom.refdata.offheapstore.ByteArrayUtils;
import stroom.refdata.offheapstore.RefDataValue;
import stroom.refdata.offheapstore.serdes.RefDataValueSerde;
import stroom.refdata.offheapstore.ValueStoreKey;
import stroom.refdata.offheapstore.serdes.ValueStoreKeySerde;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import java.nio.ByteBuffer;

/**
 * A database to hold reference data values using a generated unique surrogate key. The key
 * consists of the hashcode of the {@link RefDataValue} suffixed with a unique identifier. The
 * unique identifier is just an integer
 * key (hash|uid) | value
 * ----------------------
 * (123|0)        | 363838
 * (123|1)        | 857489
 * (456|0)        | 263673
 * (789|0)        | 689390
 */
public class ValueStoreDb extends AbstractLmdbDb<ValueStoreKey, RefDataValue> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValueStoreDb.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(ValueStoreDb.class);

    private static final String DB_NAME = "ValueStore";

    @Inject
    public ValueStoreDb(@Assisted final Env<ByteBuffer> lmdbEnvironment,
                        final ValueStoreKeySerde keySerde,
                        final RefDataValueSerde valueSerde) {
        super(lmdbEnvironment, keySerde, valueSerde, DB_NAME);
    }

    /**
     * Either gets the {@link ValueStoreKey} corresponding to the passed refDataValue
     * from the database or creates the entry in the database and returns the generated
     * key.
     * @return A clone of the {@link ByteBuffer} containing the database key.
     */
    ByteBuffer getOrCreate(final RefDataValue refDataValue) {

        LOGGER.debug("getOrCreate called for refDataValue: {}", refDataValue);

        final ByteBuffer valueBuffer = valueSerde.serialize(refDataValue);

        LAMBDA_LOGGER.debug(() ->
                LambdaLogger.buildMessage("valueBuffer: {}", ByteArrayUtils.byteBufferInfo(valueBuffer)));






        return null;
    }

    public interface Factory {
        ValueStoreDb create(final Env<ByteBuffer> lmdbEnvironment);
    }
}
