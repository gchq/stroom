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

package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Generator;
import stroom.dashboard.expression.v1.Selection;
import stroom.dashboard.expression.v1.Selector;
import stroom.dashboard.expression.v1.Val;
import stroom.lmdb.AbstractLmdbDb;
import stroom.pipeline.refdata.util.ByteBufferPool;
import stroom.pipeline.refdata.util.ByteBufferUtils;
import stroom.pipeline.refdata.util.PooledByteBuffer;
import stroom.util.concurrent.StripedLock;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.esotericsoftware.kryo.io.ByteBufferOutput;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Env;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;

import javax.annotation.Nonnull;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

public class KeyValueStoreDb extends AbstractLmdbDb<Key, Generator[]> {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(KeyValueStoreDb.class);

    private static final long COMMIT_FREQUENCY_MS = 1000;
    public static final String DB_NAME = "search_results";

    private final AtomicLong ungroupedItemSequenceNumber = new AtomicLong();
    private final CompiledField[] compiledFields;
    private final LmdbCompiledSorter[] compiledSorters;
    private final CompiledDepths compiledDepths;
    private final Sizes maxResults;
    private final Sizes storeSize;
    private final AtomicLong totalResultCount = new AtomicLong();
    private final AtomicLong resultCount = new AtomicLong();
    private final ItemSerialiser itemSerialiser;
    private final boolean hasSort;

    private final KeySerde keySerde;
    private final ValueSerde valueSerde;
    private final StripedLock stripedLock;
    private volatile boolean hasEnoughData;

//    private Txn<ByteBuffer> writeTxn;
//    private Txn<ByteBuffer> readTxn;
//    private int writeCount;

    private final ByteBuffer keyBuffer = ByteBuffer.allocateDirect(4096);
    private final ByteBuffer valueBuffer = ByteBuffer.allocateDirect(4096);
    private final LinkedBlockingQueue<Optional<QueueItem>> queue = new LinkedBlockingQueue<>(1000000);
    private final Executor executor;


    private final CountDownLatch addingData;

    KeyValueStoreDb(final Env<ByteBuffer> lmdbEnvironment,
                    final ByteBufferPool byteBufferPool,
                    final KeySerde keySerde,
                    final ValueSerde valueSerde,
                    final ItemSerialiser itemSerialiser,
                    final CompiledField[] compiledFields,
                    final LmdbCompiledSorter[] compiledSorters,
                    final CompiledDepths compiledDepths,
                    final Sizes maxResults,
                    final Sizes storeSize) {

        super(lmdbEnvironment, byteBufferPool, keySerde, valueSerde, DB_NAME);
        this.itemSerialiser = itemSerialiser;
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
        this.stripedLock = new StripedLock();


        this.compiledFields = compiledFields;
        this.compiledSorters = compiledSorters;
        this.compiledDepths = compiledDepths;
        this.maxResults = maxResults;
        this.storeSize = storeSize;

        // Find out if we have any sorting.
        boolean hasSort = false;
        for (final LmdbCompiledSorter sorter : compiledSorters) {
            if (sorter != null) {
                hasSort = true;
                break;
            }
        }
        this.hasSort = hasSort;

        // Start consume loop.
        addingData = new CountDownLatch(1);
        executor = Executors.newSingleThreadExecutor();// TODO : Use provided executor but don't allow it to be terminated by search termination.
        final Runnable runnable = () -> {
            Metrics.measure("Transfer", () -> {
                try {
                    Txn<ByteBuffer> writeTxn = null;
                    boolean run = true;
                    boolean needsCommit = false;
                    long lastCommitMs = System.currentTimeMillis();

                    while (run) {
                        final Optional<QueueItem> optional = queue.poll(1, TimeUnit.SECONDS);
                        if (optional != null) {
                            if (optional.isPresent()) {
                                if (writeTxn == null) {
                                    writeTxn = lmdbEnvironment.txnWrite();
                                }

                                final QueueItem item = optional.get();
                                consume(writeTxn, item.key, item.generators);

                            } else {
                                // Stop looping.
                                run = false;
                                // Ensure final commit.
                                lastCommitMs = 0;
                            }

                            // We have either added something or need a final commit.
                            needsCommit = true;
                        }

                        if (needsCommit) {
                            final long now = System.currentTimeMillis();
                            if (lastCommitMs < now - COMMIT_FREQUENCY_MS) {
                                lastCommitMs = now;
                                needsCommit = false;

                                if (writeTxn != null) {
                                    final Txn<ByteBuffer> txn = writeTxn;
                                    writeTxn = null;
                                    Metrics.measure("Commit", () -> {
                                        txn.commit();
                                        txn.close();
                                    });
                                }
                            }
                        }
                    }

                } catch (final InterruptedException e) {
                    LOGGER.debug(e.getMessage(), e);
                    // Continue to interrupt.
                    Thread.currentThread().interrupt();
                } finally {
                    addingData.countDown();
                }
            });
        };
        executor.execute(runnable);
    }

    public void complete() throws InterruptedException {
        queue.put(Optional.empty());
    }

    public void awaitCompletion() throws InterruptedException {
        addingData.await();
    }

