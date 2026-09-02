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

import stroom.query.language.functions.ref.StoredValues;

import java.util.Objects;
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = Ai.NAME,
        commonCategory = FunctionCategory.AI,
        commonReturnType = ValString.class,
        commonReturnDescription = "The model's answer.",
        commonDescription = "Ask a chat model a question and return its answer. The model is asked once per " +
                            "value, so prefer to use this on a grouped or otherwise small set of rows.",
        signatures = {
                @FunctionSignature(
                        args = {
                                @FunctionArg(
                                        name = "model",
                                        description = "The name or UUID of the OpenAI Model document to use.",
                                        argType = ValString.class),
                                @FunctionArg(
                                        name = "message",
                                        description = "The message to ask the model.",
                                        argType = ValString.class)}),
                @FunctionSignature(
                        args = {
                                @FunctionArg(
                                        name = "model",
                                        description = "The name or UUID of the OpenAI Model document to use.",
                                        argType = ValString.class),
                                @FunctionArg(
                                        name = "message",
                                        description = "The message to ask the model.",
                                        argType = ValString.class),
                                @FunctionArg(
                                        name = "systemPrompt",
                                        description = "The system prompt to send to the model ahead of the " +
                                                      "message, e.g. to tell it what role to play.",
                                        argType = ValString.class)})})
class Ai extends AbstractManyChildFunction {

    static final String NAME = "ai";

    private final AiProvider aiProvider;

    public Ai(final ExpressionContext expressionContext, final String name) {
        super(name, 2, 3);
        this.aiProvider = expressionContext.getAiProvider();
        Objects.requireNonNull(aiProvider, "Null AI provider");
    }

    @Override
    protected Generator createGenerator(final Generator[] childGenerators) {
        return new Gen(aiProvider, childGenerators);
    }

    private static final class Gen extends AbstractManyChildGenerator {

        private final AiProvider aiProvider;

        Gen(final AiProvider aiProvider,
            final Generator[] childGenerators) {
            super(childGenerators);
            this.aiProvider = aiProvider;
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            try {
                final Val modelVal = childGenerators[0].eval(storedValues, childDataSupplier);
                if (!modelVal.type().isValue()) {
                    return modelVal;
                }
                final Val messageVal = childGenerators[1].eval(storedValues, childDataSupplier);
                if (!messageVal.type().isValue()) {
                    return messageVal;
                }

                String systemPrompt = null;
                if (childGenerators.length > 2) {
                    final Val systemPromptVal = childGenerators[2].eval(storedValues, childDataSupplier);
                    if (!systemPromptVal.type().isValue()) {
                        return systemPromptVal;
                    }
                    systemPrompt = systemPromptVal.toString();
                }

                return aiProvider.chat(modelVal.toString(), systemPrompt, messageVal.toString());

            } catch (final RuntimeException e) {
                return ValErr.create(e.getMessage());
            }
        }
    }
}
