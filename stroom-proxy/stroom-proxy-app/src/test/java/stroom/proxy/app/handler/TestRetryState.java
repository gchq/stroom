/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
