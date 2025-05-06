package stroom.planb.impl.db.state;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.state.PlanBEnv.EnvInf;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.HasPrimitiveValue;

import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Stat;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

public abstract class AbstractSchema<K, V> implements Schema<K, V> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractSchema.class);
    private static final int CURRENT_SCHEMA_VERSION = 1;

    static final String NAME = "db";
    private static final String INFO_NAME = "info_db";

    protected final PlanBEnv env;
    protected final ByteBuffers byteBuffers;
    protected final Dbi<ByteBuffer> dbi;
    protected final Dbi<ByteBuffer> infoDbi;

    public AbstractSchema(final PlanBEnv envSupport,
                          final ByteBuffers byteBuffers) {
        this.env = envSupport;
        this.byteBuffers = byteBuffers;

        dbi = env.openDbi(NAME, DbiFlags.MDB_CREATE);

        if (envSupport.isReadOnly()) {
            // Read schema version.
            infoDbi = env.openDbi(INFO_NAME, DbiFlags.MDB_CREATE);
            env.read(txn -> {
                final int schemaVersion = readSchemaVersion(txn);
                LOGGER.debug("Read schema version {}", schemaVersion);
                return null;
            });

        } else {
            // Read and write schema version.
            infoDbi = env.openDbi(INFO_NAME, DbiFlags.MDB_CREATE);
            env.write(writer -> {
                final int schemaVersion = readSchemaVersion(writer.getWriteTxn());
                LOGGER.debug("Read schema version {}", schemaVersion);
                writeSchemaVersion(writer.getWriteTxn(), CURRENT_SCHEMA_VERSION);
            });
        }
    }

    public void condense(final long condenseBeforeMs,
                         final long deleteBeforeMs) {
        // Don't condense by default.
    }

    public final long count() {
        return env.read(readTxn -> dbi.stat(readTxn).entries);
    }

    private int readSchemaVersion(final Txn<ByteBuffer> txn) {
        int version = -1;
        try {
            final ByteBuffer valueBuffer = infoDbi.get(txn, InfoKey.SCHEMA_VERSION.getByteBuffer());
            if (valueBuffer != null) {
                version = valueBuffer.getInt();
            }

        } catch (final Exception e) {
            LOGGER.debug(e::getMessage, e);
        }
        return version;
    }

    private void writeSchemaVersion(final Txn<ByteBuffer> txn, final int schemaVersion) {
        byteBuffers.useInt(schemaVersion, byteBuffer -> {
            infoDbi.put(txn, InfoKey.SCHEMA_VERSION.getByteBuffer(), byteBuffer);
        });
    }

    private enum InfoKey implements HasPrimitiveValue {
        SCHEMA_VERSION(0),
        HASH_CLASHES(1);

        private final byte primitiveValue;
        private final ByteBuffer byteBuffer;

        InfoKey(final int primitiveValue) {
            this.primitiveValue = (byte) primitiveValue;
            this.byteBuffer = ByteBuffer.allocateDirect(1);
            byteBuffer.put((byte) primitiveValue);
            byteBuffer.flip();
        }

        @Override
        public byte getPrimitiveValue() {
            return primitiveValue;
        }

        public ByteBuffer getByteBuffer() {
            return byteBuffer.duplicate();
        }
    }

    public final String getInfo() {
        try {
            final Inf inf = env.read(txn -> {
                try {
                    final EnvInf envInf = env.getInfo();

                    final Stat stat = env.read(dbi::stat);
                    final DbInf dbInf = new DbInf("db", stat);

                    return new Inf(envInf, Collections.singletonList(dbInf), env.isReadOnly(), readSchemaVersion(txn));
                } catch (final Exception e) {
                    LOGGER.debug(e::getMessage, e);
                }
                return null;
            });
            return JsonUtil.writeValueAsString(inf);
        } catch (final Exception e) {
            LOGGER.debug(e::getMessage, e);
        }
        return null;
    }

    private record Inf(EnvInf env, List<DbInf> db, boolean readOnly, int schemaVersion) {

    }

    private record DbInf(String name, Stat stat) {

    }
}
