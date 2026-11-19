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

/// How much of a stream the tools read as its sample.
class TestAiSamplingConfig {

    @Test
    void theDefaultsAreTwoMillionCharactersAStreamAndEventsUpToAHundredThousand() {
        final AiSamplingConfig config = new AiSamplingConfig();

        assertThat(config.getMaxCharsPerStream()).isEqualTo(2_000_000);
        assertThat(config.getMaxEventChars()).isEqualTo(100_000);
        assertThat(new AiConfig().getSampling()).usingRecursiveComparison().isEqualTo(config);
    }

    @Test
    void settingsLeftOutAreTheDefaults() {
        final AiSamplingConfig read = JsonUtil.readValue("{\"maxEventChars\": 3000}", AiSamplingConfig.class);

        assertThat(read.getMaxEventChars()).isEqualTo(3_000);
        assertThat(read.getMaxCharsPerStream()).isEqualTo(AiSamplingConfig.DEFAULT_MAX_CHARS_PER_STREAM);
    }

    @Test
    void samplingCantBeTurnedOff() {
        final AiSamplingConfig config = new AiSamplingConfig(0, -1);

        assertThat(config.getMaxCharsPerStream()).isEqualTo(AiSamplingConfig.MIN_MAX_CHARS_PER_STREAM);
        assertThat(config.getMaxEventChars()).isEqualTo(AiSamplingConfig.MIN_MAX_EVENT_CHARS);
    }

    @Test
    void theSamplingSettingsAreReadFromTheAiConfig() {
        final AiConfig read = JsonUtil.readValue("{\"sampling\": {\"maxEventChars\": 50000}}", AiConfig.class);

        assertThat(read.getSampling().getMaxEventChars()).isEqualTo(50_000);
        assertThat(read.getSampling().getMaxCharsPerStream()).isEqualTo(2_000_000);
    }
}
