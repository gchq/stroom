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
    public void put_entries() throws IOException {

        KeyedInternPool<StringValue> pool = buildPool();


        KeyedInternPool.Key val1key1 = pool.put(StringValue.of("Value 1"));
        KeyedInternPool.Key val2key1 = pool.put(StringValue.of("Value 2"));
        KeyedInternPool.Key val3Key1 = pool.put(StringValue.of("Value 3"));

        ((OffHeapKeyedInternPool<StringValue>) pool).dumpContents();
//        Assertions.assertThat(pool.size()).isEqualTo(3);

        // call put twice more for value 2
        KeyedInternPool.Key val2Key2 = pool.put(StringValue.of("Value 2"));
        KeyedInternPool.Key val2Key3 = pool.put(StringValue.of("Value 2"));



        SoftAssertions softAssertions = new SoftAssertions();

        //the size should still be 3 as the last two we already in there
        softAssertions.assertThat(pool.size()).isEqualTo(3);
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
    public void testByteBufferUtils_lowValues() {

//        KeyedInternPool.Key key = new KeyedInternPool.Key(123, 0);

//        ByteBuffer byteBuffer = key.toDirectByteBuffer();
//
//        ByteBuffer byteBuffer = ByteBuffer.allocateDirect()

        IntStream.rangeClosed(-20,20)
                .forEach(i -> {
                    ByteBuffer byteBuffer = ByteBuffer.allocate(Integer.BYTES);
                    byteBuffer.putInt(i);
                    LOGGER.info("{} - {}", i, KeyedInternPool.Key.byteArrayToHex(byteBuffer.array()));

                });


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