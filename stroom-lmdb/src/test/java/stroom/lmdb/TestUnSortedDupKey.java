package stroom.lmdb;

import stroom.lmdb.UnSortedDupKey.UnsortedDupKeyFactory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestUnSortedDupKey {

    @Test
    void getKey() {
        final UnsortedDupKeyFactory<String> factory = UnSortedDupKey.createFactory(String.class, 4);
        final UnSortedDupKey<String> key1 = factory.createUnsortedKey("keyA");
        final UnSortedDupKey<String> key2 = factory.createUnsortedKey("keyA");
        // Same "keyA" but the diff ids should make them unique
        assertThat(key1)
                .isNotEqualTo(key2);
        assertThat(key1.getKey())
                .isEqualTo(key2.getKey());
        assertThat(key1.hashCode())
                .isNotEqualTo(key2.hashCode());
        assertThat(key1.getId())
                .isEqualTo(0);
        assertThat(key2.getId())
                .isEqualTo(1);

        final UnSortedDupKey<String> key3 = factory.createUnsortedKey("keyB");

        // Different "key..."
        assertThat(key1)
                .isNotEqualTo(key3);
        assertThat(key1.getKey())
                .isNotEqualTo(key3.getKey());
        assertThat(key3.getId())
                .isEqualTo(2);
    }
}
