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

import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class AiChatPollResponse {

    @JsonProperty
    private final List<AiChatMessage> newMessages;
    @JsonProperty
    private final List<AiChatAttachment> attachments;
    /**
     * The WORKING message as it stands now, or null if nothing is being processed. Reported on every
     * poll rather than only when new, because its text is updated in place as processing advances.
     */
    @JsonProperty
    private final AiChatMessage workingMessage;
    @JsonProperty
    private final boolean complete;
    /**
     * The chat's latest agent run, or null if it has never had one.
     */
    @JsonProperty
    private final AiChatRun run;
    /**
     * How much of the model's context window the chat uses, or null if it has not changed since the last poll.
     */
    @JsonProperty
    private final AiContextUsage contextUsage;
    /**
     * The answer and reasoning the model is writing now, as STREAMING_ANSWER and STREAMING_THINKING messages, or
     * an empty list. Reported on every poll, as they are updated in place.
     */
    @JsonProperty
    private final List<AiChatMessage> streamingMessages;
    /**
     * The messages the client asked to watch, as they now stand, or null.
     */
    @JsonProperty
    private final List<AiChatMessage> watchedMessages;

    @JsonCreator
    public AiChatPollResponse(@JsonProperty("newMessages") final List<AiChatMessage> newMessages,
                              @JsonProperty("attachments") final List<AiChatAttachment> attachments,
                              @JsonProperty("workingMessage") final AiChatMessage workingMessage,
                              @JsonProperty("complete") final Boolean complete,
                              @JsonProperty("run") final AiChatRun run,
                              @JsonProperty("contextUsage") final AiContextUsage contextUsage,
                              @JsonProperty("streamingMessages") final List<AiChatMessage> streamingMessages,
                              @JsonProperty("watchedMessages") final List<AiChatMessage> watchedMessages) {
        this.newMessages = newMessages;
        this.attachments = attachments;
        this.workingMessage = workingMessage;
        this.complete = Objects.requireNonNullElse(complete, false);
        this.run = run;
        this.contextUsage = contextUsage;
        this.streamingMessages = streamingMessages;
        this.watchedMessages = watchedMessages;
    }

    public List<AiChatMessage> getNewMessages() {
        return newMessages;
    }

    public List<AiChatAttachment> getAttachments() {
        return attachments;
    }

    public AiChatMessage getWorkingMessage() {
        return workingMessage;
    }

    /**
     * @return True if nothing more is coming: no agent run is active and no attachment is downloading.
     */
    public boolean isComplete() {
        return complete;
    }

    public AiChatRun getRun() {
        return run;
    }

    public AiContextUsage getContextUsage() {
        return contextUsage;
    }

    /**
     * @return The answer and reasoning being written now, or null/empty if none.
     */
    public List<AiChatMessage> getStreamingMessages() {
        return streamingMessages;
    }

    /**
     * @return The messages the client asked to watch, as they now stand, or null.
     */
    public List<AiChatMessage> getWatchedMessages() {
        return watchedMessages;
    }
}
