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

package stroom.ai.shared;

import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestAiChatStatus {

    @Test
    void anIdleCompactionIsCompactingAndAnOrdinaryRunIsNot() {
        final AiChatStatus idle = status(AiChatRunStatus.RUNNING, true);
        final AiChatStatus ordinary = status(AiChatRunStatus.RUNNING, false);

        assertThat(idle.isCompacting()).isTrue();
        assertThat(idle.isBusy()).isTrue();
        assertThat(ordinary.isCompacting()).isFalse();
        assertThat(ordinary.isBusy()).isTrue();
        // Stopping is still compacting; once it has ended it is not.
        assertThat(status(AiChatRunStatus.STOPPING, true).isCompacting()).isTrue();
        assertThat(status(AiChatRunStatus.STOPPED, true).isCompacting()).isFalse();
        assertThat(idle).isNotEqualTo(ordinary);
    }

    @Test
    void theIdleCompactionFlagIsSentToTheClient() {
        final AiChatStatus idle = status(AiChatRunStatus.RUNNING, true);

        final String json = JsonUtil.writeValueAsString(idle, false);
        final AiChatStatus read = JsonUtil.readValue(json, AiChatStatus.class);

        assertThat(json).contains("\"idleCompaction\":true").doesNotContain("compacting");
        assertThat(read).isEqualTo(idle);
        assertThat(read.isCompacting()).isTrue();
        // Statuses from before the flag read as not compacting.
        assertThat(JsonUtil.readValue("{\"chatId\":1,\"runStatus\":\"RUNNING\"}", AiChatStatus.class)
                .isCompacting()).isFalse();
    }

    private static AiChatStatus status(final AiChatRunStatus runStatus, final boolean idleCompaction) {
        return new AiChatStatus(1, "t", 0L, runStatus, false, false, 0L, null, 0, idleCompaction);
    }
}
