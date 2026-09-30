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

package stroom.query.language.functions;

/**
 * Asks a chat model a question. Implemented outside this module so that the expression language does not
 * have to know anything about how models are configured or called.
 */
public interface AiProvider {

    /**
     * Ask the model identified by {@code modelNameOrUuid} the supplied question.
     *
     * @param modelNameOrUuid The name or UUID of the OpenAI model document to use.
     * @param systemPrompt    The system prompt to send ahead of the message. May be null.
     * @param message         The message to ask the model.
     * @return Never null. The model's answer, {@link ValNull#INSTANCE} if it had nothing to say, or a
     * {@link ValErr} describing the problem, so that a failure surfaces in the expression result rather
     * than being indistinguishable from an absent value.
     */
    Val chat(String modelNameOrUuid, String systemPrompt, String message);
}
