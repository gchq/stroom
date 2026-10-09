/*
 * Copyright 2026 Crown Copyright
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

package stroom.bytebuffer.impl6;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins what happens when the work and the buffer's release <em>both</em> fail.
 *
 * <p>{@code doWithByteBuffer} runs the caller's consumer under {@code try (this)}. Under
 * try-with-resources an exception thrown by {@code close()} is <b>suppressed</b> — attached to the
 * exception the body threw, which propagates. Under a hand-written {@code try/finally} the close
 * failure <b>replaces</b> it and the original is lost.</p>
 *
 * <p>These classes sit on the LMDB reference-data path, so the difference is not academic: a real
 * reference-data failure would be reported as a buffer-return failure, and whoever was debugging it
 * would be handed the wrong exception and go looking in the wrong place.</p>
 *
 * <p>This branch carried the {@code try/finally} form from 2026-06-22 until 2026-09-04, as an
 * incidental hunk in a commit about something else. These tests exist so a future edit cannot
 * quietly reintroduce it.</p>
 */
class TestPooledByteBufferClose {

    /** A queue whose release always fails, so {@code close()} throws. */
    private static final class FailingQueue extends PooledByteBufferQueue {

        private FailingQueue() {
            super(1, 16);
        }

        @Override
        void release(final ByteBuffer buffer) {
            throw new IllegalStateException("release failed");
        }
    }

    @Test
    void testTheBodysExceptionWinsAndTheCloseFailureIsSuppressed() {
        final PooledByteBufferImpl pooled =
                new PooledByteBufferImpl(new FailingQueue(), ByteBuffer.allocate(16));

        assertThatThrownBy(() -> pooled.doWithByteBuffer(buffer -> {
            throw new IllegalArgumentException("the real failure");
        }))
                .as("the caller's failure is the one worth reporting")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("the real failure")
                .satisfies(thrown -> assertThat(thrown.getSuppressed())
                        .as("and the close failure must survive, attached rather than discarded")
                        .hasSize(1)
                        .allSatisfy(suppressed -> assertThat(suppressed)
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessage("release failed")));
    }

    /** With the body succeeding there is nothing to suppress, so the close failure propagates. */
    @Test
    void testACloseFailureAloneStillPropagates() {
        final PooledByteBufferImpl pooled =
                new PooledByteBufferImpl(new FailingQueue(), ByteBuffer.allocate(16));

        assertThatThrownBy(() -> pooled.doWithByteBuffer(buffer -> {
            // Succeeds, so close() is the only thing that can fail.
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("release failed");
    }

    /** The ordinary path: the consumer sees the buffer, and it is released afterwards. */
    @Test
    void testTheBufferIsReleasedOnTheHappyPath() {
        final PooledByteBufferQueue queue = new PooledByteBufferQueue(1, 16);
        final ByteBuffer buffer = ByteBuffer.allocate(16);
        final PooledByteBufferImpl pooled = new PooledByteBufferImpl(queue, buffer);

        final ByteBuffer[] seen = new ByteBuffer[1];
        pooled.doWithByteBuffer(b -> seen[0] = b);

        assertThat(seen[0]).isSameAs(buffer);
        assertThatThrownBy(pooled::getByteBuffer)
                .as("released, so the buffer must not be reachable again")
                .isInstanceOf(NullPointerException.class);
    }
}
