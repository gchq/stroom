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

package stroom.ai.impl;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestStoppableCall {

    private static final Duration CHECK = Duration.ofMillis(20);

    @Test
    void returnsWhatTheCallReturns() {
        assertThat(StoppableCall.run(() -> "done", new AtomicBoolean(false), CHECK)).isEqualTo("done");
    }

    @Test
    void throwsWhatTheCallThrows() {
        assertThatThrownBy(() -> StoppableCall.run(() -> {
            throw new IllegalStateException("server gone");
        }, new AtomicBoolean(false), CHECK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("server gone");
    }

    @Test
    void givesUpAsSoonAsAStopIsRequested() throws InterruptedException {
        // A call that blocks far longer than the test, as an MCP tool call waiting on its server does.
        final AtomicBoolean stop = new AtomicBoolean(false);
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch interrupted = new CountDownLatch(1);
        final Thread stopper = Thread.ofVirtual().start(() -> {
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                stop.set(true);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        final long startMs = System.currentTimeMillis();

        assertThatThrownBy(() -> StoppableCall.run(() -> {
            started.countDown();
            try {
                Thread.sleep(Duration.ofMinutes(5));
            } catch (final InterruptedException e) {
                interrupted.countDown();
            }
            return "too late";
        }, stop, CHECK)).isInstanceOf(StoppableCall.StoppedException.class);

        assertThat(System.currentTimeMillis() - startMs).isLessThan(5_000);
        // The call's thread is told to give up too.
        assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
        stopper.join();
    }

    @Test
    void doesNotStartOnceStopped() {
        final AtomicBoolean ran = new AtomicBoolean(false);

        assertThatThrownBy(() -> StoppableCall.run(() -> ran.getAndSet(true), new AtomicBoolean(true), CHECK))
                .isInstanceOf(StoppableCall.StoppedException.class);
        assertThat(ran).isFalse();
    }
}
