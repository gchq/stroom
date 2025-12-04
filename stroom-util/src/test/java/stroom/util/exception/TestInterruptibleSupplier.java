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

package stroom.util.exception;

import stroom.util.concurrent.AtomicSequence;
import stroom.util.concurrent.UncheckedInterruptedException;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Objects;

class TestInterruptibleSupplier {

    private final AtomicSequence atomicSequence = new AtomicSequence(2);

    @Test
    void unchecked() {
        final Integer val = Objects.requireNonNullElseGet(
                null,
                InterruptibleSupplier.unchecked(this::getNext));

        Assertions.assertThat(val)
                .isEqualTo(0);
    }

    @Test
    void unchecked_throws() {
        final Integer val = Objects.requireNonNullElseGet(
                null,
                InterruptibleSupplier.unchecked(this::getNext));

        Assertions.assertThat(val)
                .isEqualTo(0);

        Assertions.assertThatThrownBy(() ->
                        Objects.requireNonNullElseGet(
                                null,
                                InterruptibleSupplier.unchecked(this::getNext)))
                .isInstanceOf(UncheckedInterruptedException.class)
                .getCause()
                .isInstanceOf(InterruptedException.class);
    }

    /**
     * Alternate between returning zero or throwing a {@link InterruptedException}
     */
    private int getNext() throws InterruptedException {
        final int i = atomicSequence.next();
        if (i == 1) {
            throw new InterruptedException();
        }
        return i;
    }
}
