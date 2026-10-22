/*
 * Copyright 2018 Crown Copyright
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
import stroom.ai.api.OpenAIModelStore;
import stroom.ai.api.TableSummariser;
import stroom.ai.impl.tools.BuiltInToolsModule;
import stroom.docstore.api.DocumentStoreBinder;
import stroom.job.api.ScheduledJobsBinder;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.query.language.functions.AiProvider;
import stroom.util.RunnableWrapper;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.shared.scheduler.CronExpressions;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpSessionListener;

public class AiModule extends AbstractModule {

    @Override
    protected void configure() {
        // Services
        bind(AiService.class).to(AiServiceImpl.class);
        bind(AiAttachmentFileStore.class).asEagerSingleton();
        bind(TableSummariser.class).to(TableSummariserImpl.class);

        // Backs the ai() StroomQL function, see ExpressionContextFactory.
        bind(AiProvider.class).to(AiProviderImpl.class);
        bind(UserActionLookup.class).to(UserActionLookupImpl.class);
        // How a chat asks whether a tool call or change may go ahead, and asks the user to approve it.
        bind(ToolConsentGate.class).to(ChatConsentGate.class);
        bind(CurrentToolGrants.class).to(AiToolPolicyService.class);

        // Built-in chat tools (get_current_date, ...).
        install(new BuiltInToolsModule());

        // OpenAI Model
        DocumentStoreBinder.create(binder())
                .bind(OpenAIModelDoc.TYPE, OpenAIModelStore.class, OpenAIModelStoreImpl.class);

        RestResourcesBinder.create(binder())
                .bind(OpenAIModelResourceImpl.class)
                .bind(AskStroomAiResourceImpl.class)
                .bind(AiToolPolicyResourceImpl.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(AiRetentionJob.class, builder -> builder
                        .name(AiRetention.JOB_NAME)
                        .description("Removes 'Ask Stroom AI' workspace files and chats that are past their "
                                     + "retention age, and the chats and memory of deleted users")
                        .cronSchedule(CronExpressions.EVERY_HOUR.getExpression()));

        // Stops agent runs when the session that started them ends.
        GuiceUtil.buildMultiBinder(binder(), HttpSessionListener.class)
                .addBinding(AiChatRunSessionListener.class);
    }


    // --------------------------------------------------------------------------------


    private static class AiRetentionJob extends RunnableWrapper {

        @Inject
        AiRetentionJob(final AiRetention aiRetention) {
            super(aiRetention::exec);
        }
    }
}
