package stroom.lmdb2;

import stroom.lmdb.LmdbEnv;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;

import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.Txn;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

public class LmdbEnv2 implements AutoCloseable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbEnv2.class);

    private static final String DATA_FILE_NAME = "data.mdb";
    private static final String LOCK_FILE_NAME = "lock.mdb";

    private final Env<ByteBuffer> env;

    private final Path localDir;
    private final boolean isDedicatedDir;
    private final String name;
    private final Set<EnvFlags> envFlags;


    LmdbEnv2(final Env<ByteBuffer> env,
             final Path localDir,
             final boolean isDedicatedDir,
             final String name,
             final Set<EnvFlags> envFlags) {
        this.env = env;
        this.localDir = localDir;
        this.isDedicatedDir = isDedicatedDir;
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

    public Path getLocalDir() {
        return localDir;
    }

    /**
     * Deletes {@link LmdbEnv} from the filesystem if it is already closed.
     */
    public void delete() {
        if (!env.isClosed()) {
            throw new RuntimeException(("LMDB environment at {} is still open"));
        }

        LOGGER.debug("Deleting LMDB environment {} and all its contents", localDir.toAbsolutePath().normalize());

        // May be useful to see the sizes of db before they are deleted
        LOGGER.doIfDebugEnabled(this::dumpMdbFileSize);

        if (Files.isDirectory(localDir)) {
            if (isDedicatedDir) {
                // Dir dedicated to the env so can delete the whole dir
                if (!FileUtil.deleteDir(localDir)) {
                    throw new RuntimeException("Unable to delete dir: " + FileUtil.getCanonicalPath(localDir));
                }
            } else {
                // Not dedicated dir so just delete the files
                deleteEnvFile(LOCK_FILE_NAME);
                deleteEnvFile(DATA_FILE_NAME);
            }
        }
    }

    private void deleteEnvFile(final String filename) {
        final Path file = localDir.resolve(filename);
        if (Files.isRegularFile(file)) {
            try {
                LOGGER.info("Deleting file {}", file.toAbsolutePath());
                Files.delete(file);
            } catch (IOException e) {
                throw new RuntimeException("Unable to delete dir: " + FileUtil.getCanonicalPath(localDir));
            }
        } else {
            LOGGER.error("LMDB env file {} doesn't exist", file.toAbsolutePath());
        }
    }

    private void dumpMdbFileSize() {
        if (Files.isDirectory(localDir)) {

            try (Stream<Path> stream = Files.list(localDir)) {
                stream
                        .filter(path ->
                                !Files.isDirectory(path))
                        .filter(file ->
                                file.toString().toLowerCase().endsWith("data.mdb"))
                        .map(file -> {
                            try {
                                final long fileSizeBytes = Files.size(file);
                                return localDir.getFileName().resolve(file.getFileName())
                                        + " - file size: "
                                        + ModelStringUtil.formatIECByteSizeString(fileSizeBytes);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .forEach(LOGGER::debug);

            } catch (IOException e) {
                LOGGER.debug("Unable to list dir {} due to {}",
                        localDir.toAbsolutePath().normalize(), e.getMessage());
            }
        }
    }
}
