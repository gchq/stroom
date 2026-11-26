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

import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestProcessorTask {

    /// Nodes send tasks to each other as JSON, so the run count must survive it.
    @Test
    void testJsonKeepsRunFaultsAndReason() {
        final ProcessorTask task = ProcessorTask.builder()
                .id(1)
                .version(2)
                .status(TaskStatus.RETRY)
                .runCount(3)
                .faultCount(2)
                .reason("Broken")
                .build();

        final ProcessorTask copy = JsonUtil.readValue(JsonUtil.writeValueAsString(task), ProcessorTask.class);

        assertThat(copy).usingRecursiveComparison().isEqualTo(task);
    }

    @Test
    void testJsonWithoutRunFaultsAndReason() {
        final ProcessorTask task = JsonUtil.readValue("{\"id\":1,\"status\":\"CREATED\"}", ProcessorTask.class);

        assertThat(task.getRunCount()).isZero();
        assertThat(task.getFaultCount()).isZero();
        assertThat(task.getReason()).isNull();
    }

    @Test
    void testCopyKeepsRunFaultsAndReason() {
        final ProcessorTask task = ProcessorTask.builder()
                .id(1)
                .runCount(3)
                .faultCount(2)
                .reason("Broken")
                .build();

        assertThat(task.copy().build()).usingRecursiveComparison().isEqualTo(task);
    }
}
