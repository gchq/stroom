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

package stroom.feed;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import stroom.entity.CachingEntityManager;
import stroom.explorer.ExplorerActionHandler;
import stroom.importexport.ImportExportActionHandler;
import stroom.importexport.ImportExportHelper;
import stroom.security.SecurityContext;
import stroom.task.TaskHandler;

import javax.inject.Named;

public class FeedModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(FeedService.class).to(FeedServiceImpl.class);
        bind(RemoteFeedService.class).annotatedWith(Names.named("remoteFeedService")).to(RemoteFeedServiceImpl.class);

        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
        taskHandlerBinder.addBinding().to(stroom.feed.FetchSupportedEncodingsActionHandler.class);

        final Multibinder<ExplorerActionHandler> explorerActionHandlerBinder = Multibinder.newSetBinder(binder(), ExplorerActionHandler.class);
        explorerActionHandlerBinder.addBinding().to(stroom.feed.FeedServiceImpl.class);

        final Multibinder<ImportExportActionHandler> importExportActionHandlerBinder = Multibinder.newSetBinder(binder(), ImportExportActionHandler.class);
        importExportActionHandlerBinder.addBinding().to(stroom.feed.FeedServiceImpl.class);
    }

    @Provides
    @Named("cachedFeedService")
    public FeedService cachedFeedService(final CachingEntityManager entityManager,
                                         final ImportExportHelper importExportHelper,
                                         final SecurityContext securityContext) {
        return new FeedServiceImpl(entityManager, importExportHelper, securityContext);
    }
}