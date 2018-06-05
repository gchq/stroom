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

import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.PutFlags;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.lmdb.serde.Serde;
import stroom.refdata.offheapstore.ByteArrayUtils;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * An abstract class representing a generic LMDB table with understanding of how to (de)serialise
 * keys/values into/out of the database. Provides various helper methods for interacting with the
 * database at a higher abstraction that the raw bytes.
 * @param <K> The class of the database keys
 * @param <V> The class of the database values
 */
public abstract class AbstractLmdbDb<K, V> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLmdbDb.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(AbstractLmdbDb.class);

    protected final Serde<K> keySerde;
    protected final Serde<V> valueSerde;
    protected final String dbName;
    protected final Dbi<ByteBuffer> lmdbDbi;
    protected final Env<ByteBuffer> lmdbEnvironment;

    public AbstractLmdbDb(final Env<ByteBuffer> lmdbEnvironment,
                          final Serde<K> keySerde,
                          final Serde<V> valueSerde,
                          final String dbName) {
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
        this.dbName = dbName;
        this.lmdbEnvironment = lmdbEnvironment;
        this.lmdbDbi = openDbi(lmdbEnvironment, dbName);
    }

    public String getDbName() {
        return dbName;
    }

    public Dbi<ByteBuffer> getLmdbDbi() {
        return lmdbDbi;
    }

    protected Env<ByteBuffer> getLmdbEnvironment() {
        return lmdbEnvironment;
    }

    public Serde<K> getKeySerde() {
        return keySerde;
    }

    public Serde<V> getValueSerde() {
        return valueSerde;
    }

    public Optional<V> get(Txn<ByteBuffer> txn, final K key) {
        try {
            ByteBuffer keyBuffer = keySerde.serialize(key);
            ByteBuffer valueBuffer = lmdbDbi.get(txn, keyBuffer);
            LAMBDA_LOGGER.debug(() -> LambdaLogger.buildMessage("Get returned value [{}] for key [{}]",
                    ByteArrayUtils.byteBufferInfo(valueBuffer),
                    ByteArrayUtils.byteBufferInfo(keyBuffer)));

            return Optional.ofNullable(valueBuffer)
                    .map(valueSerde::deserialize);
        } catch (RuntimeException e) {
            throw new RuntimeException(LambdaLogger.buildMessage("Error getting key {}", key), e);
        }
    }

    public Optional<V> get(final K key) {
        return LmdbUtils.getWithReadTxn(lmdbEnvironment, txn ->
                get(txn, key));
    }

    /**
     * Get the bytes of the value for the given key buffer. The returned {@link ByteBuffer} should ONLY
     * by used while still inside the passed {@link Txn}
     */
    public Optional<ByteBuffer> getAsBytes(Txn<ByteBuffer> txn, final ByteBuffer keyBuffer) {
        try {
            ByteBuffer valueBuffer = lmdbDbi.get(txn, keyBuffer);
            LAMBDA_LOGGER.debug(() -> LambdaLogger.buildMessage("Get returned value [{}] for key [{}]",
                    ByteArrayUtils.byteBufferInfo(valueBuffer),
                    ByteArrayUtils.byteBufferInfo(keyBuffer)));

            return Optional.ofNullable(valueBuffer);
        } catch (RuntimeException e) {
            throw new RuntimeException(LambdaLogger.buildMessage("Error getting value for key [{}]",
                    ByteArrayUtils.byteBufferInfo(keyBuffer)), e);
        }
    }

    public <T> T mapValue(final K key, final Function<V, T> valueMapper) {
        try (final Txn<ByteBuffer> txn = lmdbEnvironment.txnRead()) {
            ByteBuffer keyBuffer = keySerde.serialize(key);
            ByteBuffer valueBuffer = lmdbDbi.get(txn, keyBuffer);
            V value = valueSerde.deserialize(valueBuffer);
            return valueMapper.apply(value);
        } catch (RuntimeException e) {
            throw new RuntimeException(LambdaLogger.buildMessage("Error getting key {}", key), e);
        }
    }

    public void consumeValue(final K key, final Consumer<V> valueConsumer) {
        try (final Txn<ByteBuffer> txn = lmdbEnvironment.txnRead()) {
            ByteBuffer keyBuffer = keySerde.serialize(key);
            ByteBuffer valueBuffer = lmdbDbi.get(txn, keyBuffer);
            V value = valueSerde.deserialize(valueBuffer);
            valueConsumer.accept(value);
        } catch (RuntimeException e) {
            throw new RuntimeException(LambdaLogger.buildMessage("Error getting key {}", key), e);
        }
    }

    public boolean put(final Txn<ByteBuffer> writeTxn, final K key, final V value, final boolean overwriteExisting) {
        try {
            ByteBuffer keyBuffer = keySerde.serialize(key);
            ByteBuffer valueBuffer = valueSerde.serialize(value);
            return put(writeTxn, keyBuffer, valueBuffer, overwriteExisting);
        } catch (RuntimeException e) {
            throw new RuntimeException(LambdaLogger.buildMessage("Error putting key {}, value {}", key, value), e);
        }
    }

    public boolean put(final K key, final V value, final boolean overwriteExisting) {
        try (final Txn<ByteBuffer> writeTxn = lmdbEnvironment.txnWrite()) {
            boolean didPutSucceed = put(writeTxn, key, value, overwriteExisting);
            writeTxn.commit();
            return didPutSucceed;
        } catch (RuntimeException e) {
            throw new RuntimeException(LambdaLogger.buildMessage("Error putting key {}, value {}", key, value), e);
        }
    }

    public boolean put(final Txn<ByteBuffer> writeTxn,
                       final ByteBuffer keyBuffer,
                       final ByteBuffer valueBuffer,
                       final boolean overwriteExisting) {
        try {
            boolean didPutSucceed;
            if (overwriteExisting) {
                didPutSucceed = lmdbDbi.put(writeTxn, keyBuffer, valueBuffer);
            } else {
                didPutSucceed = lmdbDbi.put(writeTxn, keyBuffer, valueBuffer, PutFlags.MDB_NOOVERWRITE);
            }
            LAMBDA_LOGGER.debug(() -> LambdaLogger.buildMessage("Put returned {} for key [{}], value [{}]",
                    didPutSucceed,
                    ByteArrayUtils.byteBufferInfo(keyBuffer),
                    ByteArrayUtils.byteBufferInfo(valueBuffer)));

            return didPutSucceed;
        } catch (RuntimeException e) {
            throw new RuntimeException(LambdaLogger.buildMessage("Error putting key {}, value {}",
                    ByteArrayUtils.byteBufferInfo(keyBuffer), ByteArrayUtils.byteBufferInfo(valueBuffer)), e);
        }
    }

    public void putAll(final Map<K, V> entries) {
        try (final Txn<ByteBuffer> txn = lmdbEnvironment.txnWrite()) {
            entries.forEach((key, value) -> {
                try {
                    ByteBuffer keyBuffer = keySerde.serialize(key);
                    ByteBuffer valueBuffer = valueSerde.serialize(value);
                    lmdbDbi.put(txn, keyBuffer, valueBuffer);
                } catch (Exception e) {
                    throw new RuntimeException(LambdaLogger.buildMessage("Error putting key {}, value {}", key, value), e);
                }
            });
            txn.commit();
        } catch (RuntimeException e) {
            throw new RuntimeException(LambdaLogger.buildMessage("Error putting {} entries", entries.size()), e);
        }
    }

    public void delete(final K key) {
        try (final Txn<ByteBuffer> txn = lmdbEnvironment.txnWrite()) {
            ByteBuffer keyBuffer = keySerde.serialize(key);
            lmdbDbi.delete(txn, keyBuffer);
            txn.commit();
        } catch (RuntimeException e) {
            throw new RuntimeException(LambdaLogger.buildMessage("Error deleting key {}", key), e);
        }
    }

    public void deleteAll(final Collection<K> keys) {
        try (final Txn<ByteBuffer> txn = lmdbEnvironment.txnWrite()) {
            keys.forEach(key -> {
                try {
                    ByteBuffer keyBuffer = keySerde.serialize(key);
                    lmdbDbi.delete(txn, keyBuffer);
                } catch (Exception e) {
                    throw new RuntimeException(LambdaLogger.buildMessage("Error deleting key {}", key), e);
                }
            });
            txn.commit();
        } catch (RuntimeException e) {
            throw new RuntimeException(LambdaLogger.buildMessage("Error deleting {} keys ", keys.size()), e);
        }
    }

    private static Dbi<ByteBuffer> openDbi(final Env<ByteBuffer> env, final String name) {
        LOGGER.debug("Opening LMDB database with name: {}", name);
        return env.openDbi(name, DbiFlags.MDB_CREATE);
    }

    public Map<String, String> getDbInfo() {
        return LmdbUtils.getDbInfo(lmdbEnvironment, lmdbDbi);
    }

    public long getEntryCount(final Txn<ByteBuffer> txn) {
        return lmdbDbi.stat(txn).entries;
    }

    public long getEntryCount() {
        return LmdbUtils.getWithReadTxn(lmdbEnvironment, this::getEntryCount);
    }

    public void logDatabaseContents(final Txn<ByteBuffer> txn) {
        LmdbUtils.logDatabaseContents(
                lmdbEnvironment,
                lmdbDbi,
                txn,
                byteBuffer -> keySerde.deserialize(byteBuffer).toString(),
                byteBuffer -> valueSerde.deserialize(byteBuffer).toString());
    }

    /**
     * Dumps all entries in the database to a single logger entry with one line per database entry.
     * This could potentially return thousands of rows so is only intended for small scale use in
     * testing. Entries are returned in the order they are held in the DB, e.g. a-z (unless the DB
     * is configured with reverse keys). The keys/values are de-serialised and a toString() is applied
     * to the resulting objects.
     */
    public void logDatabaseContents() {
        LmdbUtils.logDatabaseContents(
                lmdbEnvironment,
                lmdbDbi,
                byteBuffer -> keySerde.deserialize(byteBuffer).toString(),
                byteBuffer -> valueSerde.deserialize(byteBuffer).toString());
    }

    public void logRawDatabaseContents(final Txn<ByteBuffer> txn) {
        LmdbUtils.logRawDatabaseContents(
                lmdbEnvironment,
                lmdbDbi,
                txn);
    }

    /**
     * Dumps all entries in the database to a single logger entry with one line per database entry.
     * This could potentially return thousands of rows so is only intended for small scale use in
     * testing. Entries are returned in the order they are held in the DB, e.g. a-z (unless the DB
     * is configured with reverse keys). The keys/values are output as hex representations of the
     * byte values.
     */
    public void logRawDatabaseContents() {
        LmdbUtils.logRawDatabaseContents(
                lmdbEnvironment,
                lmdbDbi);
    }
}
