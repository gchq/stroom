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

import stroom.dataconnection.shared.DataConnectionType;
import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.task.client.TaskMonitorFactory;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// A Data Connection's Settings tab: its type, the settings of that type (one section each: JDBC,
/// Amazon Athena, LDAP, CSV and Excel, only the chosen one shown), its time column, query timeout and
/// row limit, and a button to test it.
public class DataConnectionSettings extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SelectionBox<DataConnectionType> type;
    @UiField
    JdbcSection jdbc;
    @UiField
    AthenaSection athena;
    @UiField
    LdapSection ldap;
    @UiField
    CsvSection csv;
    @UiField
    ExcelSection excel;
    @UiField
    FlowPanel timeFieldGroup;
    @UiField
    TextBox timeField;
    @UiField
    FlowPanel queryTimeoutGroup;
    @UiField
    ValueSpinner queryTimeoutSeconds;
    @UiField
    ValueSpinner maxRows;
    @UiField
    Button testConnection;

    private Runnable onChange = () -> {
    };

    /// Makes the tab, showing the JDBC settings.
    public DataConnectionSettings() {
        initWidget(BINDER.createAndBindUi(this));
        type.addItems(DataConnectionType.values());
        type.addValueChangeHandler(e -> {
            showSettingsFor(e.getValue());
            onChange.run();
        });
        timeField.addValueChangeHandler(e -> onChange.run());
        queryTimeoutSeconds.addValueChangeHandler(e -> onChange.run());
        maxRows.addValueChangeHandler(e -> onChange.run());
        setType(DataConnectionType.JDBC);
    }

    /// @param onChange Run when the user changes the type, time column, timeout or row limit.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    /// @param onTestConnection Run when Test Connection (Check CSV, Check Workbook) is pressed.
    public void setOnTestConnection(final Runnable onTestConnection) {
        testConnection.setOnClick(onTestConnection);
    }

    /// @return The Test Connection button, which spins while the test runs.
    public TaskMonitorFactory getTestConnectionTaskMonitor() {
        return testConnection;
    }

    /// @return The JDBC settings.
    public JdbcSection getJdbc() {
        return jdbc;
    }

    /// @return The Amazon Athena settings.
    public AthenaSection getAthena() {
        return athena;
    }

    /// @return The LDAP settings.
    public LdapSection getLdap() {
        return ldap;
    }

    /// @return The CSV settings.
    public CsvSection getCsv() {
        return csv;
    }

    /// @return The Excel settings.
    public ExcelSection getExcel() {
        return excel;
    }

    /// @return The type chosen.
    public DataConnectionType getType() {
        return type.getValue();
    }

    /// Shows a type, with the settings that go with it.
    ///
    /// @param type The type.
    public void setType(final DataConnectionType type) {
        this.type.setValue(type);
        showSettingsFor(type);
    }

    /// @return The time column as entered.
    public String getTimeField() {
        return timeField.getValue();
    }

    /// @param timeField The time column, or `null` for none.
    public void setTimeField(final String timeField) {
        this.timeField.setValue(timeField);
    }

    /// @return The query timeout, in seconds.
    public int getQueryTimeoutSeconds() {
        return queryTimeoutSeconds.getIntValue();
    }

    /// @param seconds The query timeout, in seconds.
    public void setQueryTimeoutSeconds(final int seconds) {
        queryTimeoutSeconds.setValue(seconds);
    }

    /// @return The most rows a query may read.
    public int getMaxRows() {
        return maxRows.getIntValue();
    }

    /// @param maxRows The most rows a query may read.
    public void setMaxRows(final int maxRows) {
        this.maxRows.setValue(maxRows);
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        type.setReadOnly(readOnly);
        jdbc.setReadOnly(readOnly);
        athena.setReadOnly(readOnly);
        ldap.setReadOnly(readOnly);
        csv.setReadOnly(readOnly);
        excel.setReadOnly(readOnly);
        timeField.setReadOnly(readOnly);
        queryTimeoutSeconds.setReadOnly(readOnly);
        maxRows.setReadOnly(readOnly);
        // Testing needs permission to edit the document
        testConnection.setEnabled(!readOnly);
    }

    private void showSettingsFor(final DataConnectionType type) {
        final DataConnectionType shown = DataConnectionType.orDefault(type);
        jdbc.setVisible(shown == DataConnectionType.JDBC);
        athena.setVisible(shown == DataConnectionType.ATHENA);
        ldap.setVisible(shown == DataConnectionType.LDAP);
        csv.setVisible(shown == DataConnectionType.CSV);
        excel.setVisible(shown == DataConnectionType.EXCEL);
        timeFieldGroup.setVisible(shown.usesTimeField());
        queryTimeoutGroup.setVisible(!shown.holdsData());
        testConnection.setText(switch (shown) {
            case CSV -> "Check CSV";
            case EXCEL -> "Check Workbook";
            case JDBC, ATHENA, LDAP -> "Test Connection";
        });
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DataConnectionSettings> {

    }
}
