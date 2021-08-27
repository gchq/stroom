package stroom.query.common.v2;

import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

public class LmdbEnvironment {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbEnvironment.class);

    private final Path path;
    private final Env<ByteBuffer> env;
    private final Semaphore activeReadTransactionsSemaphore;

    public LmdbEnvironment(final Path path,
                           final Env<ByteBuffer> env) {
        this.path = path;
        this.env = env;
        // Limit concurrent readers java side to ensure we don't get a max readers reached error
        final int maxReaders = env.info().maxReaders;
        LOGGER.debug("Initialising activeReadTransactionsSemaphore with {} permits", maxReaders);
        this.activeReadTransactionsSemaphore = new Semaphore(maxReaders);
    }

    public Dbi<ByteBuffer> openDbi(final String name) {
        LOGGER.debug(() -> "Opening LMDB database with name: " + name);
        final byte[] nameBytes = toBytes(name);
        try {
            return env.openDbi(nameBytes, DbiFlags.MDB_CREATE);
        } catch (final Exception e) {
            final String message = LogUtil.message("Error opening LMDB database '{}' in '{}' ({})",
                    name,
                    FileUtil.getCanonicalPath(path),
                    e.getMessage());

            LOGGER.error(message, e);
            throw new RuntimeException(message, e);
        }
    }

    Txn<ByteBuffer> txnWrite() {
        LOGGER.debug("About to open write tx");
        return env.txnWrite();
    }

    void doWithReadTxn(final Consumer<Txn<ByteBuffer>> work) {
        try {
            LOGGER.debug("About to acquire permit");
            activeReadTransactionsSemaphore.acquire();
            LOGGER.debug("Permit acquired");

            try (final Txn<ByteBuffer> txn = env.txnRead()) {
                LOGGER.debug("Performing work with read txn");
                work.accept(txn);
            } catch (RuntimeException e) {
                throw new RuntimeException(LogUtil.message(
                        "Error performing work in read transaction: {}",
                        e.getMessage()), e);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted", e);
        } finally {
            LOGGER.debug("Releasing permit");
            activeReadTransactionsSemaphore.release();
        }
    }

    void close() {
        env.close();
    }

    void delete() {
        if (!FileUtil.deleteDir(path)) {
            throw new RuntimeException("Unable to delete dir: " + FileUtil.getCanonicalPath(path));
        }
    }

    private byte[] toBytes(final String string) {
        return string.getBytes(StandardCharsets.UTF_8);
    }
}
