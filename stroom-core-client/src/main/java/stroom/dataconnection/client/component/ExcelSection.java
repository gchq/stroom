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

package stroom.dataconnection.client.component;

import stroom.dataconnection.shared.DataColumn;
import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.DescriptionHTML;
import stroom.widget.form.client.TextBox;
import stroom.widget.form.client.component.FileChooser;
import stroom.widget.task.client.TaskMonitorFactory;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// The settings of an Excel Data Connection, in its Settings tab ([DataConnectionSettings]): the
/// workbook held (uploaded as soon as it is chosen, stored when the document is saved, and
/// downloaded or removed here), the sheet, whether its first row is a header, and its columns.
public class ExcelSection extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String NO_WORKBOOK = "No workbook has been uploaded.";

    @UiField
    DescriptionHTML workbookName;
    @UiField
    FileChooser fileChooser;
    @UiField
    Button download;
    @UiField
    Button remove;
    @UiField
    TextBox sheet;
    @UiField
    TickBox header;
    @UiField
    Button detectColumns;
    @UiField
    DataColumnList columns;

    private boolean hasWorkbook;
    private Runnable onFormatChange = () -> {
    };

    /// Makes the section, with no workbook, reading the first sheet with a header row.
    public ExcelSection() {
        initWidget(BINDER.createAndBindUi(this));
        header.setValue(true);
        sheet.addValueChangeHandler(e -> onFormatChange.run());
        header.addValueChangeHandler(e -> onFormatChange.run());
        setWorkbook(null, false);
    }

    /// @param onFormatChange Run when the user changes the sheet or the header row, which changes
    ///                       the columns.
    public void setOnFormatChange(final Runnable onFormatChange) {
        this.onFormatChange = onFormatChange;
    }

    /// @param onColumnsChange Run when the user changes a column's type.
    public void setOnColumnsChange(final Runnable onColumnsChange) {
        columns.setOnChange(onColumnsChange);
    }

    /// @param onDetectColumns Run when Detect Columns is pressed.
    public void setOnDetectColumns(final Runnable onDetectColumns) {
        detectColumns.setOnClick(onDetectColumns);
    }

    /// @return The Detect Columns button, which spins while the columns are detected.
    public TaskMonitorFactory getDetectColumnsTaskMonitor() {
        return detectColumns;
    }

    /// @param onDownload Run when Download is pressed.
    public void setOnDownload(final Runnable onDownload) {
        download.setOnClick(onDownload);
    }

    /// @return The Download button, which spins while the workbook downloads.
    public TaskMonitorFactory getDownloadTaskMonitor() {
        return download;
    }

    /// @param onRemove Run when Remove is pressed.
    public void setOnRemove(final Runnable onRemove) {
        remove.setOnClick(onRemove);
    }

    /// @return The chooser of the workbook to upload, which its controller attaches to.
    public FileChooser getFileChooser() {
        return fileChooser;
    }

    /// Shows the workbook held, or about to be.
    ///
    /// @param name        What to call it, e.g. its file name, or `null` for none.
    /// @param canDownload True if it is stored, so can be downloaded.
    public void setWorkbook(final String name, final boolean canDownload) {
        workbookName.setText(name == null
                ? NO_WORKBOOK
                : name);
        hasWorkbook = name != null;
        download.setEnabled(canDownload);
        remove.setEnabled(hasWorkbook && !isReadOnly());
    }

    /// @return The sheet as entered.
    public String getSheet() {
        return sheet.getValue();
    }

    /// @param sheet The sheet, or `null` for the first.
    public void setSheet(final String sheet) {
        this.sheet.setValue(sheet);
    }

    /// @return True if the first row with values is a header.
    public boolean isHeader() {
        return Boolean.TRUE.equals(header.getValue());
    }

    /// @param header True if the first row with values is a header.
    public void setHeader(final boolean header) {
        this.header.setValue(header);
    }

    /// @return The columns, with the types chosen.
    public List<DataColumn> getColumns() {
        return columns.getColumns();
    }

    /// @param columns The columns.
    public void setColumns(final List<DataColumn> columns) {
        this.columns.setColumns(columns);
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        fileChooser.setReadOnly(readOnly);
        remove.setEnabled(hasWorkbook && !readOnly);
        sheet.setReadOnly(readOnly);
        header.setReadOnly(readOnly);
        // Detecting needs permission to edit the document
        detectColumns.setEnabled(!readOnly);
        columns.setReadOnly(readOnly);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ExcelSection> {

    }
}
