/*
 * Copyright 2016 Crown Copyright
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class AskStroomAiRequest {

    @JsonProperty
    private final AiChat aiChat;
    @JsonProperty
    private final AskStroomAiConfig config;
    @JsonProperty
    private final AskStroomAiContext context;
    @JsonProperty
    private final String message;
    @JsonProperty
    private final AiAgentMode mode;
    @JsonProperty
    private final AiTabSnapshot tabSnapshot;
    @JsonProperty
    private final String localZoneId;

    /// A request in the chat's current mode.
    public AskStroomAiRequest(final AiChat aiChat,
                              final AskStroomAiConfig config,
                              final AskStroomAiContext context,
                              final String message) {
        this(aiChat, config, context, message, null);
    }

    /// @param mode How the agent works in the chat from now on, or null to keep the chat's current mode.
    public AskStroomAiRequest(final AiChat aiChat,
                              final AskStroomAiConfig config,
                              final AskStroomAiContext context,
                              final String message,
                              final AiAgentMode mode) {
        this(aiChat, config, context, message, mode, null);
    }

    /// @param mode        How the agent works in the chat from now on, or null to keep the chat's current mode.
    /// @param tabSnapshot What the user sees in the attached tab, sent with a question, or null if no tab is shared.
    public AskStroomAiRequest(final AiChat aiChat,
                              final AskStroomAiConfig config,
                              final AskStroomAiContext context,
                              final String message,
                              final AiAgentMode mode,
                              final AiTabSnapshot tabSnapshot) {
        this(aiChat, config, context, message, mode, tabSnapshot, null);
    }

    /// @param mode        How the agent works in the chat from now on, or null to keep the chat's current mode.
    /// @param tabSnapshot What the user sees in the attached tab, sent with a question, or null if no tab is shared.
    /// @param localZoneId The time zone id of the user's browser (e.g. 'Australia/Canberra'), or null if not known.
    @JsonCreator
    public AskStroomAiRequest(@JsonProperty("aiChat") final AiChat aiChat,
                              @JsonProperty("config") final AskStroomAiConfig config,
                              @JsonProperty("context") final AskStroomAiContext context,
                              @JsonProperty("message") final String message,
                              @JsonProperty("mode") final AiAgentMode mode,
                              @JsonProperty("tabSnapshot") final AiTabSnapshot tabSnapshot,
                              @JsonProperty("localZoneId") final String localZoneId) {
        this.aiChat = aiChat;
        this.config = config;
        this.context = context;
        this.message = message;
        this.mode = mode;
        this.tabSnapshot = tabSnapshot;
        this.localZoneId = localZoneId;
    }

    public AiChat getAiChat() {
        return aiChat;
    }

    public AskStroomAiConfig getConfig() {
        return config;
    }

    public AskStroomAiContext getContext() {
        return context;
    }

    public String getMessage() {
        return message;
    }

    /// @return How the agent works in the chat from now on, or null to keep the chat's current mode.
    public AiAgentMode getMode() {
        return mode;
    }

    /// @return What the user sees in the attached tab, sent with a question, or null if no tab is shared. Only its
    /// hash is sent if it hasn't changed since it was last sent to the chat.
    public AiTabSnapshot getTabSnapshot() {
        return tabSnapshot;
    }

    /// @return The time zone id of the user's browser, which the AI tools take for the 'local' time zone (and for
    /// the user's preference to use the browser's zone), or null if not known.
    public String getLocalZoneId() {
        return localZoneId;
    }
}
