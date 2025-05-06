package stroom.planb.impl.db.state;

import stroom.lmdb.LmdbConfig;
import stroom.lmdb2.LmdbEnvDir;
import stroom.planb.impl.db.LmdbWriter;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.EnvInfo;
import org.lmdbjava.Stat;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

public class PlanBEnv {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PlanBEnv.class);

    private static final int CONCURRENT_READERS = 10;
    private final Semaphore concurrentReaderSemaphore;
    protected final Env<ByteBuffer> env;
    private final ReentrantLock writeTxnLock = new ReentrantLock();
    private final ReentrantLock dbCommitLock = new ReentrantLock();
    private final boolean readOnly;
    private final Runnable commitRunnable;

    public PlanBEnv(final Path path,
                    final Long mapSize,
                    final int maxDbs,
                    final boolean readOnly,
                    final Runnable commitRunnable) {
        final LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(path, true);
        this.readOnly = readOnly;
        this.commitRunnable = commitRunnable;
        concurrentReaderSemaphore = new Semaphore(CONCURRENT_READERS);

        if (readOnly) {
            LOGGER.info(() -> "Opening: " + path);
        } else {
            LOGGER.info(() -> "Creating: " + path);
        }

        final Env.Builder<ByteBuffer> builder = Env.create()
                .setMapSize(mapSize == null
                        ? LmdbConfig.DEFAULT_MAX_STORE_SIZE.getBytes()
                        : mapSize)
                .setMaxDbs(maxDbs)
                .setMaxReaders(CONCURRENT_READERS);

        if (readOnly) {
            env = builder.open(lmdbEnvDir.getEnvDir().toFile(),
                    EnvFlags.MDB_NOTLS,
                    EnvFlags.MDB_NOLOCK,
                    EnvFlags.MDB_RDONLY_ENV);
        } else {
            env = builder.open(lmdbEnvDir.getEnvDir().toFile(),
                    EnvFlags.MDB_NOTLS);
        }
    }

    public Dbi<ByteBuffer> openDbi(final String name, final DbiFlags... flags) {
        return env.openDbi(name.getBytes(StandardCharsets.UTF_8), flags);
    }

    public final LmdbWriter createWriter() {
        return new LmdbWriter(env, dbCommitLock, commitRunnable, writeTxnLock);
    }

    public final <T> T write(final Function<LmdbWriter, T> function) {
        try (final LmdbWriter writer = createWriter()) {
            return function.apply(writer);
        }
    }

    public final void write(final Consumer<LmdbWriter> consumer) {
        try (final LmdbWriter writer = createWriter()) {
            consumer.accept(writer);
        }
    }

    public final void lock(final Runnable runnable) {
        dbCommitLock.lock();
        try {
            runnable.run();
        } finally {
            dbCommitLock.unlock();
        }
    }

    public final <R> R read(final Function<Txn<ByteBuffer>, R> function) {
        try {
            concurrentReaderSemaphore.acquire();
            try {
                try (final Txn<ByteBuffer> readTxn = env.txnRead()) {
                    return function.apply(readTxn);
                }
            } finally {
                concurrentReaderSemaphore.release();
            }
        } catch (final InterruptedException e) {
            LOGGER.error(e::getMessage, e);
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException(e);
        }
    }

    public final boolean isReadOnly() {
        return readOnly;
    }

    public final void close() {
        env.close();
    }

    public EnvInf getInfo() {
        final List<String> dbNames = env
                .getDbiNames()
                .stream()
                .map(String::new)
                .sorted()
                .toList();
        return new EnvInf(env.stat(), env.info(), env.getMaxKeySize(), dbNames);
    }

    public record EnvInf(Stat stat, EnvInfo envInfo, int maxKeySize, List<String> dbNames) {

    }
}
