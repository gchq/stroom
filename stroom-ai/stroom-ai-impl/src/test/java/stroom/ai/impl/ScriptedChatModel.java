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

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/// A model for tests that replays a fixed script: each call it is given gets the next reply in the script (an
/// answer, or tool calls with arguments, with what the model says and thinks alongside), and every request it is
/// given is kept, so a test can check exactly what the model was sent.
///
/// A call made after the script has run out fails the test, rather than the model making something up, so a run
/// that asks the model more often than the script expects shows up at once.
final class ScriptedChatModel implements ChatModel {

    private final List<Reply> script;
    private final List<ChatRequest> requests = new ArrayList<>();

    /// @param replies The replies, in the order the model gives them.
    ScriptedChatModel(final Reply... replies) {
        this.script = List.of(replies);
    }

    /// @param text What the model answers.
    /// @return A reply that answers without calling a tool.
    static Reply answer(final String text) {
        return new Reply(text, null, List.of(), null);
    }

    /// @param text  What the model says as it makes the calls, or null.
    /// @param calls The tool calls.
    /// @return A reply that calls tools.
    static Reply toolCalls(final String text, final ToolExecutionRequest... calls) {
        return new Reply(text, null, Arrays.asList(calls), null);
    }

    /// @param id        The call's id.
    /// @param name      The tool.
    /// @param arguments The call's arguments, as JSON.
    /// @return A tool call.
    static ToolExecutionRequest call(final String id, final String name, final String arguments) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name(name)
                .arguments(arguments)
                .build();
    }

    @Override
    public synchronized ChatResponse doChat(final ChatRequest chatRequest) {
        requests.add(chatRequest);
        if (requests.size() > script.size()) {
            throw new AssertionError("The model was called " + requests.size() + " times, but the script only has "
                                     + script.size() + " replies");
        }
        final Reply reply = script.get(requests.size() - 1);
        if (reply.beforeReplying() != null) {
            reply.beforeReplying().run();
        }
        final AiMessage aiMessage = AiMessage.builder()
                .text(reply.text())
                .thinking(reply.thinking())
                .toolExecutionRequests(reply.calls())
                .build();
        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .finishReason(reply.calls().isEmpty()
                        ? FinishReason.STOP
                        : FinishReason.TOOL_EXECUTION)
                .build();
    }

    /// @return Every request the model has been given so far, in order.
    synchronized List<ChatRequest> requests() {
        return List.copyOf(requests);
    }

    /// @return True if every reply in the script has been given.
    synchronized boolean isFinished() {
        return requests.size() == script.size();
    }


    // --------------------------------------------------------------------------------


    /// One reply in a script.
    ///
    /// @param text           What the model says, or null.
    /// @param thinking       What the model thinks first, or null.
    /// @param calls          The tools it calls, if any.
    /// @param beforeReplying Done while the model is working on the reply (e.g. the user sends a message), or
    ///                       null.
    record Reply(String text,
                 String thinking,
                 List<ToolExecutionRequest> calls,
                 Runnable beforeReplying) {

        /// @param thinking What the model thinks before it replies.
        /// @return This reply, with the model's reasoning.
        Reply withThinking(final String thinking) {
            return new Reply(text, thinking, calls, beforeReplying);
        }

        /// @param action Done while the model works on the reply.
        /// @return This reply, doing the action first.
        Reply whileWorking(final Runnable action) {
            return new Reply(text, thinking, calls, action);
        }
    }
}
