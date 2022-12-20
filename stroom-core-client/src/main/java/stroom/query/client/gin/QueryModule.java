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
import stroom.query.client.presenter.QueryPresenter;
import stroom.query.client.presenter.QuerySettingsPresenter;
import stroom.query.client.presenter.QuerySettingsPresenter.QuerySettingsView;
import stroom.query.client.view.QuerySettingsViewImpl;

public class QueryModule extends PluginModule {

    @Override
    protected void configure() {
        bindPlugin(QueryPlugin.class);

        bind(QueryPresenter.class);

        bindSharedView(QuerySettingsView.class, QuerySettingsViewImpl.class);
        bind(QuerySettingsPresenter.class);

        bindPresenterWidget(ExpressionTreePresenter.class, ExpressionTreeView.class, ExpressionTreeViewImpl.class);
    }
}
