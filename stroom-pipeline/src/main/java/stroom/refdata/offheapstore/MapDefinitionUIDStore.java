package stroom.refdata.offheapstore;

import com.google.common.base.Preconditions;
import org.lmdbjava.Env;
import org.lmdbjava.Txn;
import stroom.refdata.lmdb.LmdbUtils;
import stroom.refdata.lmdb.serde.Serde;
import stroom.refdata.offheapstore.databases.MapUidForwardDb;
import stroom.refdata.offheapstore.databases.MapUidReverseDb;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.nio.ByteBuffer;
import java.util.Optional;

public class MapDefinitionUIDStore {

    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(MapDefinitionUIDStore.class);

    private final MapUidForwardDb mapUidForwardDb;
    private final MapUidReverseDb mapUidReverseDb;
    private final Env<ByteBuffer> lmdbEnv;

    //TODO probably need a method to delete MapDefinition<=>UID pairs from the store

    // TODO may want a background process that scans the reverse table to look for gaps
    // in the UID sequence and add the missing ones to another table which can be used
    // for allocating next UIDs before falling back to getting the next highest. If we don't
    // we could end up running out of UIDs in the LONG term.

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

    public UID getOrCreateUid(final MapDefinition mapDefinition) {
        // The returned UID is outside the txn so must be a clone of the found one
        return LmdbUtils.getWithWriteTxn(lmdbEnv, writeTxn ->
                getOrCreateUid(writeTxn, mapDefinition).clone());

    }

    /**
     * Returns the UID corresponding to the passed mapDefinition if it exists in the two mapping DBs. If it doesn't
     * exist, a forward and reverse mapping will be created and the new UID returned. The returned UID warps a
     * direct allocation {@link ByteBuffer} owned by LMDB so it may ONLY be used whilst still inside the passed
     * {@link Txn}.
     */
    public UID getOrCreateUid(final Txn<ByteBuffer> writeTxn, final MapDefinition mapDefinition) {
        Preconditions.checkArgument(!writeTxn.isReadOnly(), "Must be a write transaction");

        final Serde<MapDefinition> mapDefinitionSerde = mapUidForwardDb.getKeySerde();
        final Serde<UID> uidSerde = mapUidForwardDb.getValueSerde();

        final ByteBuffer mapDefinitionBuffer = LmdbUtils.buildDbKeyBuffer(lmdbEnv, mapDefinition, mapDefinitionSerde);

        mapUidForwardDb.getKeySerde().serialize(mapDefinitionBuffer, mapDefinition);

        // see if we already have a UID for this mapDefinition, if not create the pair
        // of entries
        return mapUidForwardDb.getUID(writeTxn, mapDefinitionBuffer)
                .map(uidBuffer -> {
                    LAMBDA_LOGGER.debug(() ->
                            LambdaLogger.buildMessage("Found existing UID {}", ByteArrayUtils.byteBufferInfo(uidBuffer)));
                    return uidSerde.deserialize(uidBuffer);
                })
                .orElseGet(() ->
                        createForwardReversePair(writeTxn, mapDefinitionBuffer));
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

    private UID createForwardReversePair(final Txn<ByteBuffer> writeTxn, final ByteBuffer mapDefinitionBuffer) {
        // this is all done in a write txn so we can be sure of consistency between the forward and reverse DBs


        LAMBDA_LOGGER.debug(() ->
                LambdaLogger.buildMessage("Creating UID mappings for mapDefinition {}",
                        ByteArrayUtils.byteBufferInfo(mapDefinitionBuffer)));

        // get the highest current UID
        final Optional<ByteBuffer> optHighestUid = mapUidReverseDb.getHighestUid(writeTxn);

        final ByteBuffer nextUidKeyBuffer = optHighestUid
                .map(highestUidBuffer ->
                        UID.wrap(highestUidBuffer).nextUid().getBackingBuffer())
                .orElseGet(() ->
                        UID.of(0).getBackingBuffer()
                );

        // put the reverse entry
        LAMBDA_LOGGER.debug(() -> LambdaLogger.buildMessage(
                "nextUidKeyBuffer {}", ByteArrayUtils.byteBufferInfo(nextUidKeyBuffer)));
        LAMBDA_LOGGER.debug(() -> LambdaLogger.buildMessage(
                "mapDefinitionBuffer {}", ByteArrayUtils.byteBufferInfo(mapDefinitionBuffer)));
        mapUidReverseDb.putReverseEntry(writeTxn, nextUidKeyBuffer, mapDefinitionBuffer);

        // we can't reuse the buffers as they are directly allocated so need to copy them
        final ByteBuffer mapDefinitionKeyBuffer = LmdbUtils.copyDirectBuffer(mapDefinitionBuffer);
        final ByteBuffer nextUidValueBuffer = LmdbUtils.copyDirectBuffer(nextUidKeyBuffer);

        LAMBDA_LOGGER.debug(() -> LambdaLogger.buildMessage(
                "mapDefinitionKeyBuffer {}", ByteArrayUtils.byteBufferInfo(mapDefinitionKeyBuffer)));
        LAMBDA_LOGGER.debug(() -> LambdaLogger.buildMessage(
                "nextUidValueBuffer {}", ByteArrayUtils.byteBufferInfo(nextUidValueBuffer)));

        // put the forward entry
        mapUidForwardDb.putForwardEntry(writeTxn, mapDefinitionKeyBuffer, nextUidValueBuffer);

        // this buffer is 'owned' by LMDB now but we are still in a txn so can pass it back
        LAMBDA_LOGGER.debug(() -> LambdaLogger.buildMessage(
                "nextUidValueBuffer {}", ByteArrayUtils.byteBufferInfo(nextUidValueBuffer)));

        return UID.wrap(nextUidValueBuffer);
    }

    Optional<MapDefinition> getMapDefinition(UID uid) {
        return null;
    }

}
