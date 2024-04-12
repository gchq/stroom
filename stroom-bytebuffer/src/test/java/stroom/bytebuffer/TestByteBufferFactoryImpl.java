package stroom.bytebuffer;

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestByteBufferFactoryImpl {

    @Test
    void test() {
        final ByteBufferFactoryImpl byteBufferFactory = new ByteBufferFactoryImpl();
        assertThat(byteBufferFactory.acquire(4).capacity()).isEqualTo(4);
        assertThat(byteBufferFactory.acquire(8).capacity()).isEqualTo(8);
        assertThat(byteBufferFactory.acquire(9).capacity()).isEqualTo(16);
        assertThat(byteBufferFactory.acquire(512).capacity()).isEqualTo(512);
        assertThat(byteBufferFactory.acquire(513).capacity()).isEqualTo(1024);
    }
}
