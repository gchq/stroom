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

package stroom.sqldatasource.client.view;

import stroom.credentials.shared.Credential;
import stroom.item.client.SelectionBox;
import stroom.sqldatasource.client.presenter.SqlDataSourceSettingsPresenter.SqlDataSourceSettingsView;
import stroom.sqldatasource.client.presenter.SqlDataSourceSettingsUiHandlers;
import stroom.sqldatasource.shared.SqlConnectionType;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Button;
import stroom.widget.form.client.DescriptionHTML;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

/// The settings of a SQL Data Source. Only the settings of the chosen connection type are shown.
public class SqlDataSourceSettingsViewImpl
        extends ViewWithUiHandlers<SqlDataSourceSettingsUiHandlers>
        implements SqlDataSourceSettingsView {

    private static final int MAX_POOL_SIZE = 100;
    private static final int MAX_FETCH_SIZE = 100_000;
    private static final int MAX_TIMEOUT_SECONDS = 86_400;
    private static final int MAX_ROWS = 100_000_000;
    // Athena reuses a result for at most seven days.
    private static final int MAX_RESULT_REUSE_MINUTES = 10_080;

    private final Widget widget;

    @UiField
    SelectionBox<SqlConnectionType> connectionType;
    @UiField
    FlowPanel jdbcSettings;
    @UiField
    TextBox jdbcUrl;
    @UiField
    SelectionBox<Credential> jdbcCredential;
    @UiField
    DescriptionHTML jdbcCredentialNote;
    @UiField
    TextArea connectionProperties;
    @UiField
    ValueSpinner maxPoolSize;
    @UiField
    ValueSpinner fetchSize;
    @UiField
    FlowPanel athenaSettings;
    @UiField
    TextBox region;
    @UiField
    TextBox workGroup;
    @UiField
    TextBox catalog;
    @UiField
    TextBox database;
    @UiField
    TextBox outputLocation;
    @UiField
    SelectionBox<Credential> athenaCredential;
    @UiField
    DescriptionHTML athenaCredentialNote;
    @UiField
    ValueSpinner resultReuseMinutes;
    @UiField
    TextBox timeField;
    @UiField
    ValueSpinner queryTimeoutSeconds;
    @UiField
    ValueSpinner maxRows;
    @UiField
    Button testConnection;

    /// @param binder Binds the UiBinder template.
    @Inject
    public SqlDataSourceSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        connectionType.addItems(SqlConnectionType.values());
        setJdbcCredentialNote(null);
        setAthenaCredentialNote(null);
        setLimits(maxPoolSize, 1, MAX_POOL_SIZE);
        setLimits(fetchSize, 0, MAX_FETCH_SIZE);
        setLimits(resultReuseMinutes, 0, MAX_RESULT_REUSE_MINUTES);
        setLimits(queryTimeoutSeconds, 1, MAX_TIMEOUT_SECONDS);
        setLimits(maxRows, 1, MAX_ROWS);
        testConnection.setIcon(SvgImage.OK);
        setConnectionType(SqlConnectionType.JDBC);
    }

    private static void setLimits(final ValueSpinner spinner, final int min, final int max) {
        spinner.setMin(min);
        spinner.setMax(max);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public SqlConnectionType getConnectionType() {
        return connectionType.getValue();
    }

    @Override
    public void setConnectionType(final SqlConnectionType connectionType) {
        this.connectionType.setValue(connectionType);
        showSettingsFor(connectionType);
    }

    private void showSettingsFor(final SqlConnectionType connectionType) {
        final boolean athena = SqlConnectionType.ATHENA.equals(connectionType);
        jdbcSettings.setVisible(!athena);
        athenaSettings.setVisible(athena);
    }

    @Override
    public String getJdbcUrl() {
        return jdbcUrl.getValue();
    }

    @Override
    public void setJdbcUrl(final String jdbcUrl) {
        this.jdbcUrl.setValue(jdbcUrl);
    }

    @Override
    public SelectionBox<Credential> getJdbcCredentialSelectionBox() {
        return jdbcCredential;
    }

    @Override
    public void setJdbcCredentialNote(final String note) {
        showNote(jdbcCredentialNote, note);
    }

    private static void showNote(final DescriptionHTML noteWidget, final String note) {
        noteWidget.setText(note);
        noteWidget.setVisible(note != null);
    }

    @Override
    public String getConnectionProperties() {
        return connectionProperties.getValue();
    }

    @Override
    public void setConnectionProperties(final String connectionProperties) {
        this.connectionProperties.setValue(connectionProperties);
    }

    @Override
    public int getMaxPoolSize() {
        return maxPoolSize.getIntValue();
    }

    @Override
    public void setMaxPoolSize(final int maxPoolSize) {
        this.maxPoolSize.setValue(maxPoolSize);
    }

    @Override
    public int getFetchSize() {
        return fetchSize.getIntValue();
    }

    @Override
    public void setFetchSize(final int fetchSize) {
        this.fetchSize.setValue(fetchSize);
    }

    @Override
    public String getRegion() {
        return region.getValue();
    }

    @Override
    public void setRegion(final String region) {
        this.region.setValue(region);
    }

    @Override
    public String getWorkGroup() {
        return workGroup.getValue();
    }

    @Override
    public void setWorkGroup(final String workGroup) {
        this.workGroup.setValue(workGroup);
    }

    @Override
    public String getCatalog() {
        return catalog.getValue();
    }

    @Override
    public void setCatalog(final String catalog) {
        this.catalog.setValue(catalog);
    }

    @Override
    public String getDatabase() {
        return database.getValue();
    }

    @Override
    public void setDatabase(final String database) {
        this.database.setValue(database);
    }

    @Override
    public String getOutputLocation() {
        return outputLocation.getValue();
    }

    @Override
    public void setOutputLocation(final String outputLocation) {
        this.outputLocation.setValue(outputLocation);
    }

    @Override
    public SelectionBox<Credential> getAthenaCredentialSelectionBox() {
        return athenaCredential;
    }

    @Override
    public void setAthenaCredentialNote(final String note) {
        showNote(athenaCredentialNote, note);
    }

    @Override
    public int getResultReuseMinutes() {
        return resultReuseMinutes.getIntValue();
    }

    @Override
    public void setResultReuseMinutes(final int resultReuseMinutes) {
        this.resultReuseMinutes.setValue(resultReuseMinutes);
    }

    @Override
    public String getTimeField() {
        return timeField.getValue();
    }

    @Override
    public void setTimeField(final String timeField) {
        this.timeField.setValue(timeField);
    }

    @Override
    public int getQueryTimeoutSeconds() {
        return queryTimeoutSeconds.getIntValue();
    }

    @Override
    public void setQueryTimeoutSeconds(final int queryTimeoutSeconds) {
        this.queryTimeoutSeconds.setValue(queryTimeoutSeconds);
    }

    @Override
    public int getMaxRows() {
        return maxRows.getIntValue();
    }

    @Override
    public void setMaxRows(final int maxRows) {
        this.maxRows.setValue(maxRows);
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        final boolean enabled = !readOnly;
        connectionType.setEnabled(enabled);
        jdbcUrl.setEnabled(enabled);
        jdbcCredential.setEnabled(enabled);
        connectionProperties.setEnabled(enabled);
        maxPoolSize.setEnabled(enabled);
        fetchSize.setEnabled(enabled);
        region.setEnabled(enabled);
        workGroup.setEnabled(enabled);
        catalog.setEnabled(enabled);
        database.setEnabled(enabled);
        outputLocation.setEnabled(enabled);
        athenaCredential.setEnabled(enabled);
        resultReuseMinutes.setEnabled(enabled);
        timeField.setEnabled(enabled);
        queryTimeoutSeconds.setEnabled(enabled);
        maxRows.setEnabled(enabled);
        // Testing needs permission to edit the document.
        testConnection.setEnabled(enabled);
    }

    private void fireChange() {
        if (getUiHandlers() != null) {
            getUiHandlers().onChange();
        }
    }

    /// Shows the settings of the chosen connection type.
    ///
    /// @param event The change.
    @UiHandler("connectionType")
    public void onConnectionType(final ValueChangeEvent<SqlConnectionType> event) {
        showSettingsFor(event.getValue());
        fireChange();
    }

    /// Marks the document as changed when a text setting changes.
    ///
    /// @param event The change.
    @UiHandler({"jdbcUrl", "connectionProperties", "region", "workGroup", "catalog", "database",
            "outputLocation", "timeField"})
    public void onTextChange(final ValueChangeEvent<String> event) {
        fireChange();
    }

    /// Marks the document as changed when a number setting changes.
    ///
    /// @param event The change.
    @UiHandler({"maxPoolSize", "fetchSize", "resultReuseMinutes", "queryTimeoutSeconds", "maxRows"})
    public void onNumberChange(final ValueChangeEvent<Long> event) {
        fireChange();
    }

    /// Marks the document as changed when a credential is chosen.
    ///
    /// @param event The change.
    @UiHandler({"jdbcCredential", "athenaCredential"})
    public void onCredentialChange(final ValueChangeEvent<Credential> event) {
        fireChange();
    }

    /// Tests the connection with the settings as they stand.
    ///
    /// @param event The click.
    @UiHandler("testConnection")
    public void onTestConnection(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onTestConnection(testConnection);
        }
    }


    // --------------------------------------------------------------------------------


    /// Binds the UiBinder template.
    public interface Binder extends UiBinder<Widget, SqlDataSourceSettingsViewImpl> {

    }
}
