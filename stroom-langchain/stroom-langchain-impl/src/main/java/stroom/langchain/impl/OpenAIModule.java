/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.langchain.impl;

import stroom.docstore.api.ContentIndexable;
import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.job.api.ScheduledJobsBinder;
import stroom.langchain.api.ChatMemoryService;
import stroom.langchain.api.OpenAIModelStore;
import stroom.langchain.api.OpenAIService;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.util.RunnableWrapper;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.shared.scheduler.CronExpressions;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;

public class OpenAIModule extends AbstractModule {

    @Override
    protected void configure() {
        // Services

        bind(OpenAIService.class).to(OpenAIServiceImpl.class);
        bind(ChatMemoryService.class).to(ChatMemoryServiceImpl.class);

        // Jobs
        ScheduledJobsBinder.create(binder())
                .bindJobTo(ChatMemoryPrune.class, builder -> builder
                        .name("Chat Memory Prune")
                        .description("Job to remove old LLM chat memory entries")
                        .cronSchedule(CronExpressions.EVERY_HOUR.getExpression()));

        // OpenAI Model
        bind(OpenAIModelStore.class).to(OpenAIModelStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(OpenAIModelStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(OpenAIModelStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ContentIndexable.class)
                .addBinding(OpenAIModelStoreImpl.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(OpenAIModelDoc.TYPE, OpenAIModelStoreImpl.class);

        RestResourcesBinder.create(binder())
                .bind(OpenAIModelResourceImpl.class);
    }

    // --------------------------------------------------------------------------------

    private static class ChatMemoryPrune extends RunnableWrapper {

        @Inject
        ChatMemoryPrune(final ChatMemoryService chatMemoryService) {
            super(chatMemoryService::pruneChatMemory);
        }
    }
}
