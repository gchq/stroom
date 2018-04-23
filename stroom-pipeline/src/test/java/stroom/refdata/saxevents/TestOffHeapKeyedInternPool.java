package stroom.refdata.saxevents;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class TestOffHeapKeyedInternPool {

    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder();

//    @Before
//    public void setup() {
//
//    }

    @Test
    public void put_newEntry() {
//        OffHeapKeyedInternPool<String>


    }

    @Test
    public void get() {
    }

    @Test
    public void close() {
    }

    private static class StringValue extends KeyedInternPool.AbstractKeyedInternPoolValue {

        private final String value;

        StringValue(final String value) {
            this.value = value;
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
    }
}