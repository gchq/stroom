package stroom.pipeline.refdata.store;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

class TestXxHashValueStoreHashAlgorithm {

    @Test
    void testHashing() {

        final String input = "Lorem ipsum dolor sit amet, consectetur adipiscing elit," +
                " sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.";

        final ValueStoreHashAlgorithm valueStoreHashAlgorithm = new XxHashValueStoreHashAlgorithm();

        long hash1a = valueStoreHashAlgorithm.hash(input);
        long hash1b = valueStoreHashAlgorithm.hash(input);

        Assertions.assertThat(hash1b).isEqualTo(hash1a);


        final ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(input);

        long hash2a = valueStoreHashAlgorithm.hash(byteBuffer);
        long hash2b = valueStoreHashAlgorithm.hash(byteBuffer);

        Assertions.assertThat(hash2b).isEqualTo(hash2a);
    }
}