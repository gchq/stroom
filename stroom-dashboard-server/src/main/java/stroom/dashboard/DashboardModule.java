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
import stroom.explorer.ExplorerActionHandler;
import stroom.importexport.ImportExportActionHandler;
import stroom.task.TaskHandler;

public class DashboardModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(QueryService.class).to(QueryServiceImpl.class);
        bind(DashboardService.class).to(DashboardServiceImpl.class);

        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
        taskHandlerBinder.addBinding().to(stroom.dashboard.DownloadQueryActionHandler.class);
        taskHandlerBinder.addBinding().to(stroom.dashboard.DownloadSearchResultsHandler.class);
        taskHandlerBinder.addBinding().to(stroom.dashboard.FetchExpressionFieldsHandler.class);
        taskHandlerBinder.addBinding().to(stroom.dashboard.FetchTimeZonesHandler.class);
        taskHandlerBinder.addBinding().to(stroom.dashboard.FetchVisualisationHandler.class);
        taskHandlerBinder.addBinding().to(stroom.dashboard.SearchBusPollActionHandler.class);
        taskHandlerBinder.addBinding().to(stroom.dashboard.ValidateExpressionHandler.class);

        final Multibinder<ExplorerActionHandler> explorerActionHandlerBinder = Multibinder.newSetBinder(binder(), ExplorerActionHandler.class);
        explorerActionHandlerBinder.addBinding().to(stroom.dashboard.DashboardServiceImpl.class);

        final Multibinder<ImportExportActionHandler> importExportActionHandlerBinder = Multibinder.newSetBinder(binder(), ImportExportActionHandler.class);
        importExportActionHandlerBinder.addBinding().to(stroom.dashboard.DashboardServiceImpl.class);
    }
}