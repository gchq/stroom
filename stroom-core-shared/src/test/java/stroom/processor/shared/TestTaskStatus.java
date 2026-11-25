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

package stroom.processor.shared;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestTaskStatus {

    /// The ids are stored in the database, so must never change or be used again.
    @SuppressWarnings("deprecation")
    @Test
    void testIds() {
        final Map<TaskStatus, Integer> ids = Arrays.stream(TaskStatus.values())
                .collect(Collectors.toMap(Function.identity(), status -> (int) status.getPrimitiveValue()));

        assertThat(ids).containsExactlyInAnyOrderEntriesOf(Map.of(
                TaskStatus.CREATED, 0,
                TaskStatus.QUEUED, 1,
                TaskStatus.ASSIGNED, 2,
                TaskStatus.PROCESSING, 3,
                TaskStatus.COMMITTING, 4,
                TaskStatus.RETRY, 5,
                TaskStatus.COMPLETE, 10,
                TaskStatus.FAILED, 22,
                TaskStatus.ABORTED, 23,
                TaskStatus.DELETED, 99));
    }

    @Test
    void testFromId() {
        for (final TaskStatus status : TaskStatus.values()) {
            assertThat(TaskStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(status.getPrimitiveValue()))
                    .isEqualTo(status);
        }
    }

    /// Searching for tasks by status turns the display value back into the status this way.
    @Test
    void testDisplayValueIsTheNameInAnotherCase() {
        for (final TaskStatus status : TaskStatus.values()) {
            assertThat(TaskStatus.valueOf(status.getDisplayValue().toUpperCase())).isEqualTo(status);
        }
    }
}
