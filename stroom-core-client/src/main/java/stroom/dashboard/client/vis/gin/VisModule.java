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

package stroom.dashboard.client.vis.gin;

import stroom.dashboard.client.vis.BasicVisSettingsPresenter;
import stroom.dashboard.client.vis.BasicVisSettingsPresenter.BasicVisSettingsView;
import stroom.dashboard.client.vis.BasicVisSettingsViewImpl;
import stroom.dashboard.client.vis.VisPlugin;
import stroom.dashboard.client.vis.VisPresenter;
import stroom.dashboard.client.vis.VisPresenter.VisView;
import stroom.dashboard.client.vis.VisViewImpl;
import stroom.visualisation.client.presenter.VisFunctionCache;

import com.google.inject.Singleton;
import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

public class VisModule extends AbstractPresenterModule {

    @Override
    protected void configure() {
        bind(VisPlugin.class).asEagerSingleton();
        bind(VisFunctionCache.class).in(Singleton.class);

        bindPresenterWidget(VisPresenter.class, VisView.class, VisViewImpl.class);
        bindPresenterWidget(BasicVisSettingsPresenter.class, BasicVisSettingsView.class,
                BasicVisSettingsViewImpl.class);
    }
}
