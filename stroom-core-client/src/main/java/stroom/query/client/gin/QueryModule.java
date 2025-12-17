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

package stroom.query.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.query.client.ExpressionTreePresenter;
import stroom.query.client.ExpressionTreePresenter.ExpressionTreeView;
import stroom.query.client.ExpressionTreeViewImpl;
import stroom.query.client.QueryPlugin;
import stroom.query.client.ResultStorePlugin;
import stroom.query.client.presenter.QueryDocPresenter;
import stroom.query.client.presenter.QueryEditPresenter;
import stroom.query.client.presenter.QueryEditPresenter.QueryEditView;
import stroom.query.client.presenter.QueryHelpPresenter;
import stroom.query.client.presenter.QueryHelpPresenter.QueryHelpView;
import stroom.query.client.presenter.QueryResultTablePresenter;
import stroom.query.client.presenter.QueryResultTablePresenter.QueryResultTableView;
import stroom.query.client.presenter.QueryResultTableSplitPresenter;
import stroom.query.client.presenter.QueryResultTableSplitPresenter.QueryResultTableSplitView;
import stroom.query.client.presenter.QueryResultVisPresenter;
import stroom.query.client.presenter.QueryResultVisPresenter.QueryResultVisView;
import stroom.query.client.presenter.QueryToolbarPresenter;
import stroom.query.client.presenter.QueryToolbarPresenter.QueryToolbarView;
import stroom.query.client.presenter.ResultStorePresenter;
import stroom.query.client.presenter.ResultStorePresenter.ResultStoreView;
import stroom.query.client.presenter.ResultStoreSettingsPresenter;
import stroom.query.client.presenter.ResultStoreSettingsPresenter.ResultStoreSettingsView;
import stroom.query.client.presenter.TextPresenter;
import stroom.query.client.presenter.TextPresenter.TextView;
import stroom.query.client.view.QueryEditViewImpl;
import stroom.query.client.view.QueryHelpViewImpl;
import stroom.query.client.view.QueryResultTableSplitViewImpl;
import stroom.query.client.view.QueryResultTableViewImpl;
import stroom.query.client.view.QueryResultTabsView;
import stroom.query.client.view.QueryResultTabsViewImpl;
import stroom.query.client.view.QueryResultVisViewImpl;
import stroom.query.client.view.QueryToolbarViewImpl;
import stroom.query.client.view.ResultStoreSettingsViewImpl;
import stroom.query.client.view.ResultStoreViewImpl;
import stroom.query.client.view.TextViewImpl;

public class QueryModule extends PluginModule {

    @Override
    protected void configure() {
        bindPlugin(QueryPlugin.class);
        bindPlugin(ResultStorePlugin.class);

        bind(QueryDocPresenter.class);

        bindPresenterWidget(
                QueryToolbarPresenter.class,
                QueryToolbarView.class,
                QueryToolbarViewImpl.class);
        bindPresenterWidget(
                QueryHelpPresenter.class,
                QueryHelpView.class,
                QueryHelpViewImpl.class);
        bindPresenterWidget(
                QueryEditPresenter.class,
                QueryEditView.class,
                QueryEditViewImpl.class);
        bindPresenterWidget(
                QueryResultTableSplitPresenter.class,
                QueryResultTableSplitView.class,
                QueryResultTableSplitViewImpl.class);
        bindSharedView(
                QueryResultTabsView.class,
                QueryResultTabsViewImpl.class);
        bindPresenterWidget(
                QueryResultTablePresenter.class,
                QueryResultTableView.class,
                QueryResultTableViewImpl.class);
        bindPresenterWidget(
                QueryResultVisPresenter.class,
                QueryResultVisView.class,
                QueryResultVisViewImpl.class);
        bindPresenterWidget(
                TextPresenter.class,
                TextView.class,
                TextViewImpl.class);

        bindPresenterWidget(
                ExpressionTreePresenter.class,
                ExpressionTreeView.class,
                ExpressionTreeViewImpl.class);

        bindPresenterWidget(
                ResultStorePresenter.class,
                ResultStoreView.class,
                ResultStoreViewImpl.class);

        bindPresenterWidget(
                ResultStoreSettingsPresenter.class,
                ResultStoreSettingsView.class,
                ResultStoreSettingsViewImpl.class);
    }
}
