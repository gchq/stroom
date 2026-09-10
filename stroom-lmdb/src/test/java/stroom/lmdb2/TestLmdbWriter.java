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

package stroom.lmdb2;

import stroom.util.concurrent.UncheckedInterruptedException;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestLmdbWriter {

    @Test
    void testInterruptedTransferRestoresFlagAndClosesTransaction() {
        final LmdbEnv env = mock(LmdbEnv.class);
        final WriteTxn transaction = mock(WriteTxn.class);
        when(env.writeTxn()).thenReturn(transaction);
        final AtomicBoolean interruptedOnExit = new AtomicBoolean();
        final Executor executor = command -> {
            try {
                Thread.currentThread().interrupt();
                command.run();
                interruptedOnExit.set(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        };

        final LmdbWriter writer = new LmdbWriter(() -> executor, env);

        assertThat(interruptedOnExit).isTrue();
        verify(transaction).commit();
        verify(transaction).close();
        assertThatThrownBy(writer::close)
                .hasCauseInstanceOf(UncheckedInterruptedException.class)
                .hasRootCauseInstanceOf(InterruptedException.class);
    }
}
