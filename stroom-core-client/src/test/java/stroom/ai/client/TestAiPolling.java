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

package stroom.ai.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestAiPolling {

    private static final long NOW = 1_000_000L;

    @Test
    void aRunWaitingForAnAnswerShownNeedsNoMorePolling() {
        assertThat(AiPolling.canStopForUser(true, true, 0, 0, NOW)).isTrue();
    }

    @Test
    void aRunWaitingWithNothingShownIsPolledUntilWhatItAsksArrives() {
        // Seen: the poll saw the run waiting before the form it asked had reached the chat, and the form was
        // never shown.
        assertThat(AiPolling.canStopForUser(true, false, NOW, 0, NOW)).isFalse();
        assertThat(AiPolling.canStopForUser(true, false, NOW - 1_000, 0, NOW)).isFalse();
    }

    @Test
    void aRunWaitingForSomethingNeverShownIsPolledForAMinuteAtMost() {
        assertThat(AiPolling.canStopForUser(true, false, NOW - AiPolling.MAX_WAIT_FOR_PROMPT_MS + 1, 0, NOW))
                .isFalse();
        assertThat(AiPolling.canStopForUser(true, false, NOW - AiPolling.MAX_WAIT_FOR_PROMPT_MS, 0, NOW))
                .isTrue();
    }

    @Test
    void aRunNotWaitingIsPolledAsItWorks() {
        assertThat(AiPolling.canStopForUser(false, true, 0, 0, NOW)).isFalse();
        assertThat(AiPolling.canStopForUser(false, false, NOW - AiPolling.MAX_WAIT_FOR_PROMPT_MS, 0, NOW))
                .isFalse();
    }

    @Test
    void justAfterAnAnswerPollingCarriesOnUntilTheRunDoes() {
        assertThat(AiPolling.canStopForUser(true, true, 0, NOW + 1, NOW)).isFalse();
        assertThat(AiPolling.canStopForUser(true, true, 0, NOW, NOW)).isFalse();
        assertThat(AiPolling.canStopForUser(true, true, 0, NOW - 1, NOW)).isTrue();
    }
}
