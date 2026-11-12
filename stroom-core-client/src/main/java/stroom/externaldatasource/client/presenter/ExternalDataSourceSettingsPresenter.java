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

package stroom.externaldatasource.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.core.client.LocationManager;
import stroom.credentials.client.presenter.CredentialClient;
import stroom.credentials.client.presenter.CredentialListModel;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.DocPresenter;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.externaldatasource.client.presenter.ExternalDataSourceSettingsPresenter.ExternalDataSourceSettingsView;
import stroom.externaldatasource.shared.AthenaConnectionSettings;
import stroom.externaldatasource.shared.CsvSettings;
import stroom.externaldatasource.shared.DataColumn;
import stroom.externaldatasource.shared.DataColumnType;
import stroom.externaldatasource.shared.ExcelSettings;
import stroom.externaldatasource.shared.ExternalDataSourceDoc;
import stroom.externaldatasource.shared.ExternalDataSourceResource;
import stroom.externaldatasource.shared.ExternalDataSourceType;
import stroom.externaldatasource.shared.JdbcConnectionSettings;
import stroom.externaldatasource.shared.LdapSearchScope;
import stroom.externaldatasource.shared.LdapSettings;
import stroom.importexport.client.presenter.ImportUtil;
import stroom.item.client.SelectionBox;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourceKey;
import stroom.widget.form.client.CustomFileUpload;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/// The settings tab of an External Data Source: its type, the settings of that type, and its limits.
///
/// JDBC, Amazon Athena and LDAP each have their own credential picker, listing only the credentials they
/// can use: usernames and passwords for JDBC and LDAP, AWS access keys for Athena. LDAP has a table of
/// the attributes read from each entry, which can be detected or added by name. CSV has an editor for the data,
/// which a file can be read into, and its columns. Excel has an upload for the workbook, which the editor
/// never holds, and its columns. The settings of every type are kept, so switching
/// type and back loses nothing.
public class ExternalDataSourceSettingsPresenter
        extends DocPresenter<ExternalDataSourceSettingsView, ExternalDataSourceDoc>
        implements ExternalDataSourceSettingsUiHandlers {

    private static final ExternalDataSourceResource EXTERNAL_DATA_SOURCE_RESOURCE =
            GWT.create(ExternalDataSourceResource.class);

    private final RestFactory restFactory;
    private final LocationManager locationManager;
    private final CredentialChoice jdbcCredential = new CredentialChoice();
    private final CredentialChoice athenaCredential = new CredentialChoice();
    private final CredentialChoice ldapCredential = new CredentialChoice();
    private final EditorPresenter csvEditor;
    // Counts reads and column detections, so a detection reply that arrives after the document was
    // read again, or after a later detection, is ignored.
    private int detection;
    // A workbook uploaded since the document was read, and its file name, sent when the document is
    // saved. The editor never has the workbook itself.
    private ResourceKey excelUploadKey;
    private String excelUploadName;
    private String excelChosenName;
    // True once the user has removed the workbook, until the document is read again.
    private boolean excelRemoved;

    /// @param eventBus         The event bus.
    /// @param view             The view.
    /// @param restFactory      Calls the server.
    /// @param credentialClient Looks up credentials.
    /// @param csvEditor        The editor of the CSV.
    /// @param locationManager  Downloads the workbook.
    @Inject
    public ExternalDataSourceSettingsPresenter(final EventBus eventBus,
                                               final ExternalDataSourceSettingsView view,
                                               final RestFactory restFactory,
                                               final CredentialClient credentialClient,
                                               final EditorPresenter csvEditor,
                                               final LocationManager locationManager) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.csvEditor = csvEditor;
        this.locationManager = locationManager;

        view.getExcelUpload().setAction(ImportUtil.getImportFileURL());
        // A workbook is uploaded as soon as it is chosen, and stored when the document is saved.
        view.getExcelUpload()
                .onFileChosen(this::uploadExcel)
                .onSuccess(this::onExcelUploaded)
                .onFailure(message -> AlertEvent.fireError(this, "Unable to upload the workbook", message, null))
                .taskMonitorFactory(this, "Uploading the workbook");

        // Plain text: CSV has no styling or formatting.
        csvEditor.setMode(AceEditorMode.TEXT);
        csvEditor.getStylesOption().setUnavailable();
        csvEditor.getFormatAction().setUnavailable();
        registerHandler(csvEditor.addValueChangeHandler(event -> onChange()));
        view.setCsvEditor(csvEditor.getWidget());

        view.getJdbcCredentialSelectionBox().setModel(credentialModel(
                eventBus, credentialClient, CredentialType.USERNAME_PASSWORD));
        view.getAthenaCredentialSelectionBox().setModel(credentialModel(
                eventBus, credentialClient, CredentialType.AWS_KEY));
        view.getLdapCredentialSelectionBox().setModel(credentialModel(
                eventBus, credentialClient, CredentialType.USERNAME_PASSWORD));
        registerHandler(view.getJdbcCredentialSelectionBox().addValueChangeHandler(event -> {
            jdbcCredential.pick(event.getValue());
            view.setJdbcCredentialNote(null);
        }));
        registerHandler(view.getAthenaCredentialSelectionBox().addValueChangeHandler(event -> {
            athenaCredential.pick(event.getValue());
            view.setAthenaCredentialNote(null);
        }));
        registerHandler(view.getLdapCredentialSelectionBox().addValueChangeHandler(event -> {
            ldapCredential.pick(event.getValue());
            view.setLdapCredentialNote(null);
        }));
        view.setUiHandlers(this);
    }

    private CredentialListModel credentialModel(final EventBus eventBus,
                                                final CredentialClient credentialClient,
                                                final CredentialType credentialType) {
        final CredentialListModel model = new CredentialListModel(eventBus, credentialClient, Set.of(credentialType));
        model.setTaskMonitorFactory(this);
        return model;
    }

    @Override
    protected void onRead(final DocRef docRef, final ExternalDataSourceDoc doc, final boolean readOnly) {
        final ExternalDataSourceSettingsView view = getView();
        view.setSourceType(Objects.requireNonNullElse(doc.getSourceType(), ExternalDataSourceType.JDBC));

        final JdbcConnectionSettings jdbc = Objects.requireNonNullElse(
                doc.getJdbc(),
                JdbcConnectionSettings.builder().build());
        view.setJdbcUrl(jdbc.getUrl());
        view.setConnectionProperties(ConnectionPropertiesText.format(jdbc.getConnectionProperties()));
        view.setMaxPoolSize(Objects.requireNonNullElse(
                jdbc.getMaxPoolSize(),
                JdbcConnectionSettings.DEFAULT_MAX_POOL_SIZE));
        view.setFetchSize(Objects.requireNonNullElse(jdbc.getFetchSize(), JdbcConnectionSettings.DEFAULT_FETCH_SIZE));
        view.setJdbcMinRefreshMinutes(Objects.requireNonNullElse(jdbc.getMinRefreshMinutes(), 0));
        showCredential(jdbc.getCredentialName(),
                jdbcCredential,
                view.getJdbcCredentialSelectionBox(),
                view::setJdbcCredentialNote);

        final AthenaConnectionSettings athena = Objects.requireNonNullElse(
                doc.getAthena(),
                AthenaConnectionSettings.builder().build());
        view.setRegion(athena.getRegion());
        view.setWorkGroup(Objects.requireNonNullElse(
                athena.getWorkGroup(),
                AthenaConnectionSettings.DEFAULT_WORK_GROUP));
        view.setCatalog(Objects.requireNonNullElse(athena.getCatalog(), AthenaConnectionSettings.DEFAULT_CATALOG));
        view.setDatabase(Objects.requireNonNullElse(
                athena.getDatabase(),
                AthenaConnectionSettings.DEFAULT_DATABASE));
        view.setOutputLocation(athena.getOutputLocation());
        view.setResultReuseMinutes(Objects.requireNonNullElse(athena.getResultReuseMinutes(), 0));
        view.setAthenaMinRefreshMinutes(Objects.requireNonNullElse(
                athena.getMinRefreshMinutes(),
                AthenaConnectionSettings.DEFAULT_MIN_REFRESH_MINUTES));
        view.setAthenaMaxDataScannedMb(Objects.requireNonNullElse(athena.getMaxDataScannedMb(), 0));
        showCredential(athena.getCredentialName(),
                athenaCredential,
                view.getAthenaCredentialSelectionBox(),
                view::setAthenaCredentialNote);

        final LdapSettings ldap = Objects.requireNonNullElse(doc.getLdap(), LdapSettings.builder().build());
        view.setLdapUrl(ldap.getUrl());
        view.setLdapStartTls(Boolean.TRUE.equals(ldap.getStartTls()));
        view.setLdapBaseDn(ldap.getBaseDn());
        view.setLdapScope(Objects.requireNonNullElse(ldap.getScope(), LdapSearchScope.SUBTREE));
        view.setLdapPageSize(Objects.requireNonNullElse(ldap.getPageSize(), LdapSettings.DEFAULT_PAGE_SIZE));
        view.setLdapMinRefreshMinutes(Objects.requireNonNullElse(ldap.getMinRefreshMinutes(), 0));
        view.setLdapAttributes(NullSafe.list(ldap.getAttributes()));
        showCredential(ldap.getCredentialName(),
                ldapCredential,
                view.getLdapCredentialSelectionBox(),
                view::setLdapCredentialNote);

        final CsvSettings csv = Objects.requireNonNullElse(doc.getCsv(), CsvSettings.builder().build());
        view.setCsvDelimiter(csv.getDelimiter());
        view.setCsvHeader(!Boolean.FALSE.equals(csv.getHeader()));
        view.setCsvColumns(NullSafe.list(csv.getColumns()));
        csvEditor.setText(NullSafe.string(csv.getData()));
        csvEditor.setReadOnly(readOnly);
        // Setting read-only restores the Format action, which CSV has no use for.
        csvEditor.getFormatAction().setUnavailable();

        final ExcelSettings excel = Objects.requireNonNullElse(doc.getExcel(), ExcelSettings.builder().build());
        excelUploadKey = null;
        excelUploadName = null;
        excelRemoved = false;
        view.getExcelUpload().reset();
        view.setExcelFile(excel.getFileName(), excel.getFileName() != null);
        view.setExcelSheet(excel.getSheet());
        view.setExcelHeader(!Boolean.FALSE.equals(excel.getHeader()));
        view.setExcelColumns(NullSafe.list(excel.getColumns()));
        detection++;

        view.setTimeField(doc.getTimeField());
        view.setQueryTimeoutSeconds(Objects.requireNonNullElse(
                doc.getQueryTimeoutSeconds(),
                ExternalDataSourceDoc.DEFAULT_QUERY_TIMEOUT_SECONDS));
        view.setMaxRows(Objects.requireNonNullElse(doc.getMaxRows(), ExternalDataSourceDoc.DEFAULT_MAX_ROWS));
    }

    /// Shows the document's credential in its picker, once looked up. If it cannot be shown, as it
    /// is gone or the user may not use it, a note says so and the document keeps it.
    private void showCredential(final String credentialName,
                                final CredentialChoice choice,
                                final SelectionBox<Credential> selectionBox,
                                final Consumer<String> noteConsumer) {
        choice.reset(credentialName);
        selectionBox.setValue(null);
        noteConsumer.accept(null);
        if (!NullSafe.isBlankString(credentialName)) {
            restFactory
                    .create(CredentialClient.CREDENTIALS_RESOURCE)
                    .method(res -> res.getCredentialByName(credentialName))
                    .onSuccess(credential -> {
                        // The user may have picked another while the lookup ran, or the
                        // document may have been read again.
                        if (choice.awaits(credentialName)) {
                            if (credential == null) {
                                noteConsumer.accept(choice.unresolvedNote());
                            } else {
                                selectionBox.setValue(credential);
                            }
                        }
                    })
                    .onFailure(error -> {
                        if (choice.awaits(credentialName)) {
                            noteConsumer.accept(choice.unresolvedNote());
                        }
                    })
                    .taskMonitorFactory(this)
                    .exec();
        }
    }

    @Override
    protected ExternalDataSourceDoc onWrite(final ExternalDataSourceDoc doc) {
        final ExternalDataSourceSettingsView view = getView();
        final JdbcConnectionSettings jdbc = JdbcConnectionSettings.builder()
                .url(blankToNull(view.getJdbcUrl()))
                .credentialName(jdbcCredential.getName())
                .connectionProperties(ConnectionPropertiesText.parse(view.getConnectionProperties()))
                .maxPoolSize(view.getMaxPoolSize())
                .fetchSize(view.getFetchSize())
                .minRefreshMinutes(view.getJdbcMinRefreshMinutes())
                .build();
        final AthenaConnectionSettings athena = AthenaConnectionSettings.builder()
                .region(blankToNull(view.getRegion()))
                .workGroup(blankToNull(view.getWorkGroup()))
                .catalog(blankToNull(view.getCatalog()))
                .database(blankToNull(view.getDatabase()))
                .outputLocation(blankToNull(view.getOutputLocation()))
                .credentialName(athenaCredential.getName())
                .resultReuseMinutes(view.getResultReuseMinutes())
                .minRefreshMinutes(view.getAthenaMinRefreshMinutes())
                .maxDataScannedMb(maxDataScannedMb(doc))
                .build();
        return doc.copy()
                .sourceType(view.getSourceType())
                .jdbc(jdbc)
                .athena(athena)
                .ldap(writeLdap(doc.getLdap()))
                .csv(writeCsv(doc.getCsv()))
                .excel(writeExcel(doc))
                .timeField(blankToNull(view.getTimeField()))
                .queryTimeoutSeconds(view.getQueryTimeoutSeconds())
                .maxRows(view.getMaxRows())
                .build();
    }

    // No LDAP settings for a document that has never had any, as for CSV. A value the document left
    // unset is kept unset while the editor shows its default.
    private LdapSettings writeLdap(final LdapSettings stored) {
        final ExternalDataSourceSettingsView view = getView();
        final String url = blankToNull(view.getLdapUrl());
        final String baseDn = blankToNull(view.getLdapBaseDn());
        final List<DataColumn> attributes = view.getLdapAttributes();
        if (stored == null
            && url == null
            && baseDn == null
            && ldapCredential.getName() == null
            && attributes.isEmpty()
            && view.getSourceType() != ExternalDataSourceType.LDAP) {
            return null;
        }
        final LdapSettings previous = Objects.requireNonNullElse(stored, LdapSettings.builder().build());
        final boolean startTls = view.isLdapStartTls();
        final LdapSearchScope scope = view.getLdapScope();
        final int pageSize = view.getLdapPageSize();
        final int minRefreshMinutes = view.getLdapMinRefreshMinutes();
        return LdapSettings.builder()
                .url(url)
                .startTls(previous.getStartTls() == null && !startTls
                        ? null
                        : startTls)
                .credentialName(ldapCredential.getName())
                .baseDn(baseDn)
                .scope(previous.getScope() == null && scope == LdapSearchScope.SUBTREE
                        ? null
                        : scope)
                .attributes(sameColumns(previous.getAttributes(), attributes)
                        ? previous.getAttributes()
                        : attributes)
                .pageSize(previous.getPageSize() == null && pageSize == LdapSettings.DEFAULT_PAGE_SIZE
                        ? null
                        : pageSize)
                .minRefreshMinutes(previous.getMinRefreshMinutes() == null && minRefreshMinutes == 0
                        ? null
                        : minRefreshMinutes)
                .build();
    }

    // No CSV settings for a document that has never had any, so its JSON stays as it was. A value the
    // document left unset is kept unset while the editor shows its default, so reading a document and
    // writing it back changes nothing, and the document is not marked as changed.
    private CsvSettings writeCsv(final CsvSettings stored) {
        final ExternalDataSourceSettingsView view = getView();
        final String data = csvEditor.getText();
        final List<DataColumn> columns = view.getCsvColumns();
        if (stored == null
            && NullSafe.isEmptyString(data)
            && columns.isEmpty()
            && view.getSourceType() != ExternalDataSourceType.CSV) {
            return null;
        }
        final CsvSettings previous = Objects.requireNonNullElse(stored, CsvSettings.builder().build());
        final String delimiter = view.getCsvDelimiter();
        final boolean header = view.isCsvHeader();
        return CsvSettings.builder()
                .delimiter(previous.getDelimiter() == null && CsvSettings.DEFAULT_DELIMITER.equals(delimiter)
                        ? null
                        : delimiter)
                .header(previous.getHeader() == null && header
                        ? null
                        : header)
                .columns(sameColumns(previous.getColumns(), columns)
                        ? previous.getColumns()
                        : columns)
                .data(NullSafe.isEmptyString(data)
                        ? null
                        : data)
                .build();
    }

    // No Excel settings for a document that has never had any, as for CSV. Saving a copy of the
    // document, which has a new UUID, names the document read so the server copies its workbook.
    private ExcelSettings writeExcel(final ExternalDataSourceDoc doc) {
        final ExternalDataSourceSettingsView view = getView();
        final ExcelSettings stored = doc.getExcel();
        final ExcelSettings read = NullSafe.get(getEntity(), ExternalDataSourceDoc::getExcel);
        final List<DataColumn> columns = view.getExcelColumns();
        final String sheet = blankToNull(view.getExcelSheet());
        if (stored == null
            && read == null
            && excelUploadKey == null
            && sheet == null
            && columns.isEmpty()
            && view.getSourceType() != ExternalDataSourceType.EXCEL) {
            return null;
        }
        final ExcelSettings previous = Objects.requireNonNullElse(
                Objects.requireNonNullElse(stored, read),
                ExcelSettings.builder().build());
        final boolean header = view.isExcelHeader();
        final boolean savingACopy = getEntity() != null
                                    && !Objects.equals(doc.getUuid(), getEntity().getUuid());
        return ExcelSettings.builder()
                // The server keeps the stored workbook while a file name is sent, and removes it
                // when none is.
                .fileName(excelUploadKey != null
                        ? excelUploadName
                        : excelRemoved
                                ? null
                                : NullSafe.get(read, ExcelSettings::getFileName))
                .sheet(sheet)
                .header(previous.getHeader() == null && header
                        ? null
                        : header)
                .columns(sameColumns(previous.getColumns(), columns)
                        ? previous.getColumns()
                        : columns)
                .uploadKey(excelUploadKey)
                // The server copies nothing if the original holds no workbook.
                .copyWorkbookFrom(savingACopy && excelUploadKey == null && !excelRemoved && read != null
                        ? getEntity().getUuid()
                        : null)
                .build();
    }

    // The editor shows a column with no type as Text, and no columns as an empty list.
    private static boolean sameColumns(final List<DataColumn> stored, final List<DataColumn> shown) {
        final List<DataColumn> storedColumns = NullSafe.list(stored);
        if (storedColumns.size() != shown.size()) {
            return false;
        }
        for (int index = 0; index < shown.size(); index++) {
            final DataColumn storedColumn = storedColumns.get(index);
            final DataColumn shownColumn = shown.get(index);
            if (!Objects.equals(storedColumn.getName(), shownColumn.getName())
                || Objects.requireNonNullElse(storedColumn.getType(), DataColumnType.TEXT)
                   != shownColumn.getType()) {
                return false;
            }
        }
        return true;
    }

    // No limit stays unset, so a document without one keeps the same JSON.
    private Integer maxDataScannedMb(final ExternalDataSourceDoc doc) {
        final int shown = getView().getAthenaMaxDataScannedMb();
        final Integer stored = NullSafe.get(doc.getAthena(), AthenaConnectionSettings::getMaxDataScannedMb);
        return shown == 0 && stored == null
                ? null
                : shown;
    }

    private static String blankToNull(final String value) {
        return NullSafe.isBlankString(value)
                ? null
                : value.trim();
    }

    @Override
    public void onTestConnection(final TaskMonitorFactory taskMonitorFactory) {
        // Send the document as edited, with its real identity, as the server checks the user can
        // edit it before testing it with the credential it names.
        final ExternalDataSourceDoc doc = onWrite(getEntity());
        restFactory
                .create(EXTERNAL_DATA_SOURCE_RESOURCE)
                .method(res -> res.testConnection(doc))
                .onSuccess(result -> {
                    if (result.isOk()) {
                        AlertEvent.fireInfo(this, "Connection Success", result.getMessage(), null);
                    } else {
                        AlertEvent.fireError(this, "Connection Failure", result.getMessage(), null);
                    }
                })
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public void onDetectColumns(final TaskMonitorFactory taskMonitorFactory) {
        // Send the CSV as edited, with the document's identity, as the server checks the user can
        // edit it.
        final ExternalDataSourceDoc doc = onWrite(getEntity());
        final ExternalDataSourceType type = doc.getSourceType();
        final int thisDetection = ++detection;
        restFactory
                .create(EXTERNAL_DATA_SOURCE_RESOURCE)
                .method(res -> res.detectColumns(doc))
                .onSuccess(columns -> {
                    if (thisDetection == detection) {
                        if (ExternalDataSourceType.LDAP.equals(type)) {
                            getView().setLdapAttributes(columns);
                        } else if (ExternalDataSourceType.EXCEL.equals(type)) {
                            getView().setExcelColumns(columns);
                        } else {
                            getView().setCsvColumns(columns);
                        }
                        onChange();
                    }
                })
                .onFailure(error -> {
                    if (thisDetection == detection) {
                        AlertEvent.fireError(this,
                                ExternalDataSourceType.LDAP.equals(type)
                                        ? "Unable to detect the attributes"
                                        : "Unable to detect the columns",
                                error.getMessage(),
                                null);
                    }
                })
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public void onDataFormatChange(final TaskMonitorFactory taskMonitorFactory) {
        onChange();
        if (hasData()) {
            onDetectColumns(taskMonitorFactory);
        }
    }

    private boolean hasData() {
        if (ExternalDataSourceType.EXCEL.equals(getView().getSourceType())) {
            return excelUploadKey != null
                   || (!excelRemoved
                       && NullSafe.get(getEntity(), ExternalDataSourceDoc::getExcel, ExcelSettings::getFileName)
                          != null);
        }
        return !NullSafe.isBlankString(csvEditor.getText());
    }

    // The file name is taken now, as the user may choose another before the upload finishes.
    private void uploadExcel() {
        excelChosenName = fileName(getView().getExcelUpload().getFilename());
        getView().getExcelUpload().submit();
    }

    // The workbook is held by the server under the key until the document is saved.
    private void onExcelUploaded(final ResourceKey resourceKey) {
        excelUploadKey = resourceKey;
        excelRemoved = false;
        excelUploadName = excelChosenName;
        getView().setExcelFile(excelUploadName + " (uploaded; stored when you save)", false);
        onChange();
        // The user may have chosen another type while the workbook was uploading.
        if (ExternalDataSourceType.EXCEL.equals(getView().getSourceType())) {
            onDetectColumns(this);
        }
    }

    // The browser gives a path such as C:\fakepath\teams.xlsx.
    private static String fileName(final String path) {
        if (path == null) {
            return null;
        }
        final int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return path.substring(slash + 1);
    }

    @Override
    public void onRemoveExcel() {
        excelUploadKey = null;
        excelUploadName = null;
        excelRemoved = true;
        getView().getExcelUpload().reset();
        getView().setExcelFile(null, false);
        onChange();
    }

    @Override
    public void onDownloadExcel(final TaskMonitorFactory taskMonitorFactory) {
        final String uuid = getEntity().getUuid();
        restFactory
                .create(EXTERNAL_DATA_SOURCE_RESOURCE)
                .method(res -> res.downloadExcelFile(uuid))
                .onSuccess(result -> ExportFileCompleteUtil.onSuccess(locationManager, this, result))
                .onFailure(error -> AlertEvent.fireError(this, "Unable to download the workbook",
                        error.getMessage(), null))
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public void onCsvFileRead(final String text) {
        csvEditor.setText(text);
        onChange();
        onDetectColumns(this);
    }

    @Override
    public void onCsvFileError(final String message) {
        AlertEvent.fireError(this, "Unable to read the file", message, null);
    }


    // --------------------------------------------------------------------------------


    /// The view of an External Data Source's settings.
    public interface ExternalDataSourceSettingsView
            extends View, ReadOnlyChangeHandler, HasUiHandlers<ExternalDataSourceSettingsUiHandlers> {

        /// @return The type chosen.
        ExternalDataSourceType getSourceType();

        /// Shows a type, with the settings that go with it.
        ///
        /// @param sourceType The type.
        void setSourceType(ExternalDataSourceType sourceType);

        /// @return The JDBC URL as entered.
        String getJdbcUrl();

        /// @param jdbcUrl The JDBC URL to show, or `null`.
        void setJdbcUrl(String jdbcUrl);

        /// @return The picker of the JDBC username and password credential.
        SelectionBox<Credential> getJdbcCredentialSelectionBox();

        /// Shows or hides a note about the JDBC credential.
        ///
        /// @param note The note, or `null` to hide it.
        void setJdbcCredentialNote(String note);

        /// @return The connection properties as entered, one `name=value` per line.
        String getConnectionProperties();

        /// @param connectionProperties The connection properties to show.
        void setConnectionProperties(String connectionProperties);

        /// @return The largest connection pool.
        int getMaxPoolSize();

        /// @param maxPoolSize The largest connection pool to show.
        void setMaxPoolSize(int maxPoolSize);

        /// @return The rows fetched in each round trip.
        int getFetchSize();

        /// @param fetchSize The fetch size to show.
        void setFetchSize(int fetchSize);

        /// @return The shortest automatic dashboard refresh of a JDBC source, in minutes; 0 for none.
        int getJdbcMinRefreshMinutes();

        /// @param minutes The shortest automatic dashboard refresh of a JDBC source to show.
        void setJdbcMinRefreshMinutes(int minutes);

        /// @return The AWS region as entered.
        String getRegion();

        /// @param region The AWS region to show, or `null`.
        void setRegion(String region);

        /// @return The Athena workgroup as entered.
        String getWorkGroup();

        /// @param workGroup The Athena workgroup to show.
        void setWorkGroup(String workGroup);

        /// @return The data catalog as entered.
        String getCatalog();

        /// @param catalog The data catalog to show.
        void setCatalog(String catalog);

        /// @return The database as entered.
        String getDatabase();

        /// @param database The database to show.
        void setDatabase(String database);

        /// @return The S3 output location as entered.
        String getOutputLocation();

        /// @param outputLocation The S3 output location to show, or `null`.
        void setOutputLocation(String outputLocation);

        /// @return The picker of the AWS access key credential.
        SelectionBox<Credential> getAthenaCredentialSelectionBox();

        /// Shows or hides a note about the AWS access key credential.
        ///
        /// @param note The note, or `null` to hide it.
        void setAthenaCredentialNote(String note);

        /// @return How old an Athena result may be and still be reused, in minutes.
        int getResultReuseMinutes();

        /// @param resultReuseMinutes The result reuse age to show.
        void setResultReuseMinutes(int resultReuseMinutes);

        /// @return The shortest automatic dashboard refresh of an Athena source, in minutes; 0 for none.
        int getAthenaMinRefreshMinutes();

        /// @param minutes The shortest automatic dashboard refresh of an Athena source to show.
        void setAthenaMinRefreshMinutes(int minutes);

        /// @return The most data, in MB, an Athena query may scan before Stroom stops it; 0 for no limit.
        int getAthenaMaxDataScannedMb();

        /// @param megabytes The Athena data scan limit to show, in MB; 0 for none.
        void setAthenaMaxDataScannedMb(int megabytes);

        /// @return The LDAP URL as entered.
        String getLdapUrl();

        /// @param url The LDAP URL to show, or `null`.
        void setLdapUrl(String url);

        /// @return True if StartTLS is to be used on an `ldap://` connection.
        boolean isLdapStartTls();

        /// @param startTls True if StartTLS is to be used on an `ldap://` connection.
        void setLdapStartTls(boolean startTls);

        /// @return The picker of the LDAP bind credential.
        SelectionBox<Credential> getLdapCredentialSelectionBox();

        /// Shows or hides a note about the LDAP bind credential.
        ///
        /// @param note The note, or `null` to hide it.
        void setLdapCredentialNote(String note);

        /// @return The base DN as entered.
        String getLdapBaseDn();

        /// @param baseDn The base DN to show, or `null`.
        void setLdapBaseDn(String baseDn);

        /// @return The search scope chosen.
        LdapSearchScope getLdapScope();

        /// @param scope The search scope to show.
        void setLdapScope(LdapSearchScope scope);

        /// @return The entries fetched in each page.
        int getLdapPageSize();

        /// @param pageSize The page size to show.
        void setLdapPageSize(int pageSize);

        /// @return The shortest automatic dashboard refresh of an LDAP source, in minutes; 0 for none.
        int getLdapMinRefreshMinutes();

        /// @param minutes The shortest automatic dashboard refresh of an LDAP source to show.
        void setLdapMinRefreshMinutes(int minutes);

        /// @return The attributes read from each entry, with the types chosen.
        List<DataColumn> getLdapAttributes();

        /// @param attributes The attributes to show.
        void setLdapAttributes(List<DataColumn> attributes);

        /// @return The CSV delimiter chosen.
        String getCsvDelimiter();

        /// @param delimiter The CSV delimiter to show, or `null` for the default.
        void setCsvDelimiter(String delimiter);

        /// @return True if the CSV's first row is a header.
        boolean isCsvHeader();

        /// @param header True if the CSV's first row is a header.
        void setCsvHeader(boolean header);

        /// @param editor The editor of the CSV, to show in the CSV settings.
        void setCsvEditor(Widget editor);

        /// @return The CSV's columns, with the types chosen.
        List<DataColumn> getCsvColumns();

        /// @param columns The CSV's columns to show.
        void setCsvColumns(List<DataColumn> columns);

        /// Shows the workbook held, or about to be.
        ///
        /// @param fileName    Its file name, or `null` for none.
        /// @param canDownload True if a stored workbook can be downloaded.
        void setExcelFile(String fileName, boolean canDownload);

        /// @return The file input a workbook is uploaded from.
        CustomFileUpload getExcelUpload();

        /// @return The sheet as entered.
        String getExcelSheet();

        /// @param sheet The sheet to show, or `null` for the first.
        void setExcelSheet(String sheet);

        /// @return True if the sheet's first row is a header.
        boolean isExcelHeader();

        /// @param header True if the sheet's first row is a header.
        void setExcelHeader(boolean header);

        /// @return The workbook's columns, with the types chosen.
        List<DataColumn> getExcelColumns();

        /// @param columns The workbook's columns to show.
        void setExcelColumns(List<DataColumn> columns);

        /// @return The time column as entered.
        String getTimeField();

        /// @param timeField The time column to show, or `null`.
        void setTimeField(String timeField);

        /// @return The query timeout in seconds.
        int getQueryTimeoutSeconds();

        /// @param queryTimeoutSeconds The query timeout to show.
        void setQueryTimeoutSeconds(int queryTimeoutSeconds);

        /// @return The row limit.
        int getMaxRows();

        /// @param maxRows The row limit to show.
        void setMaxRows(int maxRows);
    }
}
