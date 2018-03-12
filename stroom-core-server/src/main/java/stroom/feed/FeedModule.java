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
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import stroom.entity.CachingEntityManager;
import stroom.entity.EntityServiceBeanRegistry;
import stroom.entity.FindService;
import stroom.entity.shared.BaseCriteria;
import stroom.explorer.ExplorerActionHandler;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FindFeedCriteria;
import stroom.importexport.ImportExportActionHandler;
import stroom.importexport.ImportExportHelper;
import stroom.security.SecurityContext;
import stroom.spring.EntityManagerSupport;
import stroom.task.TaskHandler;

import javax.inject.Named;

import static com.google.inject.name.Names.named;

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

        final MapBinder<String, Object> entityServiceByTypeBinder = MapBinder.newMapBinder(binder(), String.class, Object.class);
        entityServiceByTypeBinder.addBinding(Feed.ENTITY_TYPE).to(stroom.feed.FeedServiceImpl.class);

        final Multibinder<FindService> findServiceBinder = Multibinder.newSetBinder(binder(), FindService.class);
        findServiceBinder.addBinding().to(stroom.feed.FeedServiceImpl.class);
    }

    @Provides
    @Named("cachedFeedService")
    public FeedService cachedFeedService(final CachingEntityManager entityManager,
                                         final EntityManagerSupport entityManagerSupport,
                                         final ImportExportHelper importExportHelper,
                                         final SecurityContext securityContext) {
        return new FeedServiceImpl(entityManager, entityManagerSupport, importExportHelper, securityContext);
    }
}