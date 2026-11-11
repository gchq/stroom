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

package stroom.externaldatasource.client.view;

import stroom.credentials.shared.Credential;
import stroom.externaldatasource.client.presenter.CsvDelimiter;
import stroom.externaldatasource.client.presenter.ExternalDataSourceSettingsPresenter.ExternalDataSourceSettingsView;
import stroom.externaldatasource.client.presenter.ExternalDataSourceSettingsUiHandlers;
import stroom.externaldatasource.shared.DataColumn;
import stroom.externaldatasource.shared.ExternalDataSourceType;
import stroom.item.client.SelectionBox;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Button;
import stroom.widget.form.client.CustomFileUpload;
import stroom.widget.form.client.DescriptionHTML;
import stroom.widget.tickbox.client.view.CustomCheckBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.FileUpload;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.List;

/// The settings of an External Data Source. Only the settings of the chosen type are shown.
public class ExternalDataSourceSettingsViewImpl
        extends ViewWithUiHandlers<ExternalDataSourceSettingsUiHandlers>
        implements ExternalDataSourceSettingsView {

    private static final int MAX_POOL_SIZE = 100;
    private static final int MAX_FETCH_SIZE = 100_000;
    private static final int MAX_TIMEOUT_SECONDS = 86_400;
    private static final int MAX_ROWS = 100_000_000;
    // Athena reuses a result for at most seven days.
    private static final int MAX_RESULT_REUSE_MINUTES = 10_080;
    private static final int MAX_MIN_REFRESH_MINUTES = 1_440;
    // 10 TB, far beyond any lookup a dashboard should make.
    private static final int MAX_DATA_SCANNED_MB = 10_000_000;
    // Well above the default stored data limit of 5 MiB: the server enforces the real limit, this only
    // stops a huge file hanging the browser on its way into the editor.
    private static final double MAX_FILE_BYTES = 50 * 1024 * 1024;

    private final Widget widget;
    private boolean readOnly;
    private boolean hasWorkbook;
    // A stored delimiter that is not one of those offered, kept until the user picks another, so
    // saving does not change it.
    private String unlistedDelimiter;

    @UiField
    SelectionBox<ExternalDataSourceType> sourceType;
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
    ValueSpinner jdbcMinRefreshMinutes;
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
    ValueSpinner athenaMinRefreshMinutes;
    @UiField
    ValueSpinner athenaMaxDataScannedMb;
    @UiField
    FlowPanel csvSettings;
    @UiField
    SelectionBox<CsvDelimiter> csvDelimiter;
    @UiField
    CustomCheckBox csvHeader;
    @UiField
    SimplePanel csvEditor;
    @UiField
    Button loadCsvFile;
    @UiField
    Button detectColumns;
    @UiField
    FileUpload csvFile;
    @UiField
    DataColumnsPanel csvColumns;
    @UiField
    FlowPanel excelSettings;
    @UiField
    DescriptionHTML excelFileName;
    @UiField
    CustomFileUpload excelFile;
    @UiField
    Button downloadExcel;
    @UiField
    Button removeExcel;
    @UiField
    TextBox excelSheet;
    @UiField
    CustomCheckBox excelHeader;
    @UiField
    Button detectExcelColumns;
    @UiField
    DataColumnsPanel excelColumns;
    @UiField
    FlowPanel timeFieldSettings;
    @UiField
    TextBox timeField;
    @UiField
    FlowPanel queryTimeoutSettings;
    @UiField
    ValueSpinner queryTimeoutSeconds;
    @UiField
    ValueSpinner maxRows;
    @UiField
    Button testConnection;

    /// @param binder Binds the UiBinder template.
    @Inject
    public ExternalDataSourceSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        sourceType.addItems(ExternalDataSourceType.values());
        csvDelimiter.addItems(CsvDelimiter.values());
        csvDelimiter.setValue(CsvDelimiter.COMMA);
        csvHeader.setValue(true);
        loadCsvFile.setIcon(SvgImage.UPLOAD);
        csvColumns.setChangeHandler(this::fireChange);
        excelHeader.setValue(true);
        downloadExcel.setIcon(SvgImage.DOWNLOAD);
        removeExcel.setIcon(SvgImage.DELETE);
        excelColumns.setChangeHandler(this::fireChange);
        setExcelFile(null, false);
        setJdbcCredentialNote(null);
        setAthenaCredentialNote(null);
        setLimits(maxPoolSize, 1, MAX_POOL_SIZE);
        setLimits(fetchSize, 0, MAX_FETCH_SIZE);
        setLimits(resultReuseMinutes, 0, MAX_RESULT_REUSE_MINUTES);
        setLimits(jdbcMinRefreshMinutes, 0, MAX_MIN_REFRESH_MINUTES);
        setLimits(athenaMinRefreshMinutes, 0, MAX_MIN_REFRESH_MINUTES);
        setLimits(athenaMaxDataScannedMb, 0, MAX_DATA_SCANNED_MB);
        setLimits(queryTimeoutSeconds, 1, MAX_TIMEOUT_SECONDS);
        setLimits(maxRows, 1, MAX_ROWS);
        testConnection.setIcon(SvgImage.OK);
        setSourceType(ExternalDataSourceType.JDBC);
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
    public ExternalDataSourceType getSourceType() {
        return sourceType.getValue();
    }

    @Override
    public void setSourceType(final ExternalDataSourceType sourceType) {
        this.sourceType.setValue(sourceType);
        showSettingsFor(sourceType);
    }

    private void showSettingsFor(final ExternalDataSourceType sourceType) {
        final boolean athena = ExternalDataSourceType.ATHENA.equals(sourceType);
        final boolean csv = ExternalDataSourceType.CSV.equals(sourceType);
        final boolean excel = ExternalDataSourceType.EXCEL.equals(sourceType);
        final boolean jdbc = !athena && !csv && !excel;
        jdbcSettings.setVisible(jdbc);
        athenaSettings.setVisible(athena);
        csvSettings.setVisible(csv);
        excelSettings.setVisible(excel);
        // Athena takes the time range in its SQL. CSV and Excel are read in Stroom, so they have no
        // query timeout.
        timeFieldSettings.setVisible(!athena);
        queryTimeoutSettings.setVisible(!csv && !excel);
        if (csv) {
            testConnection.setText("Check CSV");
        } else if (excel) {
            testConnection.setText("Check Workbook");
        } else {
            testConnection.setText("Test Connection");
        }
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
    public int getJdbcMinRefreshMinutes() {
        return jdbcMinRefreshMinutes.getIntValue();
    }

    @Override
    public void setJdbcMinRefreshMinutes(final int minutes) {
        jdbcMinRefreshMinutes.setValue(minutes);
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
    public int getAthenaMinRefreshMinutes() {
        return athenaMinRefreshMinutes.getIntValue();
    }

    @Override
    public void setAthenaMinRefreshMinutes(final int minutes) {
        athenaMinRefreshMinutes.setValue(minutes);
    }

    @Override
    public int getAthenaMaxDataScannedMb() {
        return athenaMaxDataScannedMb.getIntValue();
    }

    @Override
    public void setAthenaMaxDataScannedMb(final int megabytes) {
        athenaMaxDataScannedMb.setValue(megabytes);
    }

    @Override
    public String getCsvDelimiter() {
        return unlistedDelimiter != null
                ? unlistedDelimiter
                : csvDelimiter.getValue().getValue();
    }

    @Override
    public void setCsvDelimiter(final String delimiter) {
        final CsvDelimiter listed = CsvDelimiter.fromValue(delimiter);
        unlistedDelimiter = delimiter != null && !listed.getValue().equals(delimiter)
                ? delimiter
                : null;
        csvDelimiter.setValue(listed);
    }

    @Override
    public boolean isCsvHeader() {
        return csvHeader.getValue();
    }

    @Override
    public void setCsvHeader(final boolean header) {
        csvHeader.setValue(header);
    }

    @Override
    public void setCsvEditor(final Widget editor) {
        csvEditor.setWidget(editor);
    }

    @Override
    public List<DataColumn> getCsvColumns() {
        return csvColumns.getColumns();
    }

    @Override
    public void setCsvColumns(final List<DataColumn> columns) {
        csvColumns.setColumns(columns);
    }

    @Override
    public void setExcelFile(final String fileName, final boolean canDownload) {
        excelFileName.setText(fileName == null
                ? "No workbook has been uploaded."
                : fileName);
        downloadExcel.setEnabled(canDownload);
        hasWorkbook = fileName != null;
        removeExcel.setEnabled(hasWorkbook && !readOnly);
    }

    @Override
    public CustomFileUpload getExcelUpload() {
        return excelFile;
    }

    @Override
    public String getExcelSheet() {
        return excelSheet.getValue();
    }

    @Override
    public void setExcelSheet(final String sheet) {
        excelSheet.setValue(sheet);
    }

    @Override
    public boolean isExcelHeader() {
        return excelHeader.getValue();
    }

    @Override
    public void setExcelHeader(final boolean header) {
        excelHeader.setValue(header);
    }

    @Override
    public List<DataColumn> getExcelColumns() {
        return excelColumns.getColumns();
    }

    @Override
    public void setExcelColumns(final List<DataColumn> columns) {
        excelColumns.setColumns(columns);
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
        this.readOnly = readOnly;
        final boolean enabled = !readOnly;
        removeExcel.setEnabled(hasWorkbook && enabled);
        csvDelimiter.setEnabled(enabled);
        csvHeader.setEnabled(enabled);
        loadCsvFile.setEnabled(enabled);
        detectColumns.setEnabled(enabled);
        csvColumns.setEnabled(enabled);
        excelFile.setEnabled(enabled);
        excelSheet.setEnabled(enabled);
        excelHeader.setEnabled(enabled);
        detectExcelColumns.setEnabled(enabled);
        excelColumns.setEnabled(enabled);
        sourceType.setEnabled(enabled);
        jdbcUrl.setEnabled(enabled);
        jdbcCredential.setEnabled(enabled);
        connectionProperties.setEnabled(enabled);
        maxPoolSize.setEnabled(enabled);
        fetchSize.setEnabled(enabled);
        jdbcMinRefreshMinutes.setEnabled(enabled);
        region.setEnabled(enabled);
        workGroup.setEnabled(enabled);
        catalog.setEnabled(enabled);
        database.setEnabled(enabled);
        outputLocation.setEnabled(enabled);
        athenaCredential.setEnabled(enabled);
        resultReuseMinutes.setEnabled(enabled);
        athenaMinRefreshMinutes.setEnabled(enabled);
        athenaMaxDataScannedMb.setEnabled(enabled);
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

    /// Shows the settings of the chosen type.
    ///
    /// @param event The change.
    @UiHandler("sourceType")
    public void onSourceType(final ValueChangeEvent<ExternalDataSourceType> event) {
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
    @UiHandler({"maxPoolSize", "fetchSize", "jdbcMinRefreshMinutes", "resultReuseMinutes",
            "athenaMinRefreshMinutes", "athenaMaxDataScannedMb", "queryTimeoutSeconds", "maxRows"})
    public void onNumberChange(final ValueChangeEvent<Long> event) {
        fireChange();
    }

    /// Tells the presenter the CSV delimiter changed, as the columns may need detecting again.
    ///
    /// @param event The change.
    @UiHandler("csvDelimiter")
    public void onCsvDelimiterChange(final ValueChangeEvent<CsvDelimiter> event) {
        unlistedDelimiter = null;
        csvFormatChanged();
    }

    /// Tells the presenter the CSV header setting changed, as the columns may need detecting again.
    ///
    /// @param event The change.
    @UiHandler("csvHeader")
    public void onCsvHeaderChange(final ValueChangeEvent<Boolean> event) {
        csvFormatChanged();
    }

    private void csvFormatChanged() {
        if (getUiHandlers() != null) {
            getUiHandlers().onDataFormatChange(detectColumns);
        }
    }

    /// Tells the presenter the sheet changed, as the columns may need detecting again.
    ///
    /// @param event The change.
    @UiHandler("excelSheet")
    public void onExcelSheetChange(final ValueChangeEvent<String> event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onDataFormatChange(detectExcelColumns);
        }
    }

    /// Tells the presenter the Excel header setting changed, as the columns may need detecting again.
    ///
    /// @param event The change.
    @UiHandler("excelHeader")
    public void onExcelHeaderChange(final ValueChangeEvent<Boolean> event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onDataFormatChange(detectExcelColumns);
        }
    }

    /// Asks the presenter to remove the workbook when the document is saved.
    ///
    /// @param event The click.
    @UiHandler("removeExcel")
    public void onRemoveExcel(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onRemoveExcel();
        }
    }

    /// Asks the presenter to download the stored workbook.
    ///
    /// @param event The click.
    @UiHandler("downloadExcel")
    public void onDownloadExcel(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onDownloadExcel(downloadExcel);
        }
    }

    /// Asks the presenter to detect the workbook's columns.
    ///
    /// @param event The click.
    @UiHandler("detectExcelColumns")
    public void onDetectExcelColumns(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onDetectColumns(detectExcelColumns);
        }
    }

    /// Opens the browser's file chooser for a CSV file.
    ///
    /// @param event The click.
    @UiHandler("loadCsvFile")
    public void onLoadCsvFile(final ClickEvent event) {
        csvFile.click();
    }

    /// Reads the chosen CSV file in the browser and hands its text to the presenter.
    ///
    /// @param event The change.
    @UiHandler("csvFile")
    public void onCsvFileChosen(final ChangeEvent event) {
        if (getUiHandlers() != null) {
            final ExternalDataSourceSettingsUiHandlers handlers = getUiHandlers();
            LocalTextFile.read(
                    csvFile.getElement(),
                    MAX_FILE_BYTES,
                    handlers::onCsvFileRead,
                    handlers::onCsvFileError);
        }
        // So the same file can be chosen again.
        csvFile.getElement().setPropertyString("value", "");
    }

    /// Asks the presenter to detect the CSV's columns.
    ///
    /// @param event The click.
    @UiHandler("detectColumns")
    public void onDetectColumns(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onDetectColumns(detectColumns);
        }
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
    public interface Binder extends UiBinder<Widget, ExternalDataSourceSettingsViewImpl> {

    }
}
