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

package stroom.processor.impl;

import stroom.processor.api.ProcessorTaskState;
import stroom.processor.shared.TaskStatus;
import stroom.security.api.SecurityContext;
import stroom.util.shared.PermissionException;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Tests that held processor tasks can only be changed and looked up by the processing user.
class TestHeldProcessorTaskServiceImpl {

    private final ProcessorTaskDao processorTaskDao = Mockito.mock(ProcessorTaskDao.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final HeldProcessorTaskServiceImpl service =
            new HeldProcessorTaskServiceImpl(processorTaskDao, securityContext);

    @Test
    void processingUserCanUseEveryCall() {
        Mockito.when(securityContext.isProcessingUser()).thenReturn(true);
        Mockito.when(processorTaskDao.completeHeld(1L, 2)).thenReturn(true);
        Mockito.when(processorTaskDao.putBackHeld(1L, 2, "Lost")).thenReturn(true);
        final ProcessorTaskState state = new ProcessorTaskState(1L, TaskStatus.COMMITTING, 2);
        Mockito.when(processorTaskDao.getStates(List.of(1L))).thenReturn(Map.of(1L, state));
        final Instant olderThan = Instant.now();
        Mockito.when(processorTaskDao.findHeld(olderThan, 0, 10)).thenReturn(List.of(state));

        assertThat(service.completeHeld(1L, 2)).isTrue();
        assertThat(service.putBack(1L, 2, "Lost")).isTrue();
        assertThat(service.getStates(List.of(1L))).containsExactly(Map.entry(1L, state));
        assertThat(service.findHeld(olderThan, 0, 10)).containsExactly(state);
    }

    @Test
    void otherUsersCannotUseAnyCall() {
        Mockito.when(securityContext.isProcessingUser()).thenReturn(false);

        assertThatThrownBy(() -> service.completeHeld(1L, 2)).isInstanceOf(PermissionException.class);
        assertThatThrownBy(() -> service.putBack(1L, 2, "Lost")).isInstanceOf(PermissionException.class);
        assertThatThrownBy(() -> service.getStates(List.of(1L))).isInstanceOf(PermissionException.class);
        assertThatThrownBy(() -> service.findHeld(Instant.now(), 0, 10)).isInstanceOf(PermissionException.class);
        Mockito.verifyNoInteractions(processorTaskDao);
    }
}
