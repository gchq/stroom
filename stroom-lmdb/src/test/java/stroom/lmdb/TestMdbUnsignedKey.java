package stroom.lmdb;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.ByteBufferPoolFactory;
import stroom.lmdb.serde.LongSerde;
import stroom.lmdb.serde.UnsignedBytes;
import stroom.lmdb.serde.UnsignedBytesInstances;
import stroom.lmdb.serde.UnsignedLong;
import stroom.lmdb.serde.UnsignedLongSerde;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lmdbjava.DbiFlags;

import java.nio.file.Path;

public class TestMdbUnsignedKey extends AbstractLmdbDbTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestMdbUnsignedKey.class);

    private final ByteBufferPool byteBufferPool = new ByteBufferPoolFactory().getByteBufferPool();

    @Test
    void test(@TempDir Path tempDir) {
        final int keyLength = 8;
        final UnsignedBytes unsignedBytes = UnsignedBytesInstances.ofLength(keyLength);

        final BasicLmdbDb<UnsignedLong, Long> lmdbDb = new BasicLmdbDb<>(
                lmdbEnv,
                byteBufferPool,
                new UnsignedLongSerde(unsignedBytes),
                LongSerde.INSTANCE,
                this.getClass().getSimpleName(),
                DbiFlags.MDB_CREATE,
                DbiFlags.MDB_UNSIGNEDKEY);

        lmdbEnv.doWithWriteTxn(writeTxn -> {
            lmdbDb.put(writeTxn,
                    UnsignedLong.of(1, unsignedBytes),
                    1L,
                    false);
            lmdbDb.put(writeTxn,
                    UnsignedLong.of(10, unsignedBytes),
                    10L,
                    false);
            lmdbDb.put(writeTxn,
                    UnsignedLong.of(100, unsignedBytes),
                    100L,
                    false);
            lmdbDb.put(writeTxn,
                    UnsignedLong.of(unsignedBytes.maxValue(), unsignedBytes),
                    unsignedBytes.maxValue(),
                    false);
        });

        lmdbDb.logDatabaseContents(LOGGER::info);
    }
}
