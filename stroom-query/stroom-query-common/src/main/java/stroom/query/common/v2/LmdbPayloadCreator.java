package stroom.query.common.v2;

import stroom.lmdb.LmdbEnv.BatchingWriteTxn;
import stroom.query.api.v2.QueryKey;
import stroom.query.common.v2.SearchProgressLog.SearchPhase;
import stroom.util.concurrent.CompleteException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.Metrics;

import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;
import org.lmdbjava.Txn;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;

public class LmdbPayloadCreator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbPayloadCreator.class);

    private final Serialisers serialisers;
    private final CompiledField[] compiledFields;
    private final int maxPayloadSize;
    private final QueryKey queryKey;
    private final LmdbDataStore lmdbDataStore;
    private final LmdbPayloadQueue currentPayload = new LmdbPayloadQueue(1);
    private final LmdbRowKeyFactory lmdbRowKeyFactory;
    private final KeyFactory keyFactory;

    LmdbPayloadCreator(final Serialisers serialisers,
                       final QueryKey queryKey,
                       final LmdbDataStore lmdbDataStore,
                       final CompiledField[] compiledFields,
                       final ResultStoreConfig resultStoreConfig,
                       final LmdbRowKeyFactory lmdbRowKeyFactory,
                       final KeyFactory keyFactory) {
        this.serialisers = serialisers;
        this.queryKey = queryKey;
        this.lmdbDataStore = lmdbDataStore;
        this.compiledFields = compiledFields;
        maxPayloadSize = (int) resultStoreConfig.getMaxPayloadSize().getBytes();
        this.lmdbRowKeyFactory = lmdbRowKeyFactory;
        this.keyFactory = keyFactory;
    }

    /**
     * Read items from the supplied input and transfer them to the data store.
     *
     * @param input The input to read.
     */
    void readPayload(final Input input) {
        SearchProgressLog.increment(queryKey, SearchPhase.LMDB_DATA_STORE_READ_PAYLOAD);
        Metrics.measure("readPayload", () -> {
            // Determine how many bytes the payload contains.
            final int length = input.readInt();
            if (length > 0) {
                // If there are some bytes to read then read them.
                final byte[] bytes = input.readBytes(length);
                try (final Input in = new Input(new ByteArrayInputStream(bytes))) {
                    while (!in.end()) {
                        final int rowKeyLength = in.readInt();
                        final byte[] key = in.readBytes(rowKeyLength);
                        ByteBuffer keyBuffer = ByteBuffer.allocateDirect(key.length);
                        keyBuffer.put(key, 0, key.length);
                        keyBuffer.flip();

                        final int valueLength = in.readInt();
                        final byte[] value = in.readBytes(valueLength);
                        ByteBuffer valueBuffer = ByteBuffer.allocateDirect(value.length);
                        valueBuffer.put(value, 0, value.length);
                        valueBuffer.flip();

                        LmdbRowKey rowKey = new LmdbRowKey(keyBuffer);
                        // Create a new unique key if this isn't a group key.
                        rowKey = lmdbRowKeyFactory.makeUnique(rowKey);

                        final LmdbValue lmdbValue = new LmdbValue(serialisers, keyFactory, compiledFields, valueBuffer);
                        final LmdbKV lmdbKV = new LmdbKV(rowKey, lmdbValue);
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
            Metrics.measure("writePayload", () -> {
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

    boolean addPayload(final BatchingWriteTxn batchingWriteTxn,
                       final Dbi<ByteBuffer> dbi,
                       final boolean complete) {
        final LmdbPayload payload = createPayload(batchingWriteTxn, dbi, complete);
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

    private LmdbPayload createPayload(final BatchingWriteTxn batchingWriteTxn,
                                      final Dbi<ByteBuffer> dbi,
                                      final boolean complete) {
        SearchProgressLog.increment(queryKey, SearchPhase.LMDB_DATA_STORE_CREATE_PAYLOAD);
        final Txn<ByteBuffer> writeTxn = batchingWriteTxn.getTxn();

        return Metrics.measure("createPayload", () -> {
            if (maxPayloadSize > 0) {
                final PayloadOutput payloadOutput = serialisers.getOutputFactory().createPayloadOutput();
                boolean finalPayload = complete;
                long size = 0;
                long count = 0;
                try (final CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(writeTxn)) {
                    for (final KeyVal<ByteBuffer> kv : cursorIterable) {
                        final ByteBuffer keyBuffer = kv.key();
                        final ByteBuffer valBuffer = kv.val();

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

                            dbi.delete(writeTxn, keyBuffer.flip());

                        } else {
                            // We have reached the maximum payload size so stop adding.
                            // We also can't be complete as more payloads will be needed.
                            finalPayload = false;
                            break;
                        }
                    }
                }

                batchingWriteTxn.commit();
                payloadOutput.close();
                return new LmdbPayload(finalPayload, payloadOutput.toBytes());

            } else {
                final PayloadOutput payloadOutput = serialisers.getOutputFactory().createPayloadOutput();
                try (final CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(writeTxn)) {
                    for (final KeyVal<ByteBuffer> kv : cursorIterable) {
                        final ByteBuffer keyBuffer = kv.key();
                        final ByteBuffer valBuffer = kv.val();

                        payloadOutput.writeInt(keyBuffer.remaining());
                        payloadOutput.writeByteBuffer(keyBuffer);
                        payloadOutput.writeInt(valBuffer.remaining());
                        payloadOutput.writeByteBuffer(valBuffer);
                    }
                }

                dbi.drop(writeTxn);
                batchingWriteTxn.commit();
                payloadOutput.close();
                return new LmdbPayload(complete, payloadOutput.toBytes());
            }
        });
    }
}
