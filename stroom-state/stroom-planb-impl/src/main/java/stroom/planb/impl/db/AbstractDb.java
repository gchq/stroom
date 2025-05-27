package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.PlanBEnv.EnvInf;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.HasPrimitiveValue;

import org.lmdbjava.CopyFlags;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.PutFlags;
import org.lmdbjava.Stat;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public abstract class AbstractDb<K, V> implements Db<K, V> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractDb.class);
    private static final int CURRENT_SCHEMA_VERSION = 1;

    private static final String NAME = "db";
    private static final String INFO_NAME = "info_db";

    protected final PutFlags[] putFlags;

    protected final PlanBEnv env;
    protected final ByteBuffers byteBuffers;
    protected final Dbi<ByteBuffer> dbi;
    protected final Dbi<ByteBuffer> infoDbi;

    public AbstractDb(final PlanBEnv env,
                      final ByteBuffers byteBuffers,
                      final Boolean overwrite,
                      final HashClashCommitRunnable hashClashCommitRunnable) {
        this.env = env;
        this.byteBuffers = byteBuffers;

        dbi = env.openDbi(NAME, DbiFlags.MDB_CREATE);

        if (env.isReadOnly()) {
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
                hashClashCommitRunnable.setHashClashes(readHashClashes(writer.getWriteTxn()));
            });

            hashClashCommitRunnable.setRunnable(txn -> writeHashClashes(txn, hashClashCommitRunnable.getHashClashes()));
        }

        this.putFlags = overwrite
                ? new PutFlags[]{}
                : new PutFlags[]{PutFlags.MDB_NOOVERWRITE};
    }

    @Override
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

    private int readHashClashes(final Txn<ByteBuffer> txn) {
        int hashClashes = -1;
        try {
            final ByteBuffer valueBuffer = infoDbi.get(txn, InfoKey.HASH_CLASHES.getByteBuffer());
            if (valueBuffer != null) {
                hashClashes = valueBuffer.getInt();
            }

        } catch (final Exception e) {
            LOGGER.debug(e::getMessage, e);
        }
        return hashClashes;
    }

    private void writeHashClashes(final Txn<ByteBuffer> txn, final int schemaVersion) {
        byteBuffers.useInt(schemaVersion, byteBuffer -> {
            infoDbi.put(txn, InfoKey.HASH_CLASHES.getByteBuffer(), byteBuffer);
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

    @Override
    public LmdbWriter createWriter() {
        return env.createWriter();
    }

    @Override
    public void write(final Consumer<LmdbWriter> consumer) {
        try (final LmdbWriter writer = createWriter()) {
            consumer.accept(writer);
        }
    }

    @Override
    public void compact(final Path destination) {
        env.copy(destination.toFile(), CopyFlags.MDB_CP_COMPACT);
    }

    @Override
    public void lock(final Runnable runnable) {
        env.lock(runnable);
    }

    @Override
    public void close() {
        env.close();
    }

    public final String getInfoString() {
        final Inf inf = getInfo();
        if (inf == null) {
            return null;
        }
        return JsonUtil.writeValueAsString(inf);
    }

    public final Inf getInfo() {
        try {
            return env.read(txn -> {
                try {
                    final EnvInf envInf = env.getInfo();
                    final Stat stat = dbi.stat(txn);
                    final DbInf dbInf = new DbInf("db", stat);

                    return new Inf(
                            envInf,
                            Collections.singletonList(dbInf),
                            env.isReadOnly(),
                            readSchemaVersion(txn),
                            readHashClashes(txn));
                } catch (final Exception e) {
                    LOGGER.debug(e::getMessage, e);
                }
                return null;
            });
        } catch (final Exception e) {
            LOGGER.debug(e::getMessage, e);
        }
        return null;
    }

    public record Inf(EnvInf env, List<DbInf> db, boolean readOnly, int schemaVersion, int hashClashes) {

    }

    public record DbInf(String name, Stat stat) {

    }
}