    public void put(final Key key, final Generator[] value) {
        LOGGER.trace(() -> "put");
        if (Thread.currentThread().isInterrupted() || hasEnoughData) {
            return;
        }

        totalResultCount.incrementAndGet();

        // Some searches can be terminated early if the user is not sorting or grouping.
        if (!hasEnoughData && !hasSort && !compiledDepths.hasGroup()) {
            // No sorting or grouping so we can stop the search as soon as we have the number of results requested by
            // the client
            if (maxResults != null && totalResultCount.get() >= maxResults.size(0)) {
                hasEnoughData = true;
            }
        }

        try {
            queue.put(Optional.of(new QueueItem(key, value)));
        } catch (final InterruptedException e) {
            LOGGER.debug(e.getMessage(), e);
            // Keep interrupting this thread.
            Thread.currentThread().interrupt();
        }
    }

    public void consume(final Txn<ByteBuffer> txn, final Key key, final Generator[] value) {
        Metrics.measure("Consume", () -> {
            try {
                LOGGER.trace(() -> "consume");

                // Reset the buffers ready for use.
                keyBuffer.clear();
                valueBuffer.clear();

                keySerde.serialize(keyBuffer, key);
                keyBuffer.flip();

                if (key.isGrouped()) {
                    Metrics.measure("Grouped insert", () -> {
                        final Lock lock = stripedLock.getLockForKey(key);
                        lock.lock();
                        try {
                            // See if we can find an existing item.
                            final ByteBuffer existing = Metrics.measure("Grouped get", () -> getLmdbDbi().get(txn, keyBuffer));
                            if (existing != null) {
                                final Generator[] existingValue = valueSerde.deserialize(existing);
                                final Generator[] combined = combine(existingValue, value);

                                valueSerde.serialize(valueBuffer, combined);
                                valueBuffer.flip();
                            } else {
                                resultCount.incrementAndGet();
                                valueSerde.serialize(valueBuffer, value);
                                valueBuffer.flip();
                            }

                            Metrics.measure("Grouped put", () -> getLmdbDbi().put(txn, keyBuffer, valueBuffer));
                        } finally {
                            lock.unlock();
                        }
                    });

                } else {
                    Metrics.measure("Ungrouped insert", () -> {
                        resultCount.incrementAndGet();
                        valueSerde.serialize(valueBuffer, value);
                        valueBuffer.flip();

                        Metrics.measure("Ungrouped put", () -> getLmdbDbi().put(txn, keyBuffer, valueBuffer));
                    });
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
                throw new RuntimeException(LogUtil.message("Error putting key {}, value {}", key, value), e);
            }
        });
    }

    private Generator[] combine(final Generator[] existing, final Generator[] value) {
        return Metrics.measure("Combine", () -> {
            Generator[] result = existing;

            // Combine the new item into the original item.
            for (int i = 0; i < result.length; i++) {
                Generator existingGenerator = result[i];
                Generator newGenerator = value[i];
                if (newGenerator != null) {
                    if (existingGenerator == null) {
                        result[i] = newGenerator;
                    } else {
                        existingGenerator.merge(newGenerator);
                    }
                }
            }

            return result;
        });
    }

    void clear() {
        try (final Txn<ByteBuffer> writeTxn = getLmdbEnvironment().txnWrite()) {
            getLmdbDbi().drop(writeTxn, true);
        } catch (RuntimeException e) {
            throw new RuntimeException(LogUtil.message("Error clearing db", e));
        }
        resultCount.set(0);
        totalResultCount.set(0);
    }

    void writePayload(final Output output) {
        Metrics.measure("writePayload", () -> {
            try (final Txn<ByteBuffer> readTxn = getLmdbEnvironment().txnRead()) {
                getLmdbDbi().iterate(readTxn).forEach(kv -> {
                    final ByteBuffer keyBuffer = kv.key();
                    final ByteBuffer valBuffer = kv.val();
                    final Key key = keySerde.deserialize(keyBuffer);
                    Generator[] value;

                    if (key.isGrouped()) {
                        final Lock lock = stripedLock.getLockForKey(key);
                        lock.lock();
                        try {
                            final ByteBuffer currentValueBuffer = getLmdbDbi().get(readTxn, keyBuffer);
                            value = valueSerde.deserialize(currentValueBuffer);
                            getLmdbDbi().delete(keyBuffer);

                        } finally {
                            lock.unlock();
                        }
                    } else {
                        value = valueSerde.deserialize(valBuffer);
                        getLmdbDbi().delete(keyBuffer);
                    }

                    itemSerialiser.writeKey(key, output);
                    itemSerialiser.writeGenerators(value, output);
                });
            } catch (RuntimeException e) {
                throw new RuntimeException(LogUtil.message("Error clearing db", e));
            }
        });
    }

    boolean readPayload(final Input input) {
        return Metrics.measure("readPayload", () -> {
            while (!input.end()) {
                final Key key = itemSerialiser.readKey(input);
                final Generator[] value = itemSerialiser.readGenerators(input);

                KeyPart lastPart = key.getLast();
                if (lastPart != null && !lastPart.isGrouped()) {
                    // Ensure sequence numbers are unique for this data store.
                    ((UngroupedKeyPart) lastPart).setSequenceNumber(ungroupedItemSequenceNumber.incrementAndGet());
                }

                put(key, value);
            }

            // Return success if we have not been asked to terminate and we are still willing to accept data.
            return !Thread.currentThread().isInterrupted() && !hasEnoughData;
        });
    }

    public Items get(final RawKey rawParentKey) {
        return Metrics.measure("get", () -> {
            Key parentKey = new Key(Collections.emptyList());
            if (rawParentKey != null) {
                parentKey = itemSerialiser.toKey(rawParentKey);
            }

            final List<ItemImpl> list = new ArrayList<>();
            try (PooledByteBuffer pooledByteBuffer = getByteBufferPool().getPooledByteBuffer(4096)) {
                final ByteBuffer byteBuffer = pooledByteBuffer.getByteBuffer();
                try (final Output output = new ByteBufferOutput(byteBuffer)) {
                    itemSerialiser.writeChildKey(parentKey, output);
                }
                byteBuffer.flip();

                final KeyRange<ByteBuffer> keyRange = KeyRange.atLeast(byteBuffer);

                final int depth = parentKey.getDepth() + 1;
                final int trimmedSize = maxResults.size(depth);
                final int maxSize;
                if (trimmedSize < Integer.MAX_VALUE / 2) {
                    maxSize = trimmedSize * 2;
                } else {
                    maxSize = Integer.MAX_VALUE;
                }
                final LmdbCompiledSorter sorter = compiledSorters[depth];
                boolean sorted = true;

                boolean inRange = true;
                try (final Txn<ByteBuffer> readTxn = getLmdbEnvironment().txnRead()) {
                    try (final CursorIterable<ByteBuffer> cursorIterable = getLmdbDbi().iterate(readTxn, keyRange)) {
                        final Iterator<KeyVal<ByteBuffer>> iterator = cursorIterable.iterator();

                        while (iterator.hasNext() && inRange) {
                            final KeyVal<ByteBuffer> keyVal = iterator.next();

                            final Key key = keySerde.deserialize(keyVal.key());

                            if (!key.getParent().equals(parentKey)) {
                                inRange = false;

                            } else {
                                final byte[] keyBytes = ByteBufferUtils.toBytes(keyVal.key().flip());
                                final Generator[] generators = valueSerde.deserialize(keyVal.val());

                                final Supplier<Selection<Val>> selectionSupplier = () -> {
                                    // TODO : Implement child selection. Note that if no sorting is present we could simplify this.
                                    return null;
                                };

                                list.add(new ItemImpl(new RawKey(keyBytes), key, generators, selectionSupplier));
                                sorted = false;

                                if (list.size() > maxSize) {
                                    sortAndTrim(list, sorter, trimmedSize);
                                    sorted = true;
                                }
                            }
                        }
                    }
                }

                if (!sorted) {
                    sortAndTrim(list, sorter, trimmedSize);
                }
            }

            return new Items() {
                @Override
                @Nonnull
                public Iterator<Item> iterator() {
                    final Iterator<ItemImpl> iterator = list.iterator();
                    return new Iterator<>() {
                        @Override
                        public boolean hasNext() {
                            return iterator.hasNext();
                        }

                        @Override
                        public Item next() {
                            return iterator.next();
                        }
                    };
                }

                @Override
                public int size() {
                    return list.size();
                }
            };
        });
    }

    public long getSize() {
        return resultCount.get();
    }

    public long getTotalSize() {
        return totalResultCount.get();
    }

    private void sortAndTrim(final List<ItemImpl> list, final LmdbCompiledSorter sorter, final int trimmedSize) {
        if (sorter != null) {
            list.sort(sorter);
        }
        while (list.size() > trimmedSize) {
            list.remove(list.size() - 1);
        }
    }

    public static class ItemImpl implements Item {
        private final RawKey rawKey;
        private final Key key;
        private final Generator[] generators;
        private final Supplier<Selection<Val>> childSelection;

        public ItemImpl(final RawKey rawKey,
                        final Key key,
                        final Generator[] generators,
                        final Supplier<Selection<Val>> childSelection) {
            this.rawKey = rawKey;
            this.key = key;
            this.generators = generators;
            this.childSelection = childSelection;
        }

        @Override
        public RawKey getRawKey() {
            return rawKey;
        }

        @Override
        public Key getKey() {
            return key;
        }

        @Override
        public Val getValue(final int index) {
            Val val = null;

            final Generator generator = generators[index];
            if (generator instanceof Selector) {
                final Selector selector = (Selector) generator;
                val = selector.select(childSelection.get());
            } else if (generator != null) {
                val = generator.eval();
            }

            return val;
        }

        public Generator[] getGenerators() {
            return generators;
        }
    }

    private static class QueueItem {
        private final Key key;
        private final Generator[] generators;

        public QueueItem(final Key key,
                         final Generator[] generators) {
            this.key = key;
            this.generators = generators;
        }

        public Key getKey() {
            return key;
        }

        public Generator[] getGenerators() {
            return generators;
        }
    }

}
