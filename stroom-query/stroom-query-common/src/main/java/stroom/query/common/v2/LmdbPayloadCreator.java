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

package stroom.query.common.v2;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.lmdb2.LmdbDb;
import stroom.lmdb2.WriteTxn;
import stroom.query.api.QueryKey;
import stroom.query.common.v2.SearchProgressLog.SearchPhase;
import stroom.util.concurrent.CompleteException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.SimpleMetrics;

import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.lmdbjava.Cursor;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class LmdbPayloadCreator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbPayloadCreator.class);

    private final int maxPayloadSize;
    private final QueryKey queryKey;
    private final LmdbDataStore lmdbDataStore;
    private final LmdbPayloadQueue currentPayload = new LmdbPayloadQueue(1);
    private final LmdbRowKeyFactory lmdbRowKeyFactory;
    private final ByteBufferFactory bufferFactory;
    private final int minPayloadSize;

    LmdbPayloadCreator(final QueryKey queryKey,
                       final LmdbDataStore lmdbDataStore,
                       final AbstractResultStoreConfig resultStoreConfig,
                       final LmdbRowKeyFactory lmdbRowKeyFactory,
                       final ByteBufferFactory bufferFactory) {
        this.queryKey = queryKey;
        this.lmdbDataStore = lmdbDataStore;
        maxPayloadSize = (int) resultStoreConfig.getMaxPayloadSize().getBytes();
        this.lmdbRowKeyFactory = lmdbRowKeyFactory;
        this.bufferFactory = bufferFactory;
        this.minPayloadSize = (int) resultStoreConfig.getMinPayloadSize().getBytes();
    }

    /**
     * Read items from the supplied input and transfer them to the data store.
     *
     * @param input The input to read.
     */
    void readPayload(final Input input) {
        SearchProgressLog.increment(queryKey, SearchPhase.LMDB_DATA_STORE_READ_PAYLOAD);
        SimpleMetrics.measure("readPayload", () -> {
            // Determine how many bytes the payload contains.
            final int length = input.readInt();
            if (length > 0) {
                // If there are some bytes to read then read them.
                final byte[] bytes = input.readBytes(length);
                try (final Input in = new Input(new ByteArrayInputStream(bytes))) {
                    while (!in.end()) {
                        final int rowKeyLength = in.readInt();
                        final byte[] key = in.readBytes(rowKeyLength);
                        final ByteBuffer keyBuffer = bufferFactory.acquire(key.length);
                        keyBuffer.put(key, 0, key.length);
                        keyBuffer.flip();

                        final int valueLength = in.readInt();
                        final byte[] value = in.readBytes(valueLength);
                        final ByteBuffer valueBuffer = bufferFactory.acquire(value.length);
                        valueBuffer.put(value, 0, value.length);
                        valueBuffer.flip();

                        // Create a new unique key if this isn't a group key.
                        LmdbKV lmdbKV = new LmdbKV(null, keyBuffer, valueBuffer);
                        lmdbKV = lmdbRowKeyFactory.makeUnique(lmdbKV);

                        lmdbDataStore.put(lmdbKV);
                    }
                }
            }
        });
    }

    /**
     * Write data from the data store to an output removing them from the datastore as we go as they will be transferred
     * to another store.
     *
     * @param output The output to write to.
     */
    void writePayload(final Output output) {
        try {
            SearchProgressLog.increment(queryKey, SearchPhase.LMDB_DATA_STORE_WRITE_PAYLOAD);
            SimpleMetrics.measure("writePayload", () -> {
                try {
                    // Get the current payload.
                    final LmdbPayload payload = currentPayload.poll();
                    if (payload == null) {
                        // There will be 0 bytes to read so just write 0.
                        output.writeInt(0);

                    } else {
                        final byte[] data = payload.getData();
                        output.writeInt(data.length);
                        output.writeBytes(data);
                    }
                } catch (final KryoException e) {
                    // Expected as sometimes the output stream is closed by the receiving node.
                    LOGGER.debug(e::getMessage, e);
                } catch (final InterruptedException e) {
                    // Ensure we don't deliver further payloads.
                    currentPayload.terminate();

                    // There will be 0 bytes to read so just write 0.
                    output.writeInt(0);
                    LOGGER.trace(e::getMessage, e);
                    // Keep interrupting this thread.
                    Thread.currentThread().interrupt();
                } catch (final CompleteException e) {
                    // There will be 0 bytes to read so just write 0.
                    output.writeInt(0);
                    LOGGER.debug(() -> "Complete");
                    LOGGER.trace(e::getMessage, e);
                }
            });
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
        }
    }

    boolean isEmpty() {
        return currentPayload.isEmpty();
    }

    boolean addPayload(final WriteTxn writeTxn,
                       final LmdbDb db,
                       final boolean complete) {
        final LmdbPayload payload = createPayload(writeTxn, db, complete);
        doPut(payload);
        return payload.isFinalPayload();
    }

    void finalPayload() {
        currentPayload.complete();
    }

    private void doPut(final LmdbPayload payload) {
        try {
            currentPayload.put(payload);
        } catch (final InterruptedException e) {
            LOGGER.trace(e.getMessage(), e);
            // Keep interrupting this thread.
            Thread.currentThread().interrupt();
        }
    }

    private LmdbPayload createPayload(final WriteTxn writeTxn,
                                      final LmdbDb db,
                                      final boolean complete) {
        SearchProgressLog.increment(queryKey, SearchPhase.LMDB_DATA_STORE_CREATE_PAYLOAD);
        return SimpleMetrics.measure("createPayload", () -> {
            if (maxPayloadSize > 0) {
                final PayloadOutput payloadOutput = new PayloadOutput(minPayloadSize);
                final AtomicBoolean finalPayload = new AtomicBoolean(complete);
                long size = 0;
                long count = 0;

                try (final Cursor<ByteBuffer> cursor = db.getDbi().openCursor(writeTxn.get())) {
                    boolean isFound = cursor.first();
                    while (isFound) {
                        final ByteBuffer keyBuffer = cursor.key();
                        final ByteBuffer valBuffer = cursor.val();

                        // Add to the size of the current payload.
                        size += 4;
                        size += keyBuffer.remaining();
                        size += 4;
                        size += valBuffer.remaining();
                        count++;

                        if (size < maxPayloadSize || count == 1) {
                            payloadOutput.writeInt(keyBuffer.remaining());
                            payloadOutput.writeByteBuffer(keyBuffer);
                            payloadOutput.writeInt(valBuffer.remaining());
                            payloadOutput.writeByteBuffer(valBuffer);

                            db.delete(writeTxn, keyBuffer.flip());

                        } else {
                            // We have reached the maximum payload size so stop adding.
                            // We also can't be complete as more payloads will be needed.
                            finalPayload.set(false);
                            break;
                        }

                        isFound = cursor.next();
                    }
                }

                writeTxn.commit();
                payloadOutput.close();
                return new LmdbPayload(finalPayload.get(), payloadOutput.toBytes());

            } else {
                final PayloadOutput payloadOutput = new PayloadOutput(minPayloadSize);
                try (final Cursor<ByteBuffer> cursor = db.getDbi().openCursor(writeTxn.get())) {
                    boolean isFound = cursor.first();
                    while (isFound) {
                        final ByteBuffer keyBuffer = cursor.key();
                        final ByteBuffer valBuffer = cursor.val();

                        payloadOutput.writeInt(keyBuffer.remaining());
                        payloadOutput.writeByteBuffer(keyBuffer);
                        payloadOutput.writeInt(valBuffer.remaining());
                        payloadOutput.writeByteBuffer(valBuffer);

                        isFound = cursor.next();
                    }
                }

                db.drop(writeTxn);
                writeTxn.commit();
                payloadOutput.close();
                return new LmdbPayload(complete, payloadOutput.toBytes());
            }
        });
    }
}
