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

package stroom.dashboard.client.embeddedquery.gin;

import stroom.dashboard.client.embeddedquery.BasicEmbeddedQuerySettingsPresenter;
import stroom.dashboard.client.embeddedquery.BasicEmbeddedQuerySettingsPresenter.BasicEmbeddedQuerySettingsView;
import stroom.dashboard.client.embeddedquery.BasicEmbeddedQuerySettingsViewImpl;
import stroom.dashboard.client.embeddedquery.EmbeddedQueryPlugin;
import stroom.dashboard.client.embeddedquery.EmbeddedQueryPresenter;
import stroom.dashboard.client.embeddedquery.EmbeddedQueryPresenter.EmbeddedQueryView;
import stroom.dashboard.client.embeddedquery.EmbeddedQueryViewImpl;
import stroom.visualisation.client.presenter.VisFunctionCache;

import com.google.inject.Singleton;
import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

public class EmbeddedQueryModule extends AbstractPresenterModule {

    @Override
    protected void configure() {
        bind(EmbeddedQueryPlugin.class).asEagerSingleton();
        bind(VisFunctionCache.class).in(Singleton.class);

        bindPresenterWidget(EmbeddedQueryPresenter.class, EmbeddedQueryView.class, EmbeddedQueryViewImpl.class);
        bindPresenterWidget(BasicEmbeddedQuerySettingsPresenter.class,
                BasicEmbeddedQuerySettingsView.class,
                BasicEmbeddedQuerySettingsViewImpl.class);
    }
}
