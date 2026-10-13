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
import stroom.visualisation.client.presenter.VisFunctionCache;

import com.google.inject.Singleton;
import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `DashboardModule`
/// (`stroom/dashboard/client/gin/DashboardModule.java`), without its plugins and app services.
/// Presenters that Stroom binds with a GWTP proxy are bound as presenter widgets with a null
/// proxy: a story shows one by registering it as the handler of its event, as the proxy would.
///
/// Unlike the other mirrors, it keeps the dashboard's singletons: the `ComponentRegistry` (which the
/// component plugins fill, and every dashboard's `Components` reads) and the `VisFunctionCache`
/// (bound by Stroom's `VisModule` and `EmbeddedQueryModule`), and leaves out the `LinkTabsLayoutView`
/// that `ScreenViewsModule` already binds.
public class DashboardScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bind(ComponentRegistry.class).in(Singleton.class);
        bind(VisFunctionCache.class).in(Singleton.class);

        bindPresenterWidget(DashboardPresenter.class,
                DashboardPresenter.DashboardView.class,
                DashboardViewImpl.class);
        bindPresenterWidget(RenameTabPresenter.class,
                RenameTabView.class,
                RenameTabViewImpl.class);
        bindPresenterWidget(LayoutConstraintPresenter.class,
                LayoutConstraintView.class,
                LayoutConstraintViewImpl.class);
        bindSharedView(HTMLView.class,
                HTMLViewImpl.class);
    }
}

