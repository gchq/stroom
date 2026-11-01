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

package stroom.ai.impl;

import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/// Where the agent's tools build things, and how many tasks process a build's streams at once.
class TestAiBuildsConfig {

    @Test
    void fourTasksProcessABuildsStreamsUnlessConfigured() {
        assertThat(new AiBuildsConfig().getMaxProcessingTasks()).isEqualTo(4);
        assertThat(new AiBuildsConfig("Builds", 8).getMaxProcessingTasks()).isEqualTo(8);
        assertThat(new AiBuildsConfig("Builds", 1).getMaxProcessingTasks()).isEqualTo(1);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -3})
    void fewerThanOneTaskIsTheDefault(final Integer tasks) {
        assertThat(new AiBuildsConfig(null, tasks).getMaxProcessingTasks())
                .isEqualTo(AiBuildsConfig.DEFAULT_MAX_PROCESSING_TASKS);
    }

    @Test
    void aConfigWithoutTheSettingReadsAsTheDefault() {
        final AiBuildsConfig read = JsonUtil.readValue("{\"workspaceFolder\": \"Builds\"}", AiBuildsConfig.class);

        assertThat(read.getWorkspaceFolder()).isEqualTo("Builds");
        assertThat(read.getMaxProcessingTasks()).isEqualTo(4);
    }
}
