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

package stroom.dataconnection.client.gin;

import stroom.dataconnection.client.DataConnectionPlugin;
import stroom.dataconnection.client.presenter.DataConnectionPresenter;
import stroom.dataconnection.client.presenter.DataConnectionSettingsPresenter;

import com.google.gwt.inject.client.AsyncProvider;

/// Gives the app the Data Connection editor, loaded when first needed.
public interface DataConnectionGinjector {

    /// @return The plugin that opens Data Connections.
    AsyncProvider<DataConnectionPlugin> getDataConnectionPlugin();

    /// @return The document editor.
    AsyncProvider<DataConnectionPresenter> getDataConnectionPresenter();

    /// @return The settings tab.
    AsyncProvider<DataConnectionSettingsPresenter> getDataConnectionSettingsPresenter();
}
