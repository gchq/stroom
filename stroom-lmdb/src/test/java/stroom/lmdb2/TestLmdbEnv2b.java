package stroom.lmdb2;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.lmdb.LmdbConfig;
import stroom.util.io.ByteSize;
import stroom.util.shared.AbstractConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lmdbjava.Dbi;
import org.lmdbjava.PutFlags;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TestLmdbEnv2b {

    final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();

    @Test
    void testWriteTxn(@TempDir Path tempDir) {
        final LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(tempDir, true);

        try (final LmdbEnv2 lmdbEnv = getLmdbEnv(lmdbEnvDir)) {
            final Dbi<ByteBuffer> dbi = lmdbEnv.openDbi("foo");

            final WriteTxn writeTxn1 = lmdbEnv.txnWrite();
            writeTxn1.get();

            writeTxn1.commit();
            writeTxn1.commit();

            put(dbi, writeTxn1.get(), "foo", "bar");
            assertThat(get(dbi, writeTxn1.get(), "foo"))
                    .isEqualTo("bar");

            writeTxn1.commit();
            writeTxn1.commit();

            writeTxn1.close();
            writeTxn1.close();
            writeTxn1.commit();
            final Txn<ByteBuffer> wTxn1a = writeTxn1.get();
            final Txn<ByteBuffer> wTxn1b = writeTxn1.get();
            assertThat(wTxn1a)
                    .isSameAs(wTxn1b);
            writeTxn1.commit();

            writeTxn1.commit();
            writeTxn1.close();
        }
    }

    @Test
    void test2(@TempDir Path tempDir) {
        final LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(tempDir, true);

        try (final LmdbEnv2 lmdbEnv = getLmdbEnv(lmdbEnvDir)) {
            final Dbi<ByteBuffer> dbi = lmdbEnv.openDbi("foo");

            final WriteTxn writeTxn1 = lmdbEnv.txnWrite();

            put(dbi, writeTxn1.get(), "foo", "bar");
            assertThat(get(dbi, writeTxn1.get(), "foo"))
                    .isEqualTo("bar");
        }
    }

    private static LmdbEnv2 getLmdbEnv(final LmdbEnvDir lmdbEnvDir) {
        final LmdbEnv2 lmdbEnv = LmdbEnv2.builder()
                .name("test")
                .maxDbCount(1)
                .config(new MyLmdbConfig())
                .lmdbEnvDir(lmdbEnvDir)
                .build();
        return lmdbEnv;
    }

    private boolean put(final Dbi<ByteBuffer> dbi,
                        final Txn<ByteBuffer> txn,
                        final String key,
                        final String val,
                        final PutFlags... putFlags) {
        final ByteBuffer keyBuf = byteBufferFactory.acquire(100);
        final ByteBuffer valBuf = byteBufferFactory.acquire(100);
        try {
            keyBuf.clear();
            keyBuf.put(key.getBytes(StandardCharsets.UTF_8));
            keyBuf.flip();
            valBuf.clear();
            valBuf.put(val.getBytes(StandardCharsets.UTF_8));
            valBuf.flip();
            return dbi.put(txn, keyBuf, valBuf, putFlags);
        } finally {
            byteBufferFactory.release(keyBuf);
            byteBufferFactory.release(valBuf);
        }
    }

    private String get(final Dbi<ByteBuffer> dbi,
                       final Txn<ByteBuffer> txn,
                       final String key) {
        final ByteBuffer keyBuf = byteBufferFactory.acquire(100);
        try {
            keyBuf.clear();
            keyBuf.put(key.getBytes(StandardCharsets.UTF_8));
            keyBuf.flip();

            final ByteBuffer valBuf = dbi.get(txn, keyBuf);
            return StandardCharsets.UTF_8.decode(valBuf).toString();
        } finally {
            byteBufferFactory.release(keyBuf);
        }
    }


    // --------------------------------------------------------------------------------


    private static class MyLmdbConfig extends AbstractConfig implements LmdbConfig {

        @Override
        public String getLocalDir() {
            // Not used by LmdbEnv2.build()
            return null;
        }

        @Override
        public int getMaxReaders() {
            return 10;
        }

        @Override
        public ByteSize getMaxStoreSize() {
            return ByteSize.ofMebibytes(10);
        }

        @Override
        public boolean isReadAheadEnabled() {
            return true;
        }

        @Override
        public boolean isReaderBlockedByWriter() {
            // Not used by LmdbEnv2.build()
            return false;
        }
    }
}
