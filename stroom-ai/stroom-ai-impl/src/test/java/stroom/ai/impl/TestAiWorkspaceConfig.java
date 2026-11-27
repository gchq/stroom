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

import static org.assertj.core.api.Assertions.assertThat;

class TestAiWorkspaceConfig {

    @Test
    void characterisationHasItsOwnWorkersApartFromTheJoins() {
        // Decided by the user (2026-11-26): an admin raises characterisation's calls at once for a model with a high
        // rate limit without changing eql_sequence's joins.
        assertThat(new AiWorkspaceConfig().getCharacteriseWorkers())
                .isEqualTo(AiWorkspaceConfig.DEFAULT_CHARACTERISE_WORKERS)
                .isEqualTo(4);

        final AiWorkspaceConfig read = JsonUtil.readValue("{\"characteriseWorkers\": 8}", AiWorkspaceConfig.class);
        assertThat(read.getCharacteriseWorkers()).isEqualTo(8);
        assertThat(read.getJoinWorkers()).isEqualTo(AiWorkspaceConfig.DEFAULT_JOIN_WORKERS);
    }
}
