/*
 * Copyright 2016 Crown Copyright
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

package stroom.dashboard.client.gin;

import com.google.inject.Singleton;
import stroom.app.client.gin.PluginModule;
import stroom.dashboard.client.DashboardPlugin;
import stroom.dashboard.client.main.ComponentRegistry;
import stroom.dashboard.client.main.DashboardLayoutPresenter;
import stroom.dashboard.client.main.DashboardLayoutViewImpl;
import stroom.dashboard.client.main.DashboardPresenter;
import stroom.dashboard.client.main.DashboardViewImpl;
import stroom.dashboard.client.unknown.HTMLView;
import stroom.dashboard.client.unknown.HTMLViewImpl;
import stroom.widget.tab.client.presenter.SlideTabLayoutView;
import stroom.widget.tab.client.view.SlideTabLayoutViewImpl;

public class DashboardModule extends PluginModule {
    @Override
    protected void configure() {
        bind(ComponentRegistry.class).in(Singleton.class);

        bindPlugin(DashboardPlugin.class);
        bindPresenterWidget(DashboardPresenter.class, DashboardPresenter.DashboardView.class, DashboardViewImpl.class);
        bindPresenterWidget(DashboardLayoutPresenter.class, DashboardLayoutPresenter.DashboardLayoutView.class, DashboardLayoutViewImpl.class);

        bindSharedView(SlideTabLayoutView.class, SlideTabLayoutViewImpl.class);
        bindSharedView(HTMLView.class, HTMLViewImpl.class);
    }
}
