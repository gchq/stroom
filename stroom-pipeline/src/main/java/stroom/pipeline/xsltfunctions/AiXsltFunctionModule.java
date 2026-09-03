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

package stroom.pipeline.xsltfunctions;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import net.sf.saxon.value.SequenceType;

/**
 * The XSLT functions that need an {@code AiService}, so they are only available where the AI module is
 * installed, i.e. not in the headless CLI.
 */
public class AiXsltFunctionModule extends AbstractXsltFunctionModule {

    @Override
    protected void configureFunctions() {
        bindFunction(AskAiFunction.class);
    }

    private static class AskAiFunction extends StroomExtensionFunctionDefinition<AskAi> {

        @Inject
        AskAiFunction(final Provider<AskAi> functionCallProvider) {
            super(
                    AskAi.FUNCTION_NAME,
                    2,
                    3,
                    new SequenceType[]{
                            SequenceType.SINGLE_STRING,
                            SequenceType.SINGLE_STRING,
                            SequenceType.OPTIONAL_STRING},
                    SequenceType.OPTIONAL_STRING,
                    functionCallProvider);
        }
    }
}
