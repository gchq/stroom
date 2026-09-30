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
import stroom.docref.DocRef;
import stroom.query.language.functions.AiProvider;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValErr;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * Backs the {@code ai()} StroomQL function. The model is resolved and read as the querying user, so a user
 * can only ask a model that they are permitted to use.
 */
@Singleton
public class AiProviderImpl implements AiProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AiProviderImpl.class);

    private final Provider<AiService> aiServiceProvider;

    @Inject
    public AiProviderImpl(final Provider<AiService> aiServiceProvider) {
        this.aiServiceProvider = aiServiceProvider;
    }

    @Override
    public Val chat(final String modelNameOrUuid, final String systemPrompt, final String message) {
        try {
            final AiService aiService = aiServiceProvider.get();
            final Optional<DocRef> optionalModelRef = aiService.findModelByNameOrUuid(modelNameOrUuid);
            if (optionalModelRef.isEmpty()) {
                // Not being able to see the model is indistinguishable from it not existing, so say both.
                return ValErr.create("AI model not found with name or UUID '" + modelNameOrUuid
                                     + "'. You might not have permission to use this model");
            }

            final String answer = aiService.chat(optionalModelRef.get(), systemPrompt, message);
            return answer == null
                    ? ValNull.INSTANCE
                    : ValString.create(answer);

        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            return ValErr.create(e.getMessage());
        }
    }
}
