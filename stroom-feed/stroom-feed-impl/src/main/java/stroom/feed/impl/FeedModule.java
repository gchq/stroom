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

package stroom.feed.impl;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.util.guice.GuiceUtil;
import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.event.logging.api.ObjectInfoProviderBinder;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.feed.api.FeedProperties;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.task.api.TaskHandlerBinder;
import stroom.util.shared.Clearable;

public class FeedModule extends AbstractModule {
    @Override
    protected void configure() {
        TaskHandlerBinder.create(binder())
            .bind(stroom.feed.shared.FetchSupportedEncodingsAction.class, FetchSupportedEncodingsActionHandler.class);

        bind(FeedStore.class).to(FeedStoreImpl.class);
        bind(FeedProperties.class).to(FeedPropertiesImpl.class);

        final Multibinder<ExplorerActionHandler> explorerActionHandlerBinder = Multibinder.newSetBinder(binder(), ExplorerActionHandler.class);
        explorerActionHandlerBinder.addBinding().to(FeedStoreImpl.class);

        final Multibinder<ImportExportActionHandler> importExportActionHandlerBinder = Multibinder.newSetBinder(binder(), ImportExportActionHandler.class);
        importExportActionHandlerBinder.addBinding().to(FeedStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class).addBinding(FeedDocCache.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(FeedDoc.DOCUMENT_TYPE, FeedStoreImpl.class);

        // Provide object info to the logging service.
        ObjectInfoProviderBinder.create(binder())
                .bind(FeedDoc.class, FeedDocObjectInfoProvider.class);
    }
}