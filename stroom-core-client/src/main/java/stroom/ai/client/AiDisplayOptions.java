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
import stroom.util.shared.NullSafe;

import com.google.gwt.dom.client.Element;

import java.util.LinkedHashMap;
import java.util.Map;

/// What of each turn's activity the user has chosen to see (tool calls, reasoning and request detail), shown by
/// classes on the conversation that hide the entries they don't want. Only what is shown changes: the agent works
/// the same either way, and the entries come back as soon as the option is turned on again.
final class AiDisplayOptions {

    /// Hides tool calls, with their results.
    static final String HIDE_TOOL_CALLS_CLASS = "ai-hide-tool-calls";
    /// Hides the model's reasoning, including as it streams.
    static final String HIDE_REASONING_CLASS = "ai-hide-reasoning";
    /// Hides request detail entries.
    static final String HIDE_REQUEST_DETAIL_CLASS = "ai-hide-request-detail";

    private AiDisplayOptions() {
        // Static methods only.
    }

    /// @param config The user's settings, or null for the defaults.
    /// @return Each class that hides something, with true if it applies.
    static Map<String, Boolean> hideClasses(final AskStroomAiConfig config) {
        final Map<String, Boolean> classes = new LinkedHashMap<>();
        classes.put(HIDE_TOOL_CALLS_CLASS, !NullSafe.getOrElse(config, AskStroomAiConfig::isShowToolCalls,
                AskStroomAiConfig.DEFAULT_SHOW_TOOL_CALLS));
        classes.put(HIDE_REASONING_CLASS, !NullSafe.getOrElse(config, AskStroomAiConfig::isShowReasoning,
                AskStroomAiConfig.DEFAULT_SHOW_REASONING));
        classes.put(HIDE_REQUEST_DETAIL_CLASS, !NullSafe.getOrElse(config, AskStroomAiConfig::isEnableDebugDetail,
                AskStroomAiConfig.DEFAULT_ENABLE_DEBUG_DETAIL));
        return classes;
    }

    /// Shows the conversation's activity as the user's settings say.
    ///
    /// @param conversation The element holding the conversation.
    /// @param config       The user's settings, or null for the defaults.
    static void apply(final Element conversation, final AskStroomAiConfig config) {
        hideClasses(config).forEach((className, hide) -> {
            if (hide) {
                conversation.addClassName(className);
            } else {
                conversation.removeClassName(className);
            }
        });
    }
}
