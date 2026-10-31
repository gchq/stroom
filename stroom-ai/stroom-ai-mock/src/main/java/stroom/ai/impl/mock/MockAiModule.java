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

package stroom.ai.impl.mock;

import stroom.ai.api.AiService;
import stroom.ai.api.OpenAIModelStore;
import stroom.ai.api.TableSummariser;
import stroom.ai.impl.AiProviderImpl;
import stroom.ai.impl.OpenAIModelStoreImpl;
import stroom.ai.impl.TableSummariserImpl;
import stroom.ai.impl.tools.StroomTool;
import stroom.query.language.functions.AiProvider;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

public class MockAiModule extends AbstractModule {

    @Override
    protected void configure() {
        // Services
        bind(AiService.class).to(MockAiService.class);
        bind(TableSummariser.class).to(TableSummariserImpl.class);

        // Backs the ai() StroomQL function, see ExpressionContextFactory.
        bind(AiProvider.class).to(AiProviderImpl.class);

        // No built-in chat tools, as they need the AI database, but an empty set, so anything wiring the AI impl
        // can resolve BuiltInTools.
        Multibinder.newSetBinder(binder(), StroomTool.class);

        // OpenAI Model
        bind(OpenAIModelStore.class).to(OpenAIModelStoreImpl.class);
    }
}
