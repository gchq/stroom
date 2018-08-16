package stroom.refdata.store.offheapstore;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import org.lmdbjava.Env;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.store.MapDefinition;
import stroom.refdata.store.RefStreamDefinition;
import stroom.refdata.store.offheapstore.lmdb.LmdbUtils;
import stroom.refdata.store.offheapstore.databases.MapUidForwardDb;
import stroom.refdata.store.offheapstore.databases.MapUidReverseDb;
import stroom.refdata.util.ByteBufferUtils;
import stroom.refdata.util.PooledByteBuffer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * This class provides a front door for all interactions with the {@link MapUidForwardDb} and
 * {@link MapUidReverseDb} databases. This is to ensure the entries in both DBs are kept in sync
 * as each entry in one should have a corresponding entry in the other.
 *
 * It manages the creation and retrieval of {@link MapDefinition} <==> to UID mappings. These
 * mappings are used to reduce the storage space required for all the entries in the keyvalue
 * and rangevalue stores by just having a 4 byte UID in the key instead of a many byte {@link MapDefinition}
 */
public class MapDefinitionUIDStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(MapDefinitionUIDStore.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(MapDefinitionUIDStore.class);

    private final MapUidForwardDb mapUidForwardDb;
    private final MapUidReverseDb mapUidReverseDb;
    private final Env<ByteBuffer> lmdbEnv;

    // TODO may want a background process that scans the reverse table to look for gaps
    // in the UID sequence and add the missing ones to another table which can be used
    // for allocating next UIDs before falling back to getting the next highest. If we don't
    // we could end up running out of UIDs in the LONG term. If that happened you could just delete
    // the files off the DB and start again.

    MapDefinitionUIDStore(final Env<ByteBuffer> lmdbEnv,
                          final MapUidForwardDb mapUidForwardDb,
                          final MapUidReverseDb mapUidReverseDb) {
        this.lmdbEnv = lmdbEnv;
        this.mapUidForwardDb = mapUidForwardDb;
        this.mapUidReverseDb = mapUidReverseDb;
    }

    Optional<UID> getUid(final MapDefinition mapDefinition) {
        // The returned UID is outside the txn so must be a clone of the found one
        return LmdbUtils.getWithReadTxn(lmdbEnv, txn ->
                getUid(txn, mapDefinition)
                        .flatMap(uid -> Optional.of(uid.clone())));
    }

    Optional<UID> getUid(final Txn<ByteBuffer> txn, final MapDefinition mapDefinition) {
        return mapUidForwardDb.get(txn, mapDefinition);
    }

    boolean exists(final MapDefinition mapDefinition) {
        return mapUidForwardDb.exists(mapDefinition);
    }

    /**
     * Returns the UID corresponding to the passed mapDefinition if it exists in the two mapping DBs. If it doesn't
     * exist, a forward and reverse mapping will be created and the new UID returned. The returned UID warps a
     * direct allocation {@link ByteBuffer} owned by LMDB so it may ONLY be used whilst still inside the passed
     * {@link Txn}.
     */
    UID getOrCreateUid(final Txn<ByteBuffer> writeTxn, final MapDefinition mapDefinition) {
        Preconditions.checkArgument(!writeTxn.isReadOnly(), "Must be a write transaction");

        try (final PooledByteBuffer mapDefinitionPooledBuffer = mapUidForwardDb.getPooledKeyBuffer()) {
            final ByteBuffer mapDefinitionBuffer = mapDefinitionPooledBuffer.getByteBuffer();

            mapUidForwardDb.serializeKey(mapDefinitionBuffer, mapDefinition);

            // if we already have a UID for this mapDefinition return it
            // if not, create the pair of entries and return the created UID
            return mapUidForwardDb.getAsBytes(writeTxn, mapDefinitionBuffer)
                    .map(uidBuffer -> {
                        LAMBDA_LOGGER.trace(() ->
                                LambdaLogger.buildMessage("Found existing UID {}", ByteBufferUtils.byteBufferInfo(uidBuffer)));
                        return mapUidForwardDb.deserializeValue(uidBuffer);
                    })
                    .orElseGet(() ->
                            createForwardReversePair(writeTxn, mapDefinitionBuffer));
        }
    }

    Optional<UID> get(final Txn<ByteBuffer> txn, final MapDefinition mapDefinition) {
        return mapUidForwardDb.get(txn, mapDefinition);
    }

    public long getEntryCount() {
        long entryCountForward = mapUidForwardDb.getEntryCount();
        long entryCountReverse = mapUidReverseDb.getEntryCount();

        if (entryCountForward != entryCountReverse) {
            throw new RuntimeException(LambdaLogger.buildMessage("Entry counts don't match, forward {}, reverse {}",
                    entryCountForward, entryCountReverse));
        }
        return entryCountForward;
    }

    Optional<UID> getNextMapDefinition(final Txn<ByteBuffer> writeTxn,
                                       final RefStreamDefinition refStreamDefinition,
                                       final Supplier<ByteBuffer> uidBufferSupplier) {

        return mapUidForwardDb.getNextMapDefinition(writeTxn, refStreamDefinition, uidBufferSupplier);
    }

    void deletePair(final Txn<ByteBuffer> writeTxn,
                    final UID mapUid) {
        LOGGER.trace("deletePair({})", mapUid);

        final ByteBuffer mapDefinitionBuffer = mapUidReverseDb.getAsBytes(writeTxn, mapUid.getBackingBuffer())
                .orElseThrow(() -> new RuntimeException(LambdaLogger.buildMessage(
                        "No entry exists for mapUid {}", ByteBufferUtils.byteBufferInfo(mapUid.getBackingBuffer()))));

        // these two MUST be done in the same txn to ensure data consistency
        mapUidForwardDb.delete(writeTxn, mapDefinitionBuffer);
        mapUidReverseDb.delete(writeTxn, mapUid.getBackingBuffer());
    }

    private UID createForwardReversePair(final Txn<ByteBuffer> writeTxn, final ByteBuffer mapDefinitionBuffer) {
        // this is all done in a write txn so we can be sure of consistency between the forward and reverse DBs

        LAMBDA_LOGGER.trace(() ->
                LambdaLogger.buildMessage("Creating UID mappings for mapDefinition {}",
                        ByteBufferUtils.byteBufferInfo(mapDefinitionBuffer)));

        // get the highest current UID
        final Optional<ByteBuffer> optHighestUid = mapUidReverseDb.getHighestUid(writeTxn);

        final ByteBuffer nextUidKeyBuffer = optHighestUid
                .map(highestUidBuffer ->
                        UID.wrap(highestUidBuffer).nextUid().getBackingBuffer())
                .orElseGet(() ->
                        UID.of(0).getBackingBuffer()
                );

        // put the reverse entry
        LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage(
                "nextUidKeyBuffer {}", ByteBufferUtils.byteBufferInfo(nextUidKeyBuffer)));
        LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage(
                "mapDefinitionBuffer {}", ByteBufferUtils.byteBufferInfo(mapDefinitionBuffer)));
        mapUidReverseDb.putReverseEntry(writeTxn, nextUidKeyBuffer, mapDefinitionBuffer);

        // We are not changing the buffers so can just reuse them

        LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage(
                "mapDefinitionKeyBuffer {}", ByteBufferUtils.byteBufferInfo(mapDefinitionBuffer)));
        LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage(
                "nextUidValueBuffer {}", ByteBufferUtils.byteBufferInfo(nextUidKeyBuffer)));

        // put the forward entry
        mapUidForwardDb.putForwardEntry(writeTxn, mapDefinitionBuffer, nextUidKeyBuffer);

        // this buffer is 'owned' by LMDB now but we are still in a txn so can pass it back
        LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage(
                "nextUidValueBuffer {}", ByteBufferUtils.byteBufferInfo(nextUidKeyBuffer)));

        // ensure it is ready for reading again as we are returning it
        UID mapUid = UID.wrap(nextUidKeyBuffer);
        LOGGER.trace("Creating UID mapping for {}", mapUid);
        return mapUid;
    }

}
