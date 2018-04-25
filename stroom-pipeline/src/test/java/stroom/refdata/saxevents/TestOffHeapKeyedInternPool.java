package stroom.refdata.saxevents;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.SoftAssertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.stream.IntStream;

public class TestOffHeapKeyedInternPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestOffHeapKeyedInternPool.class);

    public static final long KILO_BYTES = 1024;
    public static final long MEGA_BYTES = 1024 * KILO_BYTES;
    public static final long GIGA_BYTES = 1024 * MEGA_BYTES;

    public static final long MAX_DB_SIZE = 1 * MEGA_BYTES;

    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder();

    @Rule
    public final TestName testname = new TestName();

//    @Before
//    public void setup() {
//
//    }

    @Test
    public void testCursorRanges() throws IOException {

        LOGGER.info("Using temp dir {}", tmpDir.getRoot().toPath().toAbsolutePath().toString());

        KeyedInternPool<StringValue> pool = buildPool();

        OffHeapKeyedInternPool<StringValue> offHeapPool = (OffHeapKeyedInternPool<StringValue>) pool;


        offHeapPool.forcedPut(new KeyedInternPool.Key(3,1), StringValue.of("3-1"));
        offHeapPool.forcedPut(new KeyedInternPool.Key(3,0), StringValue.of("3-0"));
        offHeapPool.forcedPut(new KeyedInternPool.Key(3,3), StringValue.of("3-3"));
        offHeapPool.forcedPut(new KeyedInternPool.Key(3,2), StringValue.of("3-2"));
        offHeapPool.forcedPut(new KeyedInternPool.Key(1,1), StringValue.of("1-1"));
        offHeapPool.forcedPut(new KeyedInternPool.Key(1,0), StringValue.of("1-0"));
        offHeapPool.forcedPut(new KeyedInternPool.Key(1,3), StringValue.of("1-3"));
        offHeapPool.forcedPut(new KeyedInternPool.Key(1,2), StringValue.of("1-2"));
        offHeapPool.forcedPut(new KeyedInternPool.Key(2,3), StringValue.of("2-3"));
        offHeapPool.forcedPut(new KeyedInternPool.Key(2,1), StringValue.of("2-1"));
        offHeapPool.forcedPut(new KeyedInternPool.Key(2,0), StringValue.of("2-0"));
        offHeapPool.forcedPut(new KeyedInternPool.Key(2,2), StringValue.of("2-2"));

        offHeapPool.dumpContents();

        offHeapPool.dumpContentsInRange(
                new KeyedInternPool.Key(1, 0),
                new KeyedInternPool.Key(2, 2));

    }

    @Test
    public void put_entries() throws IOException {

        LOGGER.info("Using temp dir {}", tmpDir.getRoot().toPath().toAbsolutePath().toString());

        KeyedInternPool<StringValue> pool = buildPool();

        //Put unique values into the the pool, out of order
        KeyedInternPool.Key val4Key1 = pool.put(StringValue.of("Value 0004"));
        KeyedInternPool.Key val3Key1 = pool.put(StringValue.of("Value 003"));
        KeyedInternPool.Key val2key1 = pool.put(StringValue.of("Value 02"));
        KeyedInternPool.Key val5Key1 = pool.put(StringValue.of("Value 00005"));
        KeyedInternPool.Key val1key1 = pool.put(StringValue.of("Value 1"));
        KeyedInternPool.Key val6Key1 = pool.put(StringValue.of("Value 000006"));

        ((OffHeapKeyedInternPool<StringValue>) pool).dumpContents();
//        Assertions.assertThat(pool.size()).isEqualTo(3);

        // call put twice more for value 2
        KeyedInternPool.Key val2Key2 = pool.put(StringValue.of("Value 02"));
        KeyedInternPool.Key val2Key3 = pool.put(StringValue.of("Value 02"));



        SoftAssertions softAssertions = new SoftAssertions();

        //the size should still be 3 as the last two we already in there
        softAssertions.assertThat(pool.size()).isEqualTo(6);
        softAssertions.assertThat(val1key1).isNotEqualTo(val2key1);
        softAssertions.assertThat(val1key1).isNotEqualTo(val3Key1);
        softAssertions.assertThat(val2key1).isNotEqualTo(val3Key1);

        softAssertions.assertThat(val2key1).isEqualTo(val2Key2);
        softAssertions.assertThat(val2key1).isEqualTo(val2Key3);

        softAssertions.assertAll();
    }


    private KeyedInternPool<StringValue> buildPool() throws IOException {
        String methodName = testname.getMethodName();
        Path dbDir = tmpDir.newFolder(methodName).toPath();

        OffHeapKeyedInternPool<StringValue> pool = new OffHeapKeyedInternPool<>(
                dbDir,
                methodName,
                MAX_DB_SIZE,
                StringValue::fromByteBuffer);

        return pool;
    }

    @Test
    public void get() {
    }

    @Test
    public void close() {
    }

    @Test
    public void testDbSize() throws IOException {

        Env<ByteBuffer> env = Env.<ByteBuffer>create()
                .setMapSize(10 * MEGA_BYTES)
                .setMaxDbs(1)
                .open(tmpDir.newFolder(testname.getMethodName()));

        Dbi<ByteBuffer> db = env.openDbi(testname.getMethodName(), DbiFlags.MDB_CREATE);

        try (final Txn<ByteBuffer> txn = env.txnWrite()) {

            db.put(txn, bb(1), bb(11));
            db.put(txn, bb(2), bb(12));
            db.put(txn, bb(3), bb(13));
            db.put(txn, bb(4), bb(14));
            Assertions.assertThat(db.stat(txn).entries).isEqualTo(4);
            txn.commit();
        }

        try (final Txn<ByteBuffer> txn = env.txnRead()) {
            Assertions.assertThat(db.stat(txn).entries).isEqualTo(4);
        }
    }


    @Test
    public void testByteBufferUtils_lowValues() {

//        KeyedInternPool.Key key = new KeyedInternPool.Key(123, 0);

//        ByteBuffer byteBuffer = key.toDirectByteBuffer();
//
//        ByteBuffer byteBuffer = ByteBuffer.allocateDirect()

        IntStream.rangeClosed(-10, 10)
                .forEach(i -> {
                    ByteBuffer byteBuffer = ByteBuffer.allocate(Integer.BYTES);
                    byteBuffer.putInt(i);
                    LOGGER.info("{} - {}", i, KeyedInternPool.Key.byteArrayToHex(byteBuffer.array()));
                });
        ByteBuffer byteBuffer = ByteBuffer.allocate(Integer.BYTES);
        byteBuffer.putInt(Integer.MAX_VALUE);
        LOGGER.info("{} - {}", Integer.MAX_VALUE, KeyedInternPool.Key.byteArrayToHex(byteBuffer.array()));


    }
    static ByteBuffer bb(final int value) {
        final ByteBuffer bb = ByteBuffer.allocateDirect(Integer.BYTES);
        bb.putInt(value).flip();
        return bb;
    }

    private static class StringValue extends KeyedInternPool.AbstractKeyedInternPoolValue {

        public static int fixedHash = 0;
        private final String value;

        StringValue(final String value) {
            this.value = value;
        }

        static StringValue of(String value) {
            return new StringValue(value);
        }

        @Override
        public boolean equals(final Object obj) {
            return value.equals(obj);
        }

        @Override
        public int hashCode() {
//            return fixedHash++;
            return value.hashCode();
        }

        @Override
        public byte[] toBytes() {
            return value.getBytes(StandardCharsets.UTF_8);
        }

        static StringValue fromByteBuffer(final ByteBuffer byteBuffer) {
            return new StringValue(StandardCharsets.UTF_8.decode(byteBuffer).toString());
        }

        @Override
        public String toString() {
            return "StringValue{" +
                    "value='" + value + '\'' +
                    '}';
        }
    }
}