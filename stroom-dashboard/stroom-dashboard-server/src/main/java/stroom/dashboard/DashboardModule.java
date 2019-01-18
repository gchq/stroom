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

package stroom.dashboard;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.DownloadQueryAction;
import stroom.dashboard.shared.DownloadSearchResultsAction;
import stroom.dashboard.shared.FetchTimeZonesAction;
import stroom.dashboard.shared.FetchVisualisationAction;
import stroom.dashboard.shared.SearchBusPollAction;
import stroom.dashboard.shared.ValidateExpressionAction;
import stroom.entity.EntityTypeBinder;
import stroom.entity.shared.Clearable;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.ImportExportActionHandler;
import stroom.task.api.TaskHandlerBinder;

public class DashboardModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(QueryService.class).to(QueryServiceImpl.class);
        bind(DashboardStore.class).to(DashboardStoreImpl.class);

        final Multibinder<Clearable> clearableBinder = Multibinder.newSetBinder(binder(), Clearable.class);
        clearableBinder.addBinding().to(ActiveQueriesManager.class);

        TaskHandlerBinder.create(binder())
                .bind(DownloadQueryAction.class, DownloadQueryActionHandler.class)
                .bind(DownloadSearchResultsAction.class, DownloadSearchResultsHandler.class)
                .bind(FetchTimeZonesAction.class, FetchTimeZonesHandler.class)
                .bind(FetchVisualisationAction.class, FetchVisualisationHandler.class)
                .bind(SearchBusPollAction.class, SearchBusPollActionHandler.class)
                .bind(ValidateExpressionAction.class, ValidateExpressionHandler.class);

        final Multibinder<ExplorerActionHandler> explorerActionHandlerBinder = Multibinder.newSetBinder(binder(), ExplorerActionHandler.class);
        explorerActionHandlerBinder.addBinding().to(stroom.dashboard.DashboardStoreImpl.class);

        final Multibinder<ImportExportActionHandler> importExportActionHandlerBinder = Multibinder.newSetBinder(binder(), ImportExportActionHandler.class);
        importExportActionHandlerBinder.addBinding().to(stroom.dashboard.DashboardStoreImpl.class);

        EntityTypeBinder.create(binder())
                .bind(DashboardDoc.DOCUMENT_TYPE, DashboardStoreImpl.class);
    }
}