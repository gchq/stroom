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

package stroom.mcp.impl;

import stroom.mcp.impl.RunningCalls.RunningCall;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestRunningCalls {

    private final RunningCalls calls = new RunningCalls();

    @AfterEach
    void tearDown() {
        Thread.interrupted();
    }

    @Test
    void aRequestIsCancelledByItsIdWhateverNumberTypeItWasReadAs() {
        try (final RunningCall call = calls.start(7)) {
            assertThat(calls.count()).isOne();

            assertThat(calls.cancel(7L)).isTrue();

            assertThat(call.isCancelled()).isTrue();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        }
        // Closing clears the interrupt: the thread is the server's, and goes on to other requests.
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
        assertThat(calls.count()).isZero();
    }

    @Test
    void aRequestThatHasEndedCanNoLongerBeCancelled() {
        final RunningCall call = calls.start("r1");
        call.close();

        assertThat(calls.cancel("r1")).isFalse();
        call.cancel();

        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    void everyRequestIsCancelledWhenTheSessionEnds() {
        try (final RunningCall call = calls.start(null)) {
            // A request with no id can't be cancelled by the client, but is when the session ends.
            assertThat(calls.cancel(null)).isFalse();

            calls.cancelAll();

            assertThat(call.isCancelled()).isTrue();
        }
    }
}
