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

import stroom.util.concurrent.UncheckedInterruptedException;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

class TestInterruptibleConsumer {

    final AtomicBoolean wasStuffDone = new AtomicBoolean(false);

    @Test
    void unchecked() {

        Stream.of(1)
                .forEach(InterruptibleConsumer.unchecked(this::consumeStuff));
        Assertions.assertThat(wasStuffDone)
                .isTrue();
    }

    @Test
    void unchecked_throws() {
        Assertions.assertThatThrownBy(() ->
                        Stream.of(0)
                                .forEach(InterruptibleConsumer.unchecked(this::consumeStuff)))
                .isInstanceOf(UncheckedInterruptedException.class)
                .getCause()
                .isInstanceOf(InterruptedException.class);

        Assertions.assertThat(wasStuffDone)
                .isFalse();
    }

    private void consumeStuff(final int i) throws InterruptedException {
        if (i == 0) {
            throw new InterruptedException();
        }
        wasStuffDone.set(true);
    }
}
