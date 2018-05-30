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
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.lmdb.serde.Serde;
import stroom.util.logging.LambdaLogger;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class AbstractLmdbDb<K, V> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLmdbDb.class);

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

    Serde<K> getKeySerde() {
        return keySerde;
    }

    Serde<V> getValueSerde() {
        return valueSerde;
    }

    public V get(final K key) {
        try (final Txn<ByteBuffer> txn = lmdbEnvironment.txnRead()) {
            ByteBuffer keyBuffer = keySerde.serialize(key);
            ByteBuffer valueBuffer = lmdbDbi.get(txn, keyBuffer);
            V value = valueSerde.deserialize(valueBuffer);
            return value;
        } catch (RuntimeException e) {
            throw new RuntimeException(LambdaLogger.buildMessage("Error getting key {}", key), e);
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

    public void put(final K key, final V value) {
        try (final Txn<ByteBuffer> txn = lmdbEnvironment.txnWrite()) {
            ByteBuffer keyBuffer = keySerde.serialize(key);
            ByteBuffer valueBuffer = valueSerde.serialize(value);
            lmdbDbi.put(txn, keyBuffer, valueBuffer);
            txn.commit();
        } catch (RuntimeException e) {
            throw new RuntimeException(LambdaLogger.buildMessage("Error putting key {}, value {}", key, value), e);
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
}
