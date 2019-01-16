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
import com.google.inject.multibindings.Multibinder;
import stroom.entity.EntityTypeBinder;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.ImportExportActionHandler;
import stroom.task.api.TaskHandlerBinder;

public class FeedModule extends AbstractModule {
    @Override
    protected void configure() {
        TaskHandlerBinder.create(binder())
            .bind(stroom.feed.shared.FetchSupportedEncodingsAction.class, stroom.feed.FetchSupportedEncodingsActionHandler.class);

        bind(FeedStore.class).to(FeedStoreImpl.class);
        bind(FeedDocCache.class).to(FeedDocCacheImpl.class);
        bind(RemoteFeedService.class).to(RemoteFeedServiceImpl.class);

        final Multibinder<ExplorerActionHandler> explorerActionHandlerBinder = Multibinder.newSetBinder(binder(), ExplorerActionHandler.class);
        explorerActionHandlerBinder.addBinding().to(FeedStoreImpl.class);

        final Multibinder<ImportExportActionHandler> importExportActionHandlerBinder = Multibinder.newSetBinder(binder(), ImportExportActionHandler.class);
        importExportActionHandlerBinder.addBinding().to(FeedStoreImpl.class);

        EntityTypeBinder.create(binder())
                .bind(FeedDoc.DOCUMENT_TYPE, FeedStoreImpl.class);
    }
}