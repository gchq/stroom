package stroom.proxy.repo.dao.lmdb;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.ByteBufferPoolFactory;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.lmdb.BasicLmdbDb;
import stroom.proxy.repo.ProxyRepoTestModule;
import stroom.proxy.repo.dao.lmdb.serde.LongSerde;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lmdbjava.Dbi;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(GuiceExtension.class)
@IncludeModule(ProxyRepoTestModule.class)
public class TestLmdb {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestLmdb.class);
    private BasicLmdbDb<String, String> basicLmdbDb;
    @Inject
    private LmdbEnv lmdbEnv;

//    @RepeatedTest(100)
    @Test
    void test() {
        lmdbEnv.start();
        final Db<Long, Long> db = lmdbEnv.openDb("source", new LongSerde(), new LongSerde());
        db.clear();
        try {
            assertThat(db.count()).isZero();

            db.put(1L, 1L);
//
//            final PooledByteBuffer key = serde.serialize(1L);
//            final PooledByteBuffer value = serde.serialize(1L);
//            lmdbEnv.writeAsync(txn -> dbi.put(txn, key.getByteBuffer(), value.getByteBuffer()));
////            lmdbEnv.write(txn -> lmdbEnv.commit());
//
//            lmdbEnv.sync();
////            ThreadUtil.sleep(1000);
//
////            lmdbEnv.write(txn -> {
////                        assertThat(lmdbEnv.count2(txn, dbi)).isOne();
////                    });
////            lmdbEnv.write(txn -> {
////                assertThat(lmdbEnv.count(txn, dbi)).isOne();
////            });
////            lmdbEnv.sync();
//
////            assertThat(lmdbEnv.entries(dbi)).isOne();
////            assertThat(lmdbEnv.count2(dbi)).isOne();
            assertThat(db.count()).isOne();
            db.clear();
            assertThat(db.count()).isZero();
        } finally {
            lmdbEnv.stop();
        }
    }
}
