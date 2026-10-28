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

package stroom.gwt.workbench.client.app.gin;

import stroom.widget.presenter.client.gin.AbstractPresenterModule;
import stroom.widget.tab.client.presenter.LinkTabsLayoutView;
import stroom.widget.tab.client.view.LinkTabsLayoutViewImpl;

/// The views and small presenters that Stroom binds for every screen, copied from Stroom's
/// `AppModule` and the shared widgets that other Stroom modules (e.g. `StreamStoreModule`) bind,
/// but without plugins or the app shell, so that e.g. a list presenter's `PagerPanel` is created
/// as in Stroom.
///
/// GIN checks the whole graph of every binding of an injector's modules, used or not, so a widget
/// bound here must be left out of the area modules (a key bound twice fails the GIN compile).
/// Add a binding here (copied from Stroom's module) when a view or presenter is a widget shared
/// by several areas' screens.
public class ScreenViewsModule extends AbstractPresenterModule {

    /// Binds the shared views and presenters.
    @Override
    protected void configure() {
        // AppModule: widgets
        // AppModule: menus and tool tips

        // SecurityModule

        // MonitoringModule

        // StreamStoreModule: editors, expressions and the explorer drop downs

        // DashboardModule
        bindSharedView(LinkTabsLayoutView.class, LinkTabsLayoutViewImpl.class);
    }
}
