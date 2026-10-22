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

package stroom.search.extraction;

import stroom.util.concurrent.UncheckedInterruptedException;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestStreamEventMapInterruption {

    @Test
    void testInterruptedPut() {
        final StreamEventMap map = new StreamEventMap(1);
        assertPreservesInterrupt(() -> map.put(new Event(1, 1, null)));
        assertThat(map.size()).isZero();
    }

    @Test
    void testInterruptedTake() {
        final StreamEventMap map = new StreamEventMap(1);
        assertPreservesInterrupt(map::take);
    }

    @Test
    void testInterruptedComplete() {
        final StreamEventMap map = new StreamEventMap(1);
        assertPreservesInterrupt(map::complete);
    }

    @Test
    void testInterruptedTerminate() {
        final StreamEventMap map = new StreamEventMap(1);
        assertPreservesInterrupt(map::terminate);
    }

    private void assertPreservesInterrupt(final ThrowingCallable operation) {
        try {
            Thread.currentThread().interrupt();
            assertThatThrownBy(operation)
                    .isInstanceOf(UncheckedInterruptedException.class)
                    .hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
