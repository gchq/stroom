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

import stroom.dataconnection.client.model.CsvDelimiter;
import stroom.dataconnection.shared.DataColumn;
import stroom.editor.client.component.CodeEditor;
import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.component.FileChooser;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.task.client.TaskMonitorFactory;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// The settings of a CSV Data Connection, in its Settings tab ([DataConnectionSettings]): the
/// delimiter, whether the first row is a header, the data itself (in an editor, which a file can be
/// loaded into) and its columns.
public class CsvSection extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SelectionBox<CsvDelimiter> delimiter;
    @UiField
    TickBox header;
    @UiField
    CodeEditor editor;
    @UiField
    FileChooser fileChooser;
    @UiField
    Button detectColumns;
    @UiField
    DataColumnList columns;

    // A stored delimiter that isn't one of those offered, kept until the user picks another, so
    // saving doesn't change it
    private String unlistedDelimiter;
    private Runnable onFormatChange = () -> {
    };

    /// Makes the section, comma separated, with a header row and no data.
    public CsvSection() {
        initWidget(BINDER.createAndBindUi(this));
        delimiter.addItems(CsvDelimiter.values());
        delimiter.setValue(CsvDelimiter.COMMA);
        header.setValue(true);
        delimiter.addValueChangeHandler(e -> {
            unlistedDelimiter = null;
            onFormatChange.run();
        });
        header.addValueChangeHandler(e -> onFormatChange.run());
    }

    /// @param onFormatChange Run when the user changes the delimiter or the header row, which
    ///                       changes the columns.
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

    /// @return The editor of the data, which its controller attaches to.
    public CodeEditor getEditor() {
        return editor;
    }

    /// @return The chooser of a file to load the data from, which its controller attaches to.
    public FileChooser getFileChooser() {
        return fileChooser;
    }

    /// @return The delimiter chosen, as stored.
    public String getDelimiter() {
        return unlistedDelimiter != null
                ? unlistedDelimiter
                : delimiter.getValue().getValue();
    }

    /// @param delimiter The stored delimiter, or `null` for the default. One not offered is kept
    ///                  as it is, and shown as a comma.
    public void setDelimiter(final String delimiter) {
        final CsvDelimiter listed = CsvDelimiter.fromValue(delimiter);
        unlistedDelimiter = delimiter != null && !listed.getValue().equals(delimiter)
                ? delimiter
                : null;
        this.delimiter.setValue(listed);
    }

    /// @return True if the first row is a header.
    public boolean isHeader() {
        return Boolean.TRUE.equals(header.getValue());
    }

    /// @param header True if the first row is a header.
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
        delimiter.setReadOnly(readOnly);
        header.setReadOnly(readOnly);
        fileChooser.setReadOnly(readOnly);
        // Detecting needs permission to edit the document
        detectColumns.setEnabled(!readOnly);
        columns.setReadOnly(readOnly);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, CsvSection> {

    }
}
