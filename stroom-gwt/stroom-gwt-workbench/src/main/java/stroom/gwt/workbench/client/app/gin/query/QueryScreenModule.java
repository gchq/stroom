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

import stroom.query.client.presenter.QueryDocPresenter;
import stroom.query.client.presenter.QueryEditPresenter;
import stroom.query.client.presenter.QueryEditPresenter.QueryEditView;
import stroom.query.client.presenter.QueryResultTablePresenter;
import stroom.query.client.presenter.QueryResultTablePresenter.QueryResultTableView;
import stroom.query.client.presenter.QueryResultTableSplitPresenter;
import stroom.query.client.presenter.QueryResultTableSplitPresenter.QueryResultTableSplitView;
import stroom.query.client.presenter.QueryResultVisPresenter;
import stroom.query.client.presenter.QueryResultVisPresenter.QueryResultVisView;
import stroom.query.client.presenter.ResultStorePresenter;
import stroom.query.client.presenter.ResultStorePresenter.ResultStoreView;
import stroom.query.client.view.QueryEditViewImpl;
import stroom.query.client.view.QueryResultTableSplitViewImpl;
import stroom.query.client.view.QueryResultTableViewImpl;
import stroom.query.client.view.QueryResultTabsView;
import stroom.query.client.view.QueryResultTabsViewImpl;
import stroom.query.client.view.QueryResultVisViewImpl;
import stroom.query.client.view.ResultStoreViewImpl;
import stroom.widget.presenter.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `QueryModule`
/// (`stroom/query/client/gin/QueryModule.java`), without its plugins and app services.
/// A story shows a screen that Stroom opens on an app-wide event by adding a handler for
/// the event, as the screen's area plugin does in Stroom.
public class QueryScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bind(QueryDocPresenter.class);
        bindPresenterWidget(QueryEditPresenter.class,
                QueryEditView.class,
                QueryEditViewImpl.class);
        bindPresenterWidget(QueryResultTableSplitPresenter.class,
                QueryResultTableSplitView.class,
                QueryResultTableSplitViewImpl.class);
        bindSharedView(QueryResultTabsView.class,
                QueryResultTabsViewImpl.class);
        bindPresenterWidget(QueryResultTablePresenter.class,
                QueryResultTableView.class,
                QueryResultTableViewImpl.class);
        bindPresenterWidget(QueryResultVisPresenter.class,
                QueryResultVisView.class,
                QueryResultVisViewImpl.class);
        bindPresenterWidget(ResultStorePresenter.class,
                ResultStoreView.class,
                ResultStoreViewImpl.class);
    }
}

