package stroom.pipeline.refdata.store;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TestXxHashValueStoreHashAlgorithm {

    @Test
    void testHashing() {

        final String input = "Lorem ipsum dolor sit amet, consectetur adipiscing elit," +
                " sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.";

        final ValueStoreHashAlgorithm valueStoreHashAlgorithm = new XxHashValueStoreHashAlgorithm();

        long hash1a = valueStoreHashAlgorithm.hash(input);
        long hash1b = valueStoreHashAlgorithm.hash(input);

        assertThat(hash1b).isEqualTo(hash1a);

        final ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(input);

        long hash2a = valueStoreHashAlgorithm.hash(byteBuffer);
        long hash2b = valueStoreHashAlgorithm.hash(byteBuffer);

        assertThat(hash2b).isEqualTo(hash2a);
    }

    @Test
    void testDirectVsHeap() {
        final ByteBuffer direct = ByteBuffer.allocateDirect(10);
        direct.put("foo".getBytes(StandardCharsets.UTF_8));
        direct.flip();

        final ByteBuffer heap = ByteBuffer.allocate(10);
        heap.put("foo".getBytes(StandardCharsets.UTF_8));
        heap.flip();

        final ValueStoreHashAlgorithm valueStoreHashAlgorithm = new XxHashValueStoreHashAlgorithm();
        // Same hash for direct and heap buffer
        assertThat(valueStoreHashAlgorithm.hash(direct))
                .isEqualTo(valueStoreHashAlgorithm.hash(heap));
    }

    @Test
    void testDirectVsHeap_null() {
        final ByteBuffer direct = ByteBuffer.allocateDirect(10);
        direct.flip();

        final ByteBuffer heap = ByteBuffer.allocate(10);
        heap.flip();

        final ValueStoreHashAlgorithm valueStoreHashAlgorithm = new XxHashValueStoreHashAlgorithm();
        // Same hash for direct and heap buffer
        assertThat(valueStoreHashAlgorithm.hash(direct))
                .isEqualTo(valueStoreHashAlgorithm.hash(heap));
        assertThat(direct.remaining())
                .isEqualTo(0);
        assertThat(heap.remaining())
                .isEqualTo(0);
    }

    @Test
    void testDirectVsHeap_null2() {
        final ByteBuffer direct = ByteBuffer.allocateDirect(0);
        direct.flip();

        final ByteBuffer heap = ByteBuffer.wrap(new byte[0]);
        heap.flip();

        final ValueStoreHashAlgorithm valueStoreHashAlgorithm = new XxHashValueStoreHashAlgorithm();
        // Same hash for direct and heap buffer
        assertThat(valueStoreHashAlgorithm.hash(direct))
                .isEqualTo(valueStoreHashAlgorithm.hash(heap));
    }
}
