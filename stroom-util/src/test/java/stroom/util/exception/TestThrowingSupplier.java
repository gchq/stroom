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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

class TestThrowingSupplier {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestThrowingSupplier.class);

    private final AtomicSequence atomicSequence = new AtomicSequence(2);

    @Test
    void unchecked() {
        final Integer val = Objects.requireNonNullElseGet(
                null,
                ThrowingSupplier.unchecked(this::getNext));

        Assertions.assertThat(val)
                .isEqualTo(0);
    }

    @Test
    void unchecked_throws() {
        final Integer val = Objects.requireNonNullElseGet(
                null,
                ThrowingSupplier.unchecked(this::getNext));
        Assertions.assertThat(val)
                .isEqualTo(0);

        Assertions.assertThatThrownBy(
                        () -> {
                            //noinspection ResultOfMethodCallIgnored
                            Objects.requireNonNullElseGet(
                                    null,
                                    ThrowingSupplier.unchecked(this::getNext));
                        })
                .isInstanceOf(RuntimeException.class);
    }

    /**
     * Alternate between returning zero or throwing a {@link MyCheckedException}
     */
    private int getNext() throws MyCheckedException {
        final int i = atomicSequence.next();
        if (i == 1) {
            throw new MyCheckedException();
        }
        return i;
    }

    private static class MyCheckedException extends Exception {

    }
}
