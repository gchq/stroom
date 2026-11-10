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

package stroom.sqldatasource.client.presenter;

import stroom.task.client.TaskMonitorFactory;

import com.gwtplatform.mvp.client.UiHandlers;

/// What the settings view of a SQL Data Source tells its presenter.
public interface SqlDataSourceSettingsUiHandlers extends UiHandlers {

    /// Called when any setting changes.
    void onChange();

    /// Called when the user asks to test the connection with the settings as they stand.
    ///
    /// @param taskMonitorFactory Shows that the test is running.
    void onTestConnection(TaskMonitorFactory taskMonitorFactory);
}
