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

package stroom.gwt.workbench.client.app.gin.query;

import stroom.dashboard.client.query.BasicQuerySettingsPresenter;
import stroom.dashboard.client.query.BasicQuerySettingsPresenter.BasicQuerySettingsView;
import stroom.dashboard.client.query.BasicQuerySettingsViewImpl;
import stroom.dashboard.client.query.CurrentSelectionPresenter;
import stroom.dashboard.client.query.CurrentSelectionViewImpl;
import stroom.dashboard.client.query.NamePresenter;
import stroom.dashboard.client.query.NamePresenter.NameView;
import stroom.dashboard.client.query.NameViewImpl;
import stroom.dashboard.client.query.ProcessorLimitsPresenter;
import stroom.dashboard.client.query.ProcessorLimitsPresenter.ProcessorLimitsView;
import stroom.dashboard.client.query.ProcessorLimitsViewImpl;
import stroom.dashboard.client.query.QueryFavouritesPresenter;
import stroom.dashboard.client.query.QueryFavouritesPresenter.QueryFavouritesView;
import stroom.dashboard.client.query.QueryFavouritesViewImpl;
import stroom.dashboard.client.query.QueryHistoryPresenter;
import stroom.dashboard.client.query.QueryHistoryPresenter.QueryHistoryView;
import stroom.dashboard.client.query.QueryHistoryViewImpl;
import stroom.dashboard.client.query.QueryInfoPresenter;
import stroom.dashboard.client.query.QueryInfoPresenter.QueryInfoView;
import stroom.dashboard.client.query.QueryInfoViewImpl;
import stroom.dashboard.client.query.QueryPresenter;
import stroom.dashboard.client.query.QueryPresenter.QueryView;
import stroom.dashboard.client.query.QueryViewImpl;
import stroom.dashboard.client.query.SelectionHandlerPresenter;
import stroom.dashboard.client.query.SelectionHandlerViewImpl;
import stroom.dashboard.client.query.SelectionHandlersPresenter;
import stroom.dashboard.client.query.SelectionHandlersViewImpl;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `QueryModule`
/// (`stroom/dashboard/client/query/gin/QueryModule.java`), without its plugins and app services.
/// Presenters that Stroom binds with a GWTP proxy are bound as presenter widgets with a null
/// proxy: a story shows one by registering it as the handler of its event, as the proxy would.
public class DashboardQueryScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bindPresenterWidget(QueryHistoryPresenter.class,
                QueryHistoryView.class,
                QueryHistoryViewImpl.class);
        bindPresenterWidget(QueryFavouritesPresenter.class,
                QueryFavouritesView.class,
                QueryFavouritesViewImpl.class);
        bindPresenterWidget(NamePresenter.class,
                NameView.class,
                NameViewImpl.class);
        bindPresenterWidget(QueryInfoPresenter.class,
                QueryInfoView.class,
                QueryInfoViewImpl.class);
        bindPresenterWidget(QueryPresenter.class,
                QueryView.class,
                QueryViewImpl.class);
        bindPresenterWidget(ProcessorLimitsPresenter.class,
                ProcessorLimitsView.class,
                ProcessorLimitsViewImpl.class);
        bindPresenterWidget(BasicQuerySettingsPresenter.class,
                BasicQuerySettingsView.class,
                BasicQuerySettingsViewImpl.class);
        bindPresenterWidget(SelectionHandlersPresenter.class,
                SelectionHandlersPresenter.SelectionHandlersView.class,
                SelectionHandlersViewImpl.class);
        bindPresenterWidget(SelectionHandlerPresenter.class,
                SelectionHandlerPresenter.SelectionHandlerView.class,
                SelectionHandlerViewImpl.class);
        bindPresenterWidget(CurrentSelectionPresenter.class,
                CurrentSelectionPresenter.CurrentSelectionView.class,
                CurrentSelectionViewImpl.class);
    }
}

