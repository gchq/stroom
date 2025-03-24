package stroom.proxy.app.handler;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

class TestRetryState {

    @Test
    void serDeser() {
        final RetryState retryState = new RetryState(12345L, 67890L, (short) 23);
        final ByteBuffer byteBuffer = ByteBuffer.allocate(RetryState.TOTAL_BYTES);
        retryState.serialise(byteBuffer);
        byteBuffer.flip();

        final RetryState retryState2 = RetryState.deserialise(byteBuffer);

        assertThat(retryState2)
                .isEqualTo(retryState);
    }

    @Test
    void serDeser2() {
        final RetryState retryState = new RetryState(12345L, 67890L, (short) 23);
        final byte[] bytes = retryState.serialise();
        final ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);

        final RetryState retryState2 = RetryState.deserialise(byteBuffer);

        assertThat(retryState2)
                .isEqualTo(retryState);
    }
}
