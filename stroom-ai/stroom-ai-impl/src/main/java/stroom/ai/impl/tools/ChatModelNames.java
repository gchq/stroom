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

package stroom.ai.impl.tools;

import stroom.ai.impl.AiDao;
import stroom.docref.DocRef;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Optional;

/// The model a Stroom AI chat runs on, by name, for what records who made a change (a document's version control
/// names an external client by its own name; a chat by its model, asked for by the user, rather than the generic
/// "Stroom AI chat").
public class ChatModelNames {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ChatModelNames.class);

    private final Provider<AiDao> aiDaoProvider;
    private final DocumentAccess documentAccess;

    /// @param aiDaoProvider  Says which model a chat last ran on.
    /// @param documentAccess Reads its model document, checking View.
    @Inject
    public ChatModelNames(final Provider<AiDao> aiDaoProvider, final DocumentAccess documentAccess) {
        this.aiDaoProvider = aiDaoProvider;
        this.documentAccess = documentAccess;
    }

    /// @param chatId A chat.
    /// @return The model it last ran on: its model id (e.g. `claude-opus-5-5`), else its model document's name; empty
    /// when the chat hasn't run, or its model document can't be read.
    public Optional<String> of(final int chatId) {
        try {
            final String uuid = aiDaoProvider.get().getUsage(chatId).modelDocUuid();
            if (NullSafe.isBlankString(uuid)) {
                return Optional.empty();
            }
            return documentAccess.read(new DocRef(OpenAIModelDoc.TYPE, uuid), OpenAIModelDoc.class)
                    .map(doc -> NullSafe.nonBlankStringElse(doc.getModelId(), doc.getName()))
                    .filter(NullSafe::isNonBlankString);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No model name for chat {}: {}", chatId, e.getMessage()));
            return Optional.empty();
        }
    }
}
