package stroom.lmdb2;

import stroom.lmdb.LmdbConfig;
import stroom.util.io.ByteSize;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LmdbEnv implements AutoCloseable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbEnv.class);

    private final Env<ByteBuffer> env;
    private final LmdbEnvDir lmdbEnvDir;
    private final ByteSize maxStoreSize;
    private final int maxDbs;
    private final int maxReaders;
    private final Set<EnvFlags> envFlags;
    private final LmdbErrorHandler errorHandler;

    private WriteTxn writeTxn;

    LmdbEnv(final LmdbEnvDir lmdbEnvDir,
            final ByteSize maxStoreSize,
            final int maxDbs,
            final int maxReaders,
            final Set<EnvFlags> envFlags,
            final LmdbErrorHandler errorHandler) {
        this.lmdbEnvDir = lmdbEnvDir;
        this.maxStoreSize = maxStoreSize;
        this.maxDbs = maxDbs;
        this.maxReaders = maxReaders;
        this.envFlags = envFlags;
        this.errorHandler = errorHandler;

        try {
            final Env.Builder<ByteBuffer> builder = Env.create()
                    .setMapSize(maxStoreSize.getBytes())
                    .setMaxDbs(maxDbs)
                    .setMaxReaders(maxReaders);

            LOGGER.debug("Creating LMDB environment in dir {}, maxSize: {}, maxDbs {}, maxReaders {}, "
                         + "envFlags {}",
                    lmdbEnvDir.toString(),
                    maxStoreSize,
                    maxDbs,
                    maxReaders,
                    envFlags);

            final EnvFlags[] envFlagsArr = envFlags.toArray(new EnvFlags[0]);
            env = builder.open(lmdbEnvDir.getEnvDir().toFile(), envFlagsArr);
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message(
                    "Error creating LMDB env at {}: {}",
                    lmdbEnvDir.toString(), e.getMessage()), e);
        }
    }

    public ByteSize getMaxStoreSize() {
        return maxStoreSize;
    }

    /**
     * Open the named DB, creating it if it doesn't exist.
     * <p>
     * Don't use the un-named DB if you are also using named DBs as the
     * un-named DB is used internally by LMDB to store DB names.
     * </p>
     *
     * @param dbName The name of the DB or null for the un-named DB.
     */
    public LmdbDb openDb(final String dbName) {
        return openDb(dbName, DbiFlags.MDB_CREATE);
    }

    /**
     * Open the named DB. If flags does not contain {@link DbiFlags#MDB_CREATE} it will
     * error if the DB does not already exist.
     * <p>
     * Don't use the un-named DB if you are also using named DBs as the
     * un-named DB is used internally by LMDB to store DB names.
     * </p>
     *
     * @param dbName The name of the DB or null for the un-named DB.
     */
    public LmdbDb openDb(final String dbName, final DbiFlags... flags) {
        return new LmdbDb(env, dbName, Set.of(flags), errorHandler);
    }

    /**
     * @param dbName The name of the DB or null for the un-named DB.
     * @return True if a DB with the supplied name exists.
     */
    public boolean hasDb(final String dbName) {
        Objects.requireNonNull(dbName);
        final byte[] dbNameBytes = LmdbDb.convertDbName(dbName);
        return env.getDbiNames()
                .stream()
                .anyMatch(bytes -> Arrays.equals(bytes, dbNameBytes));
    }

    /**
     * @return The set of DB names in this {@link LmdbEnv}.
     * Does not include the un-named DB.
     */
    public Set<String> getDbNames() {
        return env.getDbiNames()
                .stream()
                .map(LmdbDb::convertDbName)
                .collect(Collectors.toSet());
    }

    public synchronized WriteTxn writeTxn() {
        // Ensure there is only ever a single write txn.
        if (writeTxn == null) {
            writeTxn = new WriteTxn(env, errorHandler);
        } else {
            // Make sure it is still the same thread asking for the write txn.
            writeTxn.check();
        }
        return writeTxn;
    }

    public ReadTxn readTxn() {
        return new ReadTxn(env, errorHandler);
    }

    /**
     * Synchronise writes to ensure only a single thread can write.
     *
     * @param consumer Consumer for the write transaction.
     */
    public synchronized void write(final Consumer<WriteTxn> consumer) {
        try (final WriteTxn txn = writeTxn()) {
            consumer.accept(txn);
        } catch (final RuntimeException e) {
            errorHandler.error(e);
            throw e;
        }
    }

    public void read(final Consumer<ReadTxn> consumer) {
        try (final ReadTxn txn = readTxn()) {
            consumer.accept(txn);
        } catch (final RuntimeException e) {
            errorHandler.error(e);
            throw e;
        }
    }

    public <R> R readResult(final Function<ReadTxn, R> function) {
        try (final ReadTxn txn = new ReadTxn(env, errorHandler)) {
            return function.apply(txn);
        } catch (final RuntimeException e) {
            errorHandler.error(e);
            throw e;
        }
    }

    @Override
    public void close() {
        try {
            env.close();
        } catch (final RuntimeException e) {
            errorHandler.error(e);
            throw e;
        }
    }

    public boolean isClosed() {
        return env.isClosed();
    }

    public LmdbEnvDir getDir() {
        return lmdbEnvDir;
    }

    /**
     * Deletes {@link stroom.lmdb.LmdbEnv} from the filesystem if it is already closed.
     */
    public void delete() {
        try {
            if (!env.isClosed()) {
                throw new RuntimeException(("LMDB environment at {} is still open"));
            }

            lmdbEnvDir.delete();
        } catch (final RuntimeException e) {
            errorHandler.error(e);
            throw e;
        }
    }

    @Override
    public String toString() {
        return "Env{" +
               "lmdbEnvDir=" + lmdbEnvDir +
               ", maxStoreSize=" + maxStoreSize +
               ", maxDbs=" + maxDbs +
               ", maxReaders=" + maxReaders +
               ", envFlags=" + envFlags +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private LmdbEnvDir lmdbEnvDir;
        private ByteSize maxStoreSize = LmdbConfig.DEFAULT_MAX_STORE_SIZE;
        private int maxDbs = 1;
        private int maxReaders = LmdbConfig.DEFAULT_MAX_READERS;
        private final Set<EnvFlags> envFlags = EnumSet.noneOf(EnvFlags.class);
        private LmdbErrorHandler errorHandler = e -> LOGGER.error(e::getMessage, e);


        //        protected boolean isReadAheadEnabled = LmdbConfig.DEFAULT_IS_READ_AHEAD_ENABLED;
//        protected boolean isReaderBlockedByWriter = LmdbConfig.DEFAULT_IS_READER_BLOCKED_BY_WRITER;

        private Builder() {
        }

        public Builder config(final LmdbConfig lmdbConfig) {
            this.maxStoreSize = lmdbConfig.getMaxStoreSize();
            this.maxReaders = lmdbConfig.getMaxReaders();
            if (!lmdbConfig.isReadAheadEnabled()) {
                envFlags.add(EnvFlags.MDB_NORDAHEAD);
            }
//            this.isReaderBlockedByWriter = lmdbConfig.isReaderBlockedByWriter();
            return this;
        }

        public Builder lmdbEnvDir(final LmdbEnvDir lmdbEnvDir) {
            this.lmdbEnvDir = lmdbEnvDir;
            return this;
        }

        public Builder maxStoreSize(final ByteSize maxStoreSize) {
            this.maxStoreSize = maxStoreSize;
            return this;
        }

        public Builder maxDbs(final int maxDbs) {
            this.maxDbs = maxDbs;
            return this;
        }

        public Builder maxReaders(final int maxReaders) {
            this.maxReaders = maxReaders;
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

        public Builder errorHandler(final LmdbErrorHandler errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        public LmdbEnv build() {
            return new LmdbEnv(
                    lmdbEnvDir,
                    maxStoreSize,
                    maxDbs,
                    maxReaders,
                    envFlags,
                    errorHandler);
        }
    }
}
