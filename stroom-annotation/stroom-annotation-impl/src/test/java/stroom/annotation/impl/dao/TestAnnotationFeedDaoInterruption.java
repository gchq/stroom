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

package stroom.annotation.impl.dao;

import stroom.annotation.impl.db.AnnotationDbConnProvider;
import stroom.task.api.ExecutorProvider;
import stroom.util.concurrent.UncheckedInterruptedException;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestAnnotationFeedDaoInterruption {

    @Test
    void testInterruptedWaitKeepsInterruptFlag() {
        final List<Runnable> queued = new ArrayList<>();
        final AnnotationFeedDao dao = createDao(queued::add);
        try {
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> dao.async(() -> "result"))
                    .isInstanceOf(UncheckedInterruptedException.class)
                    .hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(queued).hasSize(1);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void testSuccessfulTaskDoesNotInterruptCaller() {
        assertThat(createDao(Runnable::run).async(() -> "result")).isEqualTo("result");
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    void testFailedTaskPreservesCauseWithoutInterruptingCaller() {
        final IllegalStateException failure = new IllegalStateException("Task failed");
        assertThatThrownBy(() -> createDao(Runnable::run).async(() -> {
            throw failure;
        })).isSameAs(failure);
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    private AnnotationFeedDao createDao(final Executor executor) {
        final ExecutorProvider provider = mock(ExecutorProvider.class);
        when(provider.get()).thenReturn(executor);
        return new AnnotationFeedDao(mock(AnnotationDbConnProvider.class), provider);
    }
}
