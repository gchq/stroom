package stroom.proxy.repo.dao.lmdb;

import stroom.bytebuffer.PooledByteBuffer;
import stroom.proxy.repo.ProxyRepoTestModule;
import stroom.proxy.repo.dao.lmdb.serde.LongSerde;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lmdbjava.Dbi;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(GuiceExtension.class)
@IncludeModule(ProxyRepoTestModule.class)
public class TestLmdb {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestLmdb.class);

    @Inject
    private LmdbEnv lmdbEnv;
    @Inject
    private LongSerde serde;

    @RepeatedTest(100)
    @Test
    void test() {
        final Dbi<ByteBuffer> dbi = lmdbEnv.openDbi("source");
        lmdbEnv.start();
        lmdbEnv.clear(dbi);
        try {
            assertThat(lmdbEnv.count(dbi)).isZero();

            final PooledByteBuffer key = serde.serialize(1L);
            final PooledByteBuffer value = serde.serialize(1L);
            lmdbEnv.write(txn -> dbi.put(txn, key.getByteBuffer(), value.getByteBuffer()));
            lmdbEnv.sync();

            assertThat(lmdbEnv.count(dbi)).isOne();
            lmdbEnv.clear(dbi);
            assertThat(lmdbEnv.count(dbi)).isZero();
        } finally {
            lmdbEnv.stop();
        }
    }
}
