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

package stroom.sqlstore.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.sqlstore.client.SqlTemporalStorePlugin;
import stroom.sqlstore.client.presenter.SqlTemporalStoreDataPresenter;
import stroom.sqlstore.client.presenter.SqlTemporalStorePresenter;
import stroom.sqlstore.client.presenter.SqlTemporalStoreSettingsPresenter;
import stroom.sqlstore.client.presenter.SqlTemporalStoreSettingsPresenter.SqlTemporalStoreSettingsView;
import stroom.sqlstore.client.view.SqlTemporalStoreDataViewImpl;
import stroom.sqlstore.client.view.SqlTemporalStoreSettingsViewImpl;

public class SqlTemporalStoreModule extends PluginModule {

    @Override
    protected void configure() {
        bindPlugin(SqlTemporalStorePlugin.class);
        bind(SqlTemporalStorePresenter.class);
        bindPresenterWidget(
                SqlTemporalStoreSettingsPresenter.class,
                SqlTemporalStoreSettingsView.class,
                SqlTemporalStoreSettingsViewImpl.class);
        bindPresenterWidget(
                SqlTemporalStoreDataPresenter.class,
                SqlTemporalStoreDataPresenter.SqlTemporalStoreDataView.class,
                SqlTemporalStoreDataViewImpl.class);
    }
}
