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

import stroom.ai.api.AiService;
import stroom.ai.impl.tools.ModelSideCalls;
import stroom.ai.impl.tools.ToolRunContext;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.task.api.TaskTerminatedException;
import stroom.util.shared.NullSafe;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/// A chat's side calls ([ModelSideCalls]): to the run's configured model, as step and compaction summaries are
/// ([ModelStreamer#callUnseen]), so a stop is seen while the model answers, and their tokens added to the run's
/// usage, as a summary's are.
@Singleton
class ChatModelSideCalls implements ModelSideCalls.ForChat {

    private final AiService aiService;
    private final ModelStreamer modelStreamer;
    private final Provider<AiChatRunManager> runManagerProvider;

    /// @param aiService          Gives the run's model as one that streams.
    /// @param modelStreamer      Calls the model unseen, as summaries are.
    /// @param runManagerProvider Adds the calls' tokens to the run's usage.
    @Inject
    ChatModelSideCalls(final AiService aiService,
                       final ModelStreamer modelStreamer,
                       final Provider<AiChatRunManager> runManagerProvider) {
        this.aiService = aiService;
        this.modelStreamer = modelStreamer;
        this.runManagerProvider = runManagerProvider;
    }

    @Override
    public ModelSideCalls forRun(final int chatId, final ToolRunContext run) {
        if (run == null || run.getChatModel() == null) {
            return ModelSideCalls.NONE;
        }
        return new ModelSideCalls() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public String via() {
                return "the chat's model" + NullSafe.getOrElse(run.getModelDoc(), doc -> " (" + doc.getName() + ")",
                        "");
            }

            @Override
            public Answer ask(final Question question) {
                return call(chatId, run, question);
            }
        };
    }

    private ModelSideCalls.Answer call(final int chatId,
                                       final ToolRunContext run,
                                       final ModelSideCalls.Question question) {
        final ChatRequest request = ChatRequest.builder()
                .messages(new SystemMessage(question.system()), new UserMessage(question.user()))
                .maxOutputTokens(question.maxTokens() > 0
                        ? question.maxTokens()
                        : null)
                .build();
        final OpenAIModelDoc modelDoc = run.getModelDoc();
        final ChatResponse response;
        try {
            final StreamingChatModel streamingModel = modelDoc != null
                    ? aiService.getStreamingChatModel(modelDoc)
                    : null;
            response = modelStreamer.callUnseen(run.getChatModel(), streamingModel, request, chatId,
                    run.getCancelled());
        } catch (final TaskTerminatedException e) {
            throw e;
        } catch (final RuntimeException e) {
            throw new ModelSideCalls.SideCallException(ChatCompactor.reason(e), e);
        }
        final TokenUsage usage = NullSafe.get(response, ChatResponse::tokenUsage);
        final long input = NullSafe.getOrElse(usage, TokenUsage::inputTokenCount, 0);
        final long output = NullSafe.getOrElse(usage, TokenUsage::outputTokenCount, 0);
        if (usage != null) {
            // The tokens count towards the run, as a summary's do.
            runManagerProvider.get().addUsage(chatId, input, output, 0);
        }
        final String text = NullSafe.get(response, ChatResponse::aiMessage, AiMessage::text);
        if (NullSafe.isBlankString(text)) {
            throw new ModelSideCalls.SideCallException("the model gave an empty answer");
        }
        return new ModelSideCalls.Answer(text.strip(), input, output);
    }
}
