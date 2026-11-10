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

package stroom.sqldatasource.client.gin;

import stroom.sqldatasource.client.SqlDataSourcePlugin;
import stroom.sqldatasource.client.presenter.SqlDataSourcePresenter;
import stroom.sqldatasource.client.presenter.SqlDataSourceSettingsPresenter;

import com.google.gwt.inject.client.AsyncProvider;

/// Gives the app the SQL Data Source editor, loaded when first needed.
public interface SqlDataSourceGinjector {

    /// @return The plugin that opens SQL Data Sources.
    AsyncProvider<SqlDataSourcePlugin> getSqlDataSourcePlugin();

    /// @return The document editor.
    AsyncProvider<SqlDataSourcePresenter> getSqlDataSourcePresenter();

    /// @return The settings tab.
    AsyncProvider<SqlDataSourceSettingsPresenter> getSqlDataSourceSettingsPresenter();
}
