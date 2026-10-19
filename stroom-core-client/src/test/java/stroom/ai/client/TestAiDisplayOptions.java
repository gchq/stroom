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

import stroom.ai.shared.AskStroomAiConfig;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class TestAiDisplayOptions {

    @Test
    void everythingIsShownByDefault() {
        assertThat(AiDisplayOptions.hideClasses(AskStroomAiConfig.builder().build())).containsOnly(
                entry(AiDisplayOptions.HIDE_TOOL_CALLS_CLASS, false),
                entry(AiDisplayOptions.HIDE_REASONING_CLASS, false),
                entry(AiDisplayOptions.HIDE_REQUEST_DETAIL_CLASS, false));
        // Settings that can't be read yet get the defaults.
        assertThat(AiDisplayOptions.hideClasses(null)).containsOnly(
                entry(AiDisplayOptions.HIDE_TOOL_CALLS_CLASS, false),
                entry(AiDisplayOptions.HIDE_REASONING_CLASS, false),
                entry(AiDisplayOptions.HIDE_REQUEST_DETAIL_CLASS, false));
    }

    @Test
    void eachOptionHidesOnlyItsOwnEntries() {
        assertThat(AiDisplayOptions.hideClasses(AskStroomAiConfig.builder().showToolCalls(false).build()))
                .containsOnly(entry(AiDisplayOptions.HIDE_TOOL_CALLS_CLASS, true),
                        entry(AiDisplayOptions.HIDE_REASONING_CLASS, false),
                        entry(AiDisplayOptions.HIDE_REQUEST_DETAIL_CLASS, false));
        assertThat(AiDisplayOptions.hideClasses(AskStroomAiConfig.builder().showReasoning(false).build()))
                .containsOnly(entry(AiDisplayOptions.HIDE_TOOL_CALLS_CLASS, false),
                        entry(AiDisplayOptions.HIDE_REASONING_CLASS, true),
                        entry(AiDisplayOptions.HIDE_REQUEST_DETAIL_CLASS, false));
        assertThat(AiDisplayOptions.hideClasses(AskStroomAiConfig.builder().enableDebugDetail(false).build()))
                .containsOnly(entry(AiDisplayOptions.HIDE_TOOL_CALLS_CLASS, false),
                        entry(AiDisplayOptions.HIDE_REASONING_CLASS, false),
                        entry(AiDisplayOptions.HIDE_REQUEST_DETAIL_CLASS, true));
    }

    @Test
    void settingsSavedBeforeTheOptionsExistedShowEverything() {
        // As read from preferences saved without them.
        final AskStroomAiConfig saved = new AskStroomAiConfig(null, null, null, null, null, null, null, null, null,
                true, null, null, true);

        assertThat(saved.isShowToolCalls()).isTrue();
        assertThat(saved.isShowReasoning()).isTrue();
    }
}
