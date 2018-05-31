package stroom.refdata.offheapstore;

import com.google.common.base.Preconditions;
import org.apache.hadoop.hbase.util.ByteBufferUtils;
import org.lmdbjava.Env;
import org.lmdbjava.Txn;
import stroom.refdata.lmdb.LmdbUtils;
import stroom.refdata.lmdb.serde.Serde;
import stroom.refdata.offheapstore.databases.MapUidForwardDb;
import stroom.refdata.offheapstore.databases.MapUidReverseDb;

import java.nio.ByteBuffer;
import java.util.Optional;

public class MapDefinitionUIDStore {

    private final MapUidForwardDb mapUidForwardDb;
    private final MapUidReverseDb mapUidReverseDb;
    private final Env<ByteBuffer> lmdbEnv;

    MapDefinitionUIDStore(final MapUidForwardDb mapUidForwardDb,
                          final MapUidReverseDb mapUidReverseDb,
                          final Env<ByteBuffer> lmdbEnv) {
        this.mapUidForwardDb = mapUidForwardDb;
        this.mapUidReverseDb = mapUidReverseDb;
        this.lmdbEnv = lmdbEnv;
    }


    Optional<UID> getId(final Txn<ByteBuffer> txn, final MapDefinition mapDefinition) {
        return null;
    }

    public UID getOrCreateId(final Txn<ByteBuffer> writeTxn, final MapDefinition mapDefinition) {
        Preconditions.checkArgument(!writeTxn.isReadOnly(), "Must be a write transaction");

        final Serde<MapDefinition> mapDefinitionSerde = mapUidForwardDb.getKeySerde();
        final Serde<UID> uidSerde = mapUidForwardDb.getValueSerde();

        final ByteBuffer mapDefinitionBuffer = LmdbUtils.buildDbKeyBuffer(lmdbEnv, mapDefinition, mapDefinitionSerde);

        mapUidForwardDb.getKeySerde().serialize(mapDefinitionBuffer, mapDefinition);

        // see if we already have a UID for this mapDefinition, if not create the pair
        // of entries
        return mapUidForwardDb.getUID(writeTxn, mapDefinitionBuffer)
                .map(uidSerde::deserialize)
                .orElseGet(() ->
                        createForwardReversePair(writeTxn, mapDefinitionBuffer));
    }

    private UID createForwardReversePair(final Txn<ByteBuffer> writeTxn, final ByteBuffer mapDefinitionBuffer) {
        // this is done in a write txn so we can be sure of consistency between the forward and reverse DBs

        // get the next UID value
        long nextId = mapUidReverseDb.getNextId(writeTxn);

        // put the reverse entry

        // put the forward entry


        return null;
    }

    Optional<String> getName(UID uid) {
        return null;
    }

}
