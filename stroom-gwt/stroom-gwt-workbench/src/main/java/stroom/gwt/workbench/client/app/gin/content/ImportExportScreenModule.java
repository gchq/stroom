/*
 * Copyright 2026 Crown Copyright
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

package stroom.gwt.workbench.client.app.gin.content;

import stroom.importexport.client.presenter.DependenciesInfoPresenter;
import stroom.importexport.client.presenter.DependenciesInfoPresenter.DependenciesInfoProxy;
import stroom.importexport.client.presenter.DependenciesInfoPresenter.DependenciesInfoView;
import stroom.importexport.client.presenter.DependenciesInfoViewImpl;
import stroom.importexport.client.presenter.DependenciesTabPresenter;
import stroom.importexport.client.presenter.ExportConfigPresenter;
import stroom.importexport.client.presenter.ExportConfigPresenter.ExportConfigView;
import stroom.importexport.client.presenter.ExportConfigPresenter.ExportProxy;
import stroom.importexport.client.presenter.ImportConfigConfirmPresenter;
import stroom.importexport.client.presenter.ImportConfigConfirmPresenter.ImportConfigConfirmView;
import stroom.importexport.client.presenter.ImportConfigConfirmPresenter.ImportConfirmProxy;
import stroom.importexport.client.presenter.ImportConfigPresenter;
import stroom.importexport.client.presenter.ImportConfigPresenter.ImportConfigView;
import stroom.importexport.client.presenter.ImportConfigPresenter.ImportProxy;
import stroom.importexport.client.view.DependenciesTabViewImpl;
import stroom.importexport.client.view.ExportConfigViewImpl;
import stroom.importexport.client.view.ImportConfigConfirmViewImpl;
import stroom.importexport.client.view.ImportConfigViewImpl;

import com.google.inject.Provides;
import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `ImportExportConfigModule`
/// (`stroom/importexport/client/gin/ImportExportConfigModule.java`), without its plugins and app services.
/// Presenters that Stroom binds with a GWTP proxy are bound as presenter widgets with a null
/// proxy: a story shows one by registering it as the handler of its event, as the proxy would.
public class ImportExportScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bindPresenterWidget(ImportConfigPresenter.class,
                ImportConfigView.class,
                ImportConfigViewImpl.class);
        bindPresenterWidget(ImportConfigConfirmPresenter.class,
                ImportConfigConfirmView.class,
                ImportConfigConfirmViewImpl.class);
        bindPresenterWidget(ExportConfigPresenter.class,
                ExportConfigView.class,
                ExportConfigViewImpl.class);
        bindPresenterWidget(DependenciesInfoPresenter.class,
                DependenciesInfoView.class,
                DependenciesInfoViewImpl.class);
        bindPresenterWidget(DependenciesTabPresenter.class,
                DependenciesTabPresenter.DependenciesTabView.class,
                DependenciesTabViewImpl.class);
    }

    /// @return No proxy, see the class description.
    @Provides
    DependenciesInfoProxy provideDependenciesInfoProxy() {
        return null;
    }

    /// @return No proxy, see the class description.
    @Provides
    ImportProxy provideImportProxy() {
        return null;
    }

    /// @return No proxy, see the class description.
    @Provides
    ImportConfirmProxy provideImportConfirmProxy() {
        return null;
    }

    /// @return No proxy, see the class description.
    @Provides
    ExportProxy provideExportProxy() {
        return null;
    }
}
