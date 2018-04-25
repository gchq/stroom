package stroom.refdata.saxevents;

import org.assertj.core.api.SoftAssertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

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

        OffHeapKeyedInternPool<StringValue> pool = buildPool();

        pool.forcedPut(new Key(3,1), StringValue.of("3-1"));
        pool.forcedPut(new Key(3,0), StringValue.of("3-0"));
        pool.forcedPut(new Key(3,3), StringValue.of("3-3"));
        pool.forcedPut(new Key(3,2), StringValue.of("3-2"));
        pool.forcedPut(new Key(1,1), StringValue.of("1-1"));
        pool.forcedPut(new Key(1,0), StringValue.of("1-0"));
        pool.forcedPut(new Key(1,3), StringValue.of("1-3"));
        pool.forcedPut(new Key(1,2), StringValue.of("1-2"));
        pool.forcedPut(new Key(2,3), StringValue.of("2-3"));
        pool.forcedPut(new Key(2,1), StringValue.of("2-1"));
        pool.forcedPut(new Key(2,0), StringValue.of("2-0"));
        pool.forcedPut(new Key(2,2), StringValue.of("2-2"));

        pool.dumpContents();

        pool.dumpContentsInRange(
                new Key(1, 0),
                new Key(2, 2));

    }

    @Test
    public void put_entries() throws IOException {

        LOGGER.info("Using temp dir {}", tmpDir.getRoot().toPath().toAbsolutePath().toString());

        OffHeapKeyedInternPool<StringValue> pool = buildPool();

        //Put unique values into the the pool, out of order
        Key val4Key1 = pool.put(StringValue.of("Value 0004"));
        Key val3Key1 = pool.put(StringValue.of("Value 003"));
        Key val2key1 = pool.put(StringValue.of("Value 02"));
        Key val5Key1 = pool.put(StringValue.of("Value 00005"));
        Key val1key1 = pool.put(StringValue.of("Value 1"));
        Key val6Key1 = pool.put(StringValue.of("Value 000006"));

        ((OffHeapKeyedInternPool<StringValue>) pool).dumpContents();
//        Assertions.assertThat(pool.size()).isEqualTo(3);

        // call put twice more for value 2
        Key val2Key2 = pool.put(StringValue.of("Value 02"));
        Key val2Key3 = pool.put(StringValue.of("Value 02"));

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

    @Test
    public void intern() throws IOException {

        LOGGER.info("Using temp dir {}", tmpDir.getRoot().toPath().toAbsolutePath().toString());

        OffHeapKeyedInternPool<StringValue> pool = buildPool();

        //Put unique values into the the pool, out of order
        ValueSupplier<StringValue> val4Supplier1 = pool.intern(StringValue.of("Value 0004"));
        ValueSupplier<StringValue> val3Supplier1 = pool.intern(StringValue.of("Value 003"));
        ValueSupplier<StringValue> val2Supplier1 = pool.intern(StringValue.of("Value 02"));
        ValueSupplier<StringValue> val5Supplier1 = pool.intern(StringValue.of("Value 00005"));
        ValueSupplier<StringValue> val1Supplier1 = pool.intern(StringValue.of("Value 1"));
        ValueSupplier<StringValue> val6Supplier1 = pool.intern(StringValue.of("Value 000006"));

        ((OffHeapKeyedInternPool<StringValue>) pool).dumpContents();
//        Assertions.assertThat(pool.size()).isEqualTo(3);

        // call put twice more for value 2
        ValueSupplier<StringValue> val2Supplier2 = pool.intern(StringValue.of("Value 02"));
        ValueSupplier<StringValue> val2Supplier3 = pool.intern(StringValue.of("Value 02"));

        SoftAssertions softAssertions = new SoftAssertions();

        //the size should still be 3 as the last two we already in there
        softAssertions.assertThat(pool.size()).isEqualTo(6);
        softAssertions.assertThat(val1Supplier1).isNotEqualTo(val2Supplier1);
        softAssertions.assertThat(val1Supplier1).isNotEqualTo(val3Supplier1);
        softAssertions.assertThat(val2Supplier1).isNotEqualTo(val3Supplier1);

        softAssertions.assertThat(val2Supplier1).isEqualTo(val2Supplier2);
        softAssertions.assertThat(val2Supplier1).isEqualTo(val2Supplier3);

        softAssertions.assertAll();
    }


    private OffHeapKeyedInternPool<StringValue> buildPool() throws IOException {
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


    static ByteBuffer bb(final int value) {
        final ByteBuffer bb = ByteBuffer.allocateDirect(Integer.BYTES);
        bb.putInt(value).flip();
        return bb;
    }

    private static class StringValue extends KeyedInternPool.AbstractKeyedInternPoolValue {

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