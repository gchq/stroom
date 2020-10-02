package stroom.pipeline.refdata.store.offheapstore;

import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.refdata.store.offheapstore.databases.MapUidForwardDb;
import stroom.pipeline.refdata.store.offheapstore.databases.MapUidReverseDb;
import stroom.pipeline.refdata.store.offheapstore.lmdb.LmdbUtils;
import stroom.pipeline.refdata.util.ByteBufferUtils;
import stroom.pipeline.refdata.util.PooledByteBuffer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.common.base.Preconditions;
import io.vavr.Tuple2;
import org.lmdbjava.Env;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * This class provides a front door for all interactions with the {@link MapUidForwardDb} and
 * {@link MapUidReverseDb} databases. This is to ensure the entries in both DBs are kept in sync
 * as each entry in one should have a corresponding entry in the other.
 * <p>
 * It manages the creation and retrieval of {@link MapDefinition} <==> to UID mappings. These
 * mappings are used to reduce the storage space required for all the entries in the keyvalue
 * and rangevalue stores by just having a 4 byte UID in the key instead of a many byte {@link MapDefinition}
 */
public class MapDefinitionUIDStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MapDefinitionUIDStore.class);

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
     * exist, a forward and reverse mapping will be created and the new UID returned. The returned UID wraps a
     * direct allocation {@link ByteBuffer} owned by LMDB so it may ONLY be used whilst still inside the passed
     * {@link Txn}.
     */
    UID getOrCreateUid(final Txn<ByteBuffer> writeTxn,
                       final MapDefinition mapDefinition,
                       final PooledByteBuffer uidPooledBuffer) {
        Preconditions.checkArgument(!writeTxn.isReadOnly(), "Must be a write transaction");

        try (final PooledByteBuffer mapDefinitionPooledBuffer = mapUidForwardDb.getPooledKeyBuffer()) {

            final ByteBuffer mapDefinitionBuffer = mapDefinitionPooledBuffer.getByteBuffer();

            mapUidForwardDb.serializeKey(mapDefinitionBuffer, mapDefinition);

            // if we already have a UID for this mapDefinition return it
            // if not, create the pair of entries and return the created UID
            return mapUidForwardDb.getAsBytes(writeTxn, mapDefinitionBuffer)
                    .map(uidBuffer -> {
                        LOGGER.trace(() ->
                                LogUtil.message("Found existing UID {}", ByteBufferUtils.byteBufferInfo(uidBuffer)));
                        return mapUidForwardDb.deserializeValue(uidBuffer);
                    })
                    .orElseGet(() ->
                            createForwardReversePair(writeTxn, mapDefinitionBuffer, uidPooledBuffer));
        }
    }

    Optional<UID> get(final Txn<ByteBuffer> txn, final MapDefinition mapDefinition) {
        return mapUidForwardDb.get(txn, mapDefinition);
    }

    Optional<MapDefinition> get(final Txn<ByteBuffer> txn, final UID mapUid) {
        return mapUidReverseDb.get(txn, mapUid);
    }

    public long getEntryCount() {
        long entryCountForward = mapUidForwardDb.getEntryCount();
        long entryCountReverse = mapUidReverseDb.getEntryCount();

        if (entryCountForward != entryCountReverse) {
            throw new RuntimeException(LogUtil.message("Entry counts don't match, forward {}, reverse {}",
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

        final ByteBuffer mapDefinitionBuffer = mapUidReverseDb.getAsBytes(
                writeTxn,
                mapUid.getBackingBuffer())
                .orElseThrow(() -> new RuntimeException(LogUtil.message(
                        "No entry exists for mapUid {}",
                        ByteBufferUtils.byteBufferInfo(mapUid.getBackingBuffer()))));

        // these two MUST be done in the same txn to ensure data consistency
        mapUidForwardDb.delete(writeTxn, mapDefinitionBuffer);
        mapUidReverseDb.delete(writeTxn, mapUid.getBackingBuffer());
    }

    void forEach(final Txn<ByteBuffer> txn,
                 final Consumer<Tuple2<MapDefinition, UID>> entryConsumer) {

        mapUidForwardDb.forEachEntry( txn, KeyRange.all(), entryConsumer);
    }

    private UID createForwardReversePair(final Txn<ByteBuffer> writeTxn,
                                         final ByteBuffer mapDefinitionBuffer,
                                         final PooledByteBuffer newUidPooledBuffer) {

        // this is all done in a write txn so we can be sure of consistency
        // between the forward and reverse DBs

        LOGGER.trace(() ->
                LogUtil.message("Creating UID mappings for mapDefinition {}",
                        ByteBufferUtils.byteBufferInfo(mapDefinitionBuffer)));

        // get the next UID into our pooled buffer
        final ByteBuffer nextUidKeyBuffer = mapUidReverseDb.getNextUid(writeTxn, newUidPooledBuffer);

        LOGGER.trace(() -> "nextUidKeyBuffer " + ByteBufferUtils.byteBufferToHexAll(nextUidKeyBuffer));

        mapUidReverseDb.putReverseEntry(writeTxn, nextUidKeyBuffer, mapDefinitionBuffer);

        // We are not changing the buffers so can just reuse them

        LOGGER.trace(() -> "nextUidKeyBuffer " + ByteBufferUtils.byteBufferToHexAll(nextUidKeyBuffer));

        mapUidForwardDb.putForwardEntry(writeTxn, mapDefinitionBuffer, nextUidKeyBuffer);

        // this buffer is 'owned' by LMDB now but we are still in a txn so can pass it back

        LOGGER.trace(() -> "nextUidKeyBuffer " + ByteBufferUtils.byteBufferToHexAll(nextUidKeyBuffer));

        // ensure it is ready for reading again as we are returning it
        UID mapUid = UID.wrap(nextUidKeyBuffer);

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("nextUidKeyBuffer {}", ByteBufferUtils.byteBufferInfo(nextUidKeyBuffer));
            LOGGER.trace("mapDefinitionBuffer {}", ByteBufferUtils.byteBufferInfo(mapDefinitionBuffer));
            LOGGER.trace("mapDefinitionKeyBuffer {}", ByteBufferUtils.byteBufferInfo(mapDefinitionBuffer));
            LOGGER.trace("nextUidValueBuffer {}", ByteBufferUtils.byteBufferInfo(nextUidKeyBuffer));
            LOGGER.trace("nextUidValueBuffer {}", ByteBufferUtils.byteBufferInfo(nextUidKeyBuffer));
            LOGGER.trace("Creating UID mapping for {}", mapUid);
        }
        return mapUid;
    }

    public PooledByteBuffer getUidPooledByteBuffer() {
        return mapUidReverseDb.getPooledKeyBuffer();
    }

    public PooledByteBuffer getMapDefinitionPooledByteBuffer() {
        return mapUidForwardDb.getPooledKeyBuffer();
    }
}
