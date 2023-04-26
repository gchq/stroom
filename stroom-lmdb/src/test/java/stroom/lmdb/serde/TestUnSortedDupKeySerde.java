package stroom.lmdb.serde;

import stroom.lmdb.UnSortedDupKey;
import stroom.lmdb.UnSortedDupKey.UnsortedDupKeyFactory;

import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

class TestUnSortedDupKeySerde
        extends AbstractSerdeTest<
        UnSortedDupKey<String>,
        UnSortedDupKeySerde<String>> {

    private static final int ID_BYTE_LENGTH = 4;

    @Test
    void serializeDeserialize() {
        final String key = "foo";
        final UnsortedDupKeyFactory<String> keyFactory = UnSortedDupKey.createFactory(
                String.class, ID_BYTE_LENGTH);
        final UnSortedDupKey<String> unSortedDupKey = keyFactory.createUnsortedKey(key);

        doSerialisationDeserialisationTest(unSortedDupKey);
    }

    @Override
    Supplier<UnSortedDupKeySerde<String>> getSerdeSupplier() {
        return () -> new UnSortedDupKeySerde<>(new StringSerde(), ID_BYTE_LENGTH);
    }

    @Override
    TypeLiteral<UnSortedDupKeySerde<String>> getSerdeType() {
        return new TypeLiteral<UnSortedDupKeySerde<String>>(){};
    }
}
