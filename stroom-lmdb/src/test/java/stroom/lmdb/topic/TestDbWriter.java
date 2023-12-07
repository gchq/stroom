package stroom.lmdb.topic;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.ByteBufferPoolFactory;
import stroom.lmdb.AbstractLmdbDbTest;
import stroom.lmdb.BasicLmdbDb;
import stroom.lmdb.LmdbEnv;
import stroom.lmdb.LmdbEnvFactory;
import stroom.lmdb.LmdbLibraryConfig;
import stroom.lmdb.serde.LongSerde;
import stroom.lmdb.serde.StringSerde;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.io.PathCreator;
import stroom.util.io.TempDirProvider;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lmdbjava.EnvFlags;

import java.util.concurrent.CountDownLatch;
import javax.inject.Provider;

class TestDbWriter extends AbstractLmdbDbTest {

    private final ByteBufferPool byteBufferPool = new ByteBufferPoolFactory().getByteBufferPool();
    private DbWriter dbWriter;
    private BasicLmdbDb<Long, String> basicLmdbDb;

    @Override
    protected LmdbEnv buildLmdbEnv(final PathCreator pathCreator,
                                   final TempDirProvider tempDirProvider,
                                   final Provider<LmdbLibraryConfig> lmdbLibraryConfig,
                                   final EnvFlags[] envFlags) {
        return new LmdbEnvFactory(pathCreator, tempDirProvider, LmdbLibraryConfig::new)
                .builder(getDbDir())
                .withMapSize(getMaxSizeBytes())
                .withMaxDbCount(10)
                .withEnvFlags(envFlags)
                .setIsReaderBlockedByWriter(false)
                .build();
    }

    @BeforeEach
    void setup() {
        basicLmdbDb = new BasicLmdbDb<>(
                lmdbEnv,
                byteBufferPool,
                new LongSerde(),
                new StringSerde(),
                "MyBasicLmdb");
        dbWriter = new DbWriter(lmdbEnv);
    }

    void shutdown() {
        dbWriter.shutdown();
    }

    @Test
    void putSync() throws InterruptedException {
        for (long i = 0; i < 10; i++) {
            final long iCopy = i;
            dbWriter.putSync(false, writeTxn -> {
                basicLmdbDb.put(writeTxn,
                        iCopy,
                        "val" + iCopy,
                        false,
                        true);
            });
        }
        dbWriter.commitSync();
        Assertions.assertThat(basicLmdbDb.getEntryCount())
                .isEqualTo(10);
    }

    @Test
    void putASync() throws InterruptedException {
        for (long i = 0; i < 10; i++) {
            final long iCopy = i;
            dbWriter.putAsync(false, writeTxn -> {
                ThreadUtil.sleepIgnoringInterrupts(20);
                basicLmdbDb.put(writeTxn,
                        iCopy,
                        "val" + iCopy,
                        false,
                        true);
            });
        }
        Assertions.assertThat(basicLmdbDb.getEntryCount())
                .isLessThan(10);

        dbWriter.commitSync();
        Assertions.assertThat(basicLmdbDb.getEntryCount())
                .isEqualTo(10);
    }

    @Test
    void putAsync() {
    }

    @Test
    void commitSync() {
    }

    @Test
    void commitAsync() {
    }
}