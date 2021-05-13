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

public class LmdbEnvironment {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbEnvironment.class);

    private final Path path;
    private final Env<ByteBuffer> env;

    public LmdbEnvironment(final Path path,
                           final Env<ByteBuffer> env) {
        this.path = path;
        this.env = env;
    }

    public Dbi<ByteBuffer> openDbi(final String name) {
        LOGGER.debug(() -> "Opening LMDB database with name: " + name);
        final byte[] nameBytes = toBytes(name);
        try {
            final Dbi<ByteBuffer> dbi = env.openDbi(nameBytes, DbiFlags.MDB_CREATE);
            return dbi;
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
        return env.txnWrite();
    }

    Txn<ByteBuffer> txnRead() {
        return env.txnRead();
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
