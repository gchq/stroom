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

package stroom.query.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.query.client.ExpressionTreePresenter;
import stroom.query.client.ExpressionTreePresenter.ExpressionTreeView;
import stroom.query.client.ExpressionTreeViewImpl;
import stroom.query.client.QueryPlugin;
import stroom.query.client.ResultStorePlugin;
import stroom.query.client.presenter.QueryDocPresenter;
import stroom.query.client.presenter.QueryDocPresenter.QueryDocView;
import stroom.query.client.presenter.QueryDocSettingsPresenter;
import stroom.query.client.presenter.QueryDocSettingsPresenter.QueryDocSettingsView;
import stroom.query.client.presenter.QueryDocSuperPresenter;
import stroom.query.client.presenter.QueryResultTablePresenter;
import stroom.query.client.presenter.QueryResultTablePresenter.QueryResultTableView;
import stroom.query.client.presenter.ResultStorePresenter;
import stroom.query.client.presenter.ResultStorePresenter.ResultStoreView;
import stroom.query.client.presenter.ResultStoreSettingsPresenter;
import stroom.query.client.presenter.ResultStoreSettingsPresenter.ResultStoreSettingsView;
import stroom.query.client.view.QueryDocSettingsViewImpl;
import stroom.query.client.view.QueryDocViewImpl;
import stroom.query.client.view.QueryResultTableViewImpl;
import stroom.query.client.view.ResultStoreSettingsViewImpl;
import stroom.query.client.view.ResultStoreViewImpl;

public class QueryModule extends PluginModule {

    @Override
    protected void configure() {
        bindPlugin(QueryPlugin.class);
        bindPlugin(ResultStorePlugin.class);

        bind(QueryDocSuperPresenter.class);

        bindPresenterWidget(
                QueryDocPresenter.class,
                QueryDocView.class,
                QueryDocViewImpl.class);
        bindPresenterWidget(
                QueryDocSettingsPresenter.class,
                QueryDocSettingsView.class,
                QueryDocSettingsViewImpl.class);
        bindPresenterWidget(
                QueryResultTablePresenter.class,
                QueryResultTableView.class,
                QueryResultTableViewImpl.class);

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
