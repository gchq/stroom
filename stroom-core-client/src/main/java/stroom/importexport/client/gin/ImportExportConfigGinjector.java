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

package stroom.importexport.client.gin;

import stroom.importexport.client.ExportConfigPlugin;
import stroom.importexport.client.ImportConfigPlugin;
import stroom.importexport.client.presenter.DependenciesInfoPresenter;
import stroom.importexport.client.presenter.ExportConfigPresenter;
import stroom.importexport.client.presenter.ImportConfigConfirmPresenter;
import stroom.importexport.client.presenter.ImportConfigPresenter;

import com.google.gwt.inject.client.AsyncProvider;

public interface ImportExportConfigGinjector {

    AsyncProvider<ExportConfigPlugin> getExportPlugin();

    AsyncProvider<ImportConfigPlugin> getImportPlugin();

    AsyncProvider<ExportConfigPresenter> getExportPresenter();

    AsyncProvider<ImportConfigPresenter> getImportPresenter();

    AsyncProvider<ImportConfigConfirmPresenter> getImportConfirmPresenter();

    AsyncProvider<DependenciesInfoPresenter> getDependenciesInfoPresenter();
}
