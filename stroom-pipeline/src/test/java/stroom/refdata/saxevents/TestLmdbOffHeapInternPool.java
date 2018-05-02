package stroom.refdata.saxevents;

import org.apache.hadoop.hbase.util.Bytes;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.SoftAssertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

public class TestLmdbOffHeapInternPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestLmdbOffHeapInternPool.class);

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

        LmdbOffHeapInternPool<StringValue> pool = buildPool();

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

        LmdbOffHeapInternPool<StringValue> pool = buildPool();

        //Put unique values into the the pool, out of order
        Key val4Key1 = pool.put(StringValue.of("Value 0004"));
        Key val3Key1 = pool.put(StringValue.of("Value 003"));
        Key val2key1 = pool.put(StringValue.of("Value 02"));
        Key val5Key1 = pool.put(StringValue.of("Value 00005"));
        Key val1key1 = pool.put(StringValue.of("Value 1"));
        Key val6Key1 = pool.put(StringValue.of("Value 000006"));

        ((LmdbOffHeapInternPool<StringValue>) pool).dumpContents();
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

        LmdbOffHeapInternPool<StringValue> pool = buildPool();

        String value2str = "Value 02";
        StringValue value2instance1 = StringValue.of(value2str);
        StringValue value2instance2 = StringValue.of(value2str);
        StringValue value2instance3 = StringValue.of(value2str);

        //Put unique values into the the pool, out of order
        ValueProxy<StringValue> val4Supplier1 = pool.intern(StringValue.of("Value 0004"));
        ValueProxy<StringValue> val3Supplier1 = pool.intern(StringValue.of("Value 003"));
        ValueProxy<StringValue> val2Supplier1 = pool.intern(value2instance1);
        ValueProxy<StringValue> val5Supplier1 = pool.intern(StringValue.of("Value 00005"));
        ValueProxy<StringValue> val1Supplier1 = pool.intern(StringValue.of("Value 1"));
        ValueProxy<StringValue> val6Supplier1 = pool.intern(StringValue.of("Value 000006"));

        StringValue suppliedValue2Instance1 = val2Supplier1.supplyValue().get();

        pool.dumpContents();
//        Assertions.assertThat(pool.size()).isEqualTo(3);

        // call put twice more for two new StringValue instances that are logically the same
        // as value2instance1
        ValueProxy<StringValue> val2Supplier2 = pool.intern(value2instance2);
        ValueProxy<StringValue> val2Supplier3 = pool.intern(value2instance3);

        StringValue suppliedValue2Instance2 = val2Supplier2.supplyValue().get();
        StringValue suppliedValue2Instance3 = val2Supplier3.supplyValue().get();

        SoftAssertions softAssertions = new SoftAssertions();
        softAssertions.assertThat(value2instance1).isNotSameAs(value2instance2);
        softAssertions.assertThat(value2instance1).isNotSameAs(value2instance3);
        softAssertions.assertThat(value2instance2).isNotSameAs(value2instance3);

        //the size should still be 6 as the last two were already in there
        softAssertions.assertThat(pool.size()).isEqualTo(6);
        softAssertions.assertThat(val1Supplier1).isNotEqualTo(val2Supplier1);
        softAssertions.assertThat(val1Supplier1).isNotEqualTo(val3Supplier1);
        softAssertions.assertThat(val2Supplier1).isNotEqualTo(val3Supplier1);


        softAssertions.assertThat(val2Supplier1).isEqualTo(val2Supplier2);
        softAssertions.assertThat(val2Supplier1).isEqualTo(val2Supplier3);

        // these suppliers should be the same instances as the ones obtained in the first
        // set of interns
        softAssertions.assertThat(val2Supplier1).isSameAs(val2Supplier2);
        softAssertions.assertThat(val2Supplier1).isSameAs(val2Supplier3);

        // The instance we interned should be logically the same as the one we got back
        // from the pool
        Assertions.assertThat(value2instance1).isEqualTo(suppliedValue2Instance1);
        softAssertions.assertThat(value2instance1).isEqualTo(suppliedValue2Instance1);

        //All three value instances obtained from the pool should be the same instance
        softAssertions.assertThat(suppliedValue2Instance1).isEqualTo(suppliedValue2Instance2);
        softAssertions.assertThat(suppliedValue2Instance1).isEqualTo(suppliedValue2Instance3);
        softAssertions.assertThat(suppliedValue2Instance2).isEqualTo(suppliedValue2Instance3);

        softAssertions.assertAll();
    }



    private LmdbOffHeapInternPool<StringValue> buildPool() throws IOException {
        String methodName = testname.getMethodName();
        Path dbDir = tmpDir.newFolder(methodName).toPath();

        LmdbOffHeapInternPool<StringValue> pool = new LmdbOffHeapInternPool<>(
                dbDir,
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
    public void testStringValueSerDeser() {
        String value2str = "Value 02";
        StringValue value2instance1 = StringValue.of(value2str);
        StringValue value2instance2 = StringValue.of(value2str);
        Assertions.assertThat(value2instance1).isEqualTo(value2instance2);

        byte[] bytes = Bytes.copy(value2instance1.getValueBytes());
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);

        Assertions.assertThat(value2instance1).isEqualTo(StringValue.fromByteBuffer(byteBuffer));
    }


    static ByteBuffer bb(final int value) {
        final ByteBuffer bb = ByteBuffer.allocateDirect(Integer.BYTES);
        bb.putInt(value).flip();
        return bb;
    }

}