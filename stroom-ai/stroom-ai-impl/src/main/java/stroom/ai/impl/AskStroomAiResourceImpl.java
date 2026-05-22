/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiChatPollRequest;
import stroom.ai.shared.AiChatPollResponse;
import stroom.ai.shared.AskStroomAIConfig;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.ai.shared.AskStroomAiResource;
import stroom.ai.shared.AskStroomAiResponse;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.util.shared.FindNamedEntityCriteria;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;

@AutoLogged
class AskStroomAiResourceImpl implements AskStroomAiResource {

    private final Provider<AskStroomAIService> askStroomAIServiceProvider;

    @Inject
    AskStroomAiResourceImpl(final Provider<AskStroomAIService> askStroomAIServiceProvider) {
        this.askStroomAIServiceProvider = askStroomAIServiceProvider;
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public AskStroomAiResponse askStroomAi(final AskStroomAiRequest request) {
        return askStroomAIServiceProvider.get().askStroomAi(request);
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public AskStroomAIConfig getDefaultConfig() {
        return askStroomAIServiceProvider.get().getDefaultConfig();
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public Boolean setDefaultAskStroomAIConfig(final AskStroomAIConfig config) {
        return askStroomAIServiceProvider.get().setDefaultAskStroomAIConfig(config);
    }

    @Override
    public AiChat createChat() {
        return askStroomAIServiceProvider.get().createChat();
    }

    @Override
    public ResultPage<AiChat> listChats(final FindNamedEntityCriteria criteria) {
        return askStroomAIServiceProvider.get().listChats(criteria);
    }

    @Override
    public AiChat getChat(final int chatId) {
        return askStroomAIServiceProvider.get().getChat(chatId);
    }

    @AutoLogged(OperationType.DELETE)
    @Override
    public Boolean deleteChat(final int chatId) {
        askStroomAIServiceProvider.get().deleteChat(chatId);
        return true;
    }

    @Override
    public List<AiChatMessage> getMessages(final int chatId) {
        return askStroomAIServiceProvider.get().getMessages(chatId);
    }

    @AutoLogged(OperationType.UPDATE)
    @Override
    public Boolean updateChatTitle(final int chatId, final String title) {
        askStroomAIServiceProvider.get().updateChatTitle(chatId, title);
        return true;
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public AiChatPollResponse pollMessages(final int chatId, final AiChatPollRequest request) {
        return askStroomAIServiceProvider.get().pollMessages(chatId, request);
    }

    @AutoLogged(OperationType.UPDATE)
    @Override
    public Boolean cancelProcessing(final int chatId) {
        return askStroomAIServiceProvider.get().cancelProcessing(chatId);
    }
}
