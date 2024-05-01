package stroom.lmdb2;

import stroom.lmdb.LmdbConfig;
import stroom.lmdb.LmdbEnv;
import stroom.util.io.ByteSize;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;

public class LmdbEnv2 implements AutoCloseable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbEnv2.class);

    private final Env<ByteBuffer> env;
    private final LmdbEnvDir lmdbEnvDir;
    private final String name;
    private final Set<EnvFlags> envFlags;

    LmdbEnv2(final Env<ByteBuffer> env,
             final LmdbEnvDir lmdbEnvDir,
             final String name,
             final Set<EnvFlags> envFlags) {
        this.env = env;
        this.lmdbEnvDir = lmdbEnvDir;
        this.name = name;
        this.envFlags = envFlags;
    }

    public Dbi<ByteBuffer> openDbi(final String dbName) {
        return openDbi(dbName, DbiFlags.MDB_CREATE);
    }

    public Dbi<ByteBuffer> openDbi(final String dbName, final DbiFlags... flags) {
        final byte[] nameBytes = dbName == null
                ? null
                : dbName.getBytes(UTF_8);
        return env.openDbi(nameBytes, flags);
    }

    /**
     * Obtain a read-only transaction.
     *
     * @return a read-only transaction
     */
    public Txn<ByteBuffer> txnRead() {
        return env.txnRead();
    }

    /**
     * Obtain a read-write transaction.
     *
     * @return a read-write transaction
     */
    public WriteTxn txnWrite() {
        return new WriteTxn(env);
    }

    public void read(final Consumer<Txn<ByteBuffer>> consumer) {
        try (final Txn<ByteBuffer> txn = env.txnRead()) {
            consumer.accept(txn);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    public <R> R readResult(final Function<Txn<ByteBuffer>, R> function) {
        try (final Txn<ByteBuffer> txn = env.txnRead()) {
            return function.apply(txn);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    public long count(final Dbi<ByteBuffer> dbi) {
        return readResult(txn -> dbi.stat(txn).entries);
    }

    @Override
    public void close() {
        env.close();
    }

    public boolean isClosed() {
        return env.isClosed();
    }

    public LmdbEnvDir getDir() {
        return lmdbEnvDir;
    }

    /**
     * Deletes {@link LmdbEnv} from the filesystem if it is already closed.
     */
    public void delete() {
        if (!env.isClosed()) {
            throw new RuntimeException(("LMDB environment at {} is still open"));
        }

        lmdbEnvDir.delete();
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private LmdbEnvDir lmdbEnvDir;
        private final Set<EnvFlags> envFlags = EnumSet.noneOf(EnvFlags.class);

        protected int maxReaders = LmdbConfig.DEFAULT_MAX_READERS;
        protected ByteSize maxStoreSize = LmdbConfig.DEFAULT_MAX_STORE_SIZE;
        protected int maxDbs = 1;
        protected boolean isReadAheadEnabled = LmdbConfig.DEFAULT_IS_READ_AHEAD_ENABLED;
        protected boolean isReaderBlockedByWriter = LmdbConfig.DEFAULT_IS_READER_BLOCKED_BY_WRITER;
        protected String name = null;

        private Builder() {
        }

        public Builder config(final LmdbConfig lmdbConfig) {
            this.maxReaders = lmdbConfig.getMaxReaders();
            this.maxStoreSize = lmdbConfig.getMaxStoreSize();
            this.isReadAheadEnabled = lmdbConfig.isReadAheadEnabled();
            this.isReaderBlockedByWriter = lmdbConfig.isReaderBlockedByWriter();
            return this;
        }

        public Builder lmdbEnvDir(final LmdbEnvDir lmdbEnvDir) {
            this.lmdbEnvDir = lmdbEnvDir;
            return this;
        }

        public Builder maxDbCount(final int maxDbCount) {
            this.maxDbs = maxDbCount;
            return this;
        }

        public Builder addEnvFlag(final EnvFlags envFlag) {
            this.envFlags.add(envFlag);
            return this;
        }

        public Builder envFlags(final EnvFlags... envFlags) {
            this.envFlags.addAll(Arrays.asList(envFlags));
            return this;
        }

        public Builder envFlags(final Collection<EnvFlags> envFlags) {
            this.envFlags.addAll(envFlags);
            return this;
        }

        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        public LmdbEnv2 build() {
            final Env<ByteBuffer> env;
            try {
                final Env.Builder<ByteBuffer> builder = Env.create()
                        .setMapSize(maxStoreSize.getBytes())
                        .setMaxDbs(maxDbs)
                        .setMaxReaders(maxReaders);

                if (envFlags.contains(EnvFlags.MDB_NORDAHEAD) && isReadAheadEnabled) {
                    throw new RuntimeException("Can't set isReadAheadEnabled to true and add flag "
                            + EnvFlags.MDB_NORDAHEAD);
                }

                envFlags.add(EnvFlags.MDB_NOTLS);
                if (!isReadAheadEnabled) {
                    envFlags.add(EnvFlags.MDB_NORDAHEAD);
                }

                LOGGER.debug("Creating LMDB environment in dir {}, maxSize: {}, maxDbs {}, maxReaders {}, "
                                + "isReadAheadEnabled {}, isReaderBlockedByWriter {}, envFlags {}",
                        lmdbEnvDir.toString(),
                        maxStoreSize,
                        maxDbs,
                        maxReaders,
                        isReadAheadEnabled,
                        isReaderBlockedByWriter,
                        envFlags);

                final EnvFlags[] envFlagsArr = envFlags.toArray(new EnvFlags[0]);
                env = builder.open(lmdbEnvDir.getEnvDir().toFile(), envFlagsArr);
            } catch (Exception e) {
                throw new RuntimeException(LogUtil.message(
                        "Error creating LMDB env at {}: {}",
                        lmdbEnvDir.toString(), e.getMessage()), e);
            }
            return new LmdbEnv2(env, lmdbEnvDir, name, envFlags);
        }
    }
}
