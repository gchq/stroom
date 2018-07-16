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

package stroom.importexport;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.task.TaskHandler;

public class ImportExportHandlerModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(DependencyService.class).to(DependencyServiceImpl.class);

        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
        taskHandlerBinder.addBinding().to(ExportConfigHandler.class);
        taskHandlerBinder.addBinding().to(FetchDependenciesHandler.class);
        taskHandlerBinder.addBinding().to(ImportConfigConfirmationHandler.class);
        taskHandlerBinder.addBinding().to(ImportConfigHandler.class);
    }
}