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

import stroom.core.client.gin.PluginModule;
import stroom.importexport.client.DependenciesPlugin;
import stroom.importexport.client.ExportConfigPlugin;
import stroom.importexport.client.ImportConfigPlugin;
import stroom.importexport.client.presenter.DependenciesInfoPresenter;
import stroom.importexport.client.presenter.DependenciesInfoPresenter.DependenciesInfoProxy;
import stroom.importexport.client.presenter.DependenciesInfoPresenter.DependenciesInfoView;
import stroom.importexport.client.presenter.DependenciesInfoViewImpl;
import stroom.importexport.client.presenter.DependenciesTabPresenter;
import stroom.importexport.client.presenter.ExportConfigPresenter;
import stroom.importexport.client.presenter.ExportConfigPresenter.ExportConfigView;
import stroom.importexport.client.presenter.ImportConfigConfirmPresenter;
import stroom.importexport.client.presenter.ImportConfigConfirmPresenter.ImportConfigConfirmView;
import stroom.importexport.client.presenter.ImportConfigPresenter;
import stroom.importexport.client.presenter.ImportConfigPresenter.ImportConfigView;
import stroom.importexport.client.view.DependenciesTabViewImpl;
import stroom.importexport.client.view.ExportConfigViewImpl;
import stroom.importexport.client.view.ImportConfigConfirmViewImpl;
import stroom.importexport.client.view.ImportConfigViewImpl;

public class ImportExportConfigModule extends PluginModule {

    @Override
    protected void configure() {
        // Import.
        bindPlugin(ImportConfigPlugin.class);
        bind(ImportConfigPresenter.ImportProxy.class).asEagerSingleton();
        bindPresenterWidget(
                ImportConfigPresenter.class,
                ImportConfigView.class,
                ImportConfigViewImpl.class);
        bind(ImportConfigConfirmPresenter.ImportConfirmProxy.class).asEagerSingleton();
        bindPresenterWidget(
                ImportConfigConfirmPresenter.class,
                ImportConfigConfirmView.class,
                ImportConfigConfirmViewImpl.class);

        // Export.
        bindPlugin(ExportConfigPlugin.class);
        bind(ExportConfigPresenter.ExportProxy.class).asEagerSingleton();
        bindPresenterWidget(
                ExportConfigPresenter.class,
                ExportConfigView.class,
                ExportConfigViewImpl.class);

        // Dependencies.
        bindPlugin(DependenciesPlugin.class);
        bindPresenter(
                DependenciesInfoPresenter.class,
                DependenciesInfoView.class,
                DependenciesInfoViewImpl.class,
                DependenciesInfoProxy.class);

        bindPresenterWidget(
                DependenciesTabPresenter.class,
                DependenciesTabPresenter.DependenciesTabView.class,
                DependenciesTabViewImpl.class);
    }
}
