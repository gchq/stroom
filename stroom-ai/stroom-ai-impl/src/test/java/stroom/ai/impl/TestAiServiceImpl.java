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

import stroom.util.shared.http.HttpClientConfig;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.time.SimpleDurationUtil;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TestAiServiceImpl {

    @Test
    void testDefaultHttpClientConfigTimeouts() {
        // Regression test for gh-6017. Now that the model document's timeouts are honoured, the
        // default connection timeout must stay short so an unreachable model server is reported
        // promptly, while the response timeout stays long enough for slow models.
        final AiServiceImpl aiService = new AiServiceImpl(
                null, null, null, null, null, null, null);

        final HttpClientConfig config = aiService.getDefaultHttpClientConfig();

        assertThat(toDuration(config.getConnectionTimeout()))
                .isEqualTo(Duration.ofSeconds(30));
        assertThat(toDuration(config.getTimeout()))
                .isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void testDefaultHttpClientConfigIsReused() {
        final AiServiceImpl aiService = new AiServiceImpl(
                null, null, null, null, null, null, null);

        assertThat(aiService.getDefaultHttpClientConfig())
                .isSameAs(aiService.getDefaultHttpClientConfig());
    }

    private Duration toDuration(final SimpleDuration simpleDuration) {
        return SimpleDurationUtil.convertToStroomDuration(simpleDuration).getDuration();
    }
}
