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

package stroom.dataconnection.client.presenter;

import stroom.task.client.TaskMonitorFactory;

import com.gwtplatform.mvp.client.UiHandlers;

/// What the settings view of a Data Connection tells its presenter.
public interface DataConnectionSettingsUiHandlers extends UiHandlers {

    /// Called when any setting changes.
    void onChange();

    /// Called when the user asks to test the connection with the settings as they stand.
    ///
    /// @param taskMonitorFactory Shows that the test is running.
    void onTestConnection(TaskMonitorFactory taskMonitorFactory);

    /// Called when the user asks to detect the columns of the CSV as it stands.
    ///
    /// @param taskMonitorFactory Shows that the detection is running.
    void onDetectColumns(TaskMonitorFactory taskMonitorFactory);

    /// Called when a setting for reading the data changes: the CSV delimiter, the sheet or whether the
    /// first row is a header. The columns may need detecting again.
    ///
    /// @param taskMonitorFactory Shows that a detection is running.
    void onDataFormatChange(TaskMonitorFactory taskMonitorFactory);

    /// Called when the user asks to remove the workbook, which happens when the document is saved.
    void onRemoveExcel();

    /// Called when the user asks to download the stored workbook.
    ///
    /// @param taskMonitorFactory Shows that the download is being prepared.
    void onDownloadExcel(TaskMonitorFactory taskMonitorFactory);

    /// Called when a CSV file the user chose has been read in the browser.
    ///
    /// @param text The file's text.
    void onCsvFileRead(String text);

    /// Called when a CSV file the user chose could not be read.
    ///
    /// @param message What went wrong.
    void onCsvFileError(String message);
}
