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

package stroom.gwt.workbench.client.app.gin.dashboard;

import stroom.dashboard.client.embeddedquery.BasicEmbeddedQuerySettingsPresenter;
import stroom.dashboard.client.embeddedquery.BasicEmbeddedQuerySettingsPresenter.BasicEmbeddedQuerySettingsView;
import stroom.dashboard.client.embeddedquery.BasicEmbeddedQuerySettingsViewImpl;
import stroom.dashboard.client.embeddedquery.EmbeddedQueryPresenter;
import stroom.dashboard.client.embeddedquery.EmbeddedQueryPresenter.EmbeddedQueryView;
import stroom.dashboard.client.embeddedquery.EmbeddedQueryViewImpl;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `EmbeddedQueryModule`
/// (`stroom/dashboard/client/embeddedquery/gin/EmbeddedQueryModule.java`), without its plugins and app services.
/// Presenters that Stroom binds with a GWTP proxy are bound as presenter widgets with a null
/// proxy: a story shows one by registering it as the handler of its event, as the proxy would.
public class EmbeddedQueryScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bindPresenterWidget(EmbeddedQueryPresenter.class,
                EmbeddedQueryView.class,
                EmbeddedQueryViewImpl.class);
        bindPresenterWidget(BasicEmbeddedQuerySettingsPresenter.class,
                BasicEmbeddedQuerySettingsView.class,
                BasicEmbeddedQuerySettingsViewImpl.class);
    }
}

