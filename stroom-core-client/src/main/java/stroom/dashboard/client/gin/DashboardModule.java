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

package stroom.dashboard.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.dashboard.client.DashboardPlugin;
import stroom.dashboard.client.main.ComponentRegistry;
import stroom.dashboard.client.main.DashboardPresenter;
import stroom.dashboard.client.main.DashboardViewImpl;
import stroom.dashboard.client.main.LayoutConstraintPresenter;
import stroom.dashboard.client.main.LayoutConstraintPresenter.LayoutConstraintView;
import stroom.dashboard.client.main.LayoutConstraintViewImpl;
import stroom.dashboard.client.main.RenameTabPresenter;
import stroom.dashboard.client.main.RenameTabPresenter.RenameTabView;
import stroom.dashboard.client.main.RenameTabViewImpl;
import stroom.dashboard.client.unknown.HTMLView;
import stroom.dashboard.client.unknown.HTMLViewImpl;
import stroom.hyperlink.client.HyperlinkEventHandlerImpl;
import stroom.widget.tab.client.presenter.LinkTabsLayoutView;
import stroom.widget.tab.client.view.LinkTabsLayoutViewImpl;

import com.google.inject.Singleton;

public class DashboardModule extends PluginModule {

    @Override
    protected void configure() {
        bind(HyperlinkEventHandlerImpl.class).asEagerSingleton();

        bind(ComponentRegistry.class).in(Singleton.class);

        bindPlugin(DashboardPlugin.class);

        bindPresenterWidget(
                DashboardPresenter.class,
                DashboardPresenter.DashboardView.class,
                DashboardViewImpl.class);
        bindPresenterWidget(
                RenameTabPresenter.class,
                RenameTabView.class,
                RenameTabViewImpl.class);
        bindPresenterWidget(
                LayoutConstraintPresenter.class,
                LayoutConstraintView.class,
                LayoutConstraintViewImpl.class);

        bindSharedView(LinkTabsLayoutView.class, LinkTabsLayoutViewImpl.class);
        bindSharedView(HTMLView.class, HTMLViewImpl.class);
    }
}
