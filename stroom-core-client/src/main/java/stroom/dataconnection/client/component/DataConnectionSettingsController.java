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

import stroom.alert.client.event.AlertEvent;
import stroom.core.client.LocationManager;
import stroom.credentials.client.CredentialClient;
import stroom.credentials.client.model.CredentialListModel;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.dataconnection.client.model.ConnectionPropertiesText;
import stroom.dataconnection.client.model.CredentialChoice;
import stroom.dataconnection.client.model.DataColumns;
import stroom.dataconnection.shared.AthenaConnectionSettings;
import stroom.dataconnection.shared.CsvSettings;
import stroom.dataconnection.shared.DataColumn;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.dataconnection.shared.DataConnectionResource;
import stroom.dataconnection.shared.DataConnectionType;
import stroom.dataconnection.shared.ExcelSettings;
import stroom.dataconnection.shared.JdbcConnectionSettings;
import stroom.dataconnection.shared.LdapSearchScope;
import stroom.dataconnection.shared.LdapSettings;
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.editor.client.component.CodeEditorController;
import stroom.importexport.client.ImportUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourceKey;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.widget.form.client.component.FileChooserController;
import stroom.widget.popup.client.component.NameDialogController;
import stroom.widget.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/// A Data Connection's Settings tab ([DataConnectionSettings]): reads the document into it and
/// writes it back, and tests the connection, detects columns, loads CSV from a file and uploads,
/// downloads and removes an Excel workbook. Read only as its document is.
///
/// JDBC, Amazon Athena and LDAP each have their own credential picker, listing only the credentials
/// they can use: usernames and passwords for JDBC and LDAP, AWS access keys for Athena. A stored
/// credential the user can't see is kept until they pick another ([CredentialChoice]). The settings of
/// every type are kept, so switching type and back loses nothing, and a value the document left
/// unset is kept unset while its default is shown, so reading a document and writing it back changes
/// nothing.
public class DataConnectionSettingsController extends Controller {

    private static final DataConnectionResource DATA_CONNECTION_RESOURCE =
            GWT.create(DataConnectionResource.class);
    // Well above the default stored data limit of 5 MiB: the server enforces the real limit, this
    // only stops a huge file hanging the browser on its way into the editor
    private static final double MAX_CSV_FILE_BYTES = 50 * 1024 * 1024;

    private final DataConnectionSettings view;
    private final RestFactory restFactory;
    private final CredentialClient credentialClient;
    private final LocationManager locationManager;
    private final Provider<NameDialogController> nameDialogProvider;
    private final CodeEditorController csvEditor;
    private final FileChooserController csvFile;
    private final FileChooserController excelFile;
    private final CredentialChoice jdbcCredential = new CredentialChoice();
    private final CredentialChoice athenaCredential = new CredentialChoice();
    private final CredentialChoice ldapCredential = new CredentialChoice();

    // The document as read, which a Save As copies the workbook from
    private DataConnectionDoc readDoc;
    // Counts reads and column detections, so a detection reply that comes after the document was
    // read again, or after a later detection, is dropped
    private int detection;
    // A workbook uploaded since the document was read, and its file name, sent when the document is
    // saved. The editor never has the workbook itself
    private ResourceKey excelUploadKey;
    private String excelUploadName;
    private String excelChosenName;
    // True once the user has removed the workbook, until the document is read again
    private boolean excelRemoved;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus                     The application's event bus.
    /// @param view                         The tab.
    /// @param restFactory                  Tests the connection, detects columns and downloads
    ///                                     the workbook.
    /// @param credentialClient             Finds the credentials.
    /// @param locationManager              Downloads the workbook.
    /// @param codeEditorControllerProvider Makes the CSV editor's controller.
    /// @param fileChooserProvider          Makes the controllers that read the CSV file and upload
    ///                                     the workbook.
    /// @param nameDialogProvider           Makes the dialog that asks for an attribute's name.
    @Inject
    public DataConnectionSettingsController(
            final EventBus eventBus,
            final DataConnectionSettings view,
            final RestFactory restFactory,
            final CredentialClient credentialClient,
            final LocationManager locationManager,
            final Provider<CodeEditorController> codeEditorControllerProvider,
            final Provider<FileChooserController> fileChooserProvider,
            final Provider<NameDialogController> nameDialogProvider) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.credentialClient = credentialClient;
        this.locationManager = locationManager;
        this.nameDialogProvider = nameDialogProvider;
        initWidget(view);

        final JdbcSection jdbc = view.getJdbc();
        final AthenaSection athena = view.getAthena();
        final LdapSection ldap = view.getLdap();
        final CsvSection csv = view.getCsv();
        final ExcelSection excel = view.getExcel();

        jdbc.setCredentialModel(credentialModel(eventBus, CredentialType.USERNAME_PASSWORD));
        athena.setCredentialModel(credentialModel(eventBus, CredentialType.AWS_KEY));
        ldap.setCredentialModel(credentialModel(eventBus, CredentialType.USERNAME_PASSWORD));
        jdbc.setOnCredentialChange(credential ->
                onCredentialPicked(jdbcCredential, credential, jdbc::setCredentialNote));
        athena.setOnCredentialChange(credential ->
                onCredentialPicked(athenaCredential, credential, athena::setCredentialNote));
        ldap.setOnCredentialChange(credential ->
                onCredentialPicked(ldapCredential, credential, ldap::setCredentialNote));

        // Plain text: CSV has no styling or formatting
        csvEditor = make(codeEditorControllerProvider).attach(csv.getEditor());
        csvEditor.setMode(AceEditorMode.TEXT);
        csvEditor.getOptions().getStylesOption().setUnavailable();
        csvEditor.getFormatAction().setUnavailable();
        own(csvEditor.addValueChangeHandler(event -> markChanged()));
        csvFile = make(fileChooserProvider);
        csvFile.attach(csv.getFileChooser());
        // A cancelled file dialog clears the input, which is a change with no file
        csv.getFileChooser().setOnChange(() -> {
            if (!csvFile.getFilename().isEmpty()) {
                onCsvFileChosen();
            }
        });

        // A workbook is uploaded as soon as it is chosen, and stored when the document is saved
        excelFile = make(fileChooserProvider);
        excelFile.attach(excel.getFileChooser());
        excelFile.setTaskMonitorFactory(null, "Uploading the workbook");
        excel.getFileChooser().setOnChange(() -> {
            if (!excelFile.getFilename().isEmpty()) {
                uploadExcel();
            }
        });

        view.setOnChange(this::markChanged);
        jdbc.setOnChange(this::markChanged);
        athena.setOnChange(this::markChanged);
        ldap.setOnChange(this::markChanged);
        csv.setOnColumnsChange(this::markChanged);
        excel.setOnColumnsChange(this::markChanged);
        csv.setOnFormatChange(() -> onDataFormatChange(csv.getDetectColumnsTaskMonitor()));
        excel.setOnFormatChange(() -> onDataFormatChange(excel.getDetectColumnsTaskMonitor()));
        ldap.setOnDetectAttributes(() -> detectColumns(ldap.getDetectAttributesTaskMonitor()));
        csv.setOnDetectColumns(() -> detectColumns(csv.getDetectColumnsTaskMonitor()));
        excel.setOnDetectColumns(() -> detectColumns(excel.getDetectColumnsTaskMonitor()));
        ldap.setOnAddAttribute(this::onAddAttribute);
        excel.setOnDownload(this::onDownloadExcel);
        excel.setOnRemove(this::onRemoveExcel);
        view.setOnTestConnection(this::onTestConnection);

        watch(Contexts.READ_ONLY, readOnly -> {
            view.setReadOnly(readOnly);
            csvEditor.setReadOnly(readOnly);
            // Setting read only makes Format available again, which CSV has no use for
            csvEditor.getFormatAction().setUnavailable();
        });
    }

    private CredentialListModel credentialModel(final EventBus eventBus, final CredentialType credentialType) {
        final CredentialListModel model = new CredentialListModel(eventBus, credentialClient, Set.of(credentialType));
        model.setTaskMonitorFactory(taskMonitor());
        return model;
    }

    private void onCredentialPicked(final CredentialChoice choice,
                                    final Credential credential,
                                    final Consumer<String> noteConsumer) {
        choice.pick(credential);
        noteConsumer.accept(null);
        markChanged();
    }

    /// Shows a Data Connection's settings.
    ///
    /// @param docRef The Data Connection.
    /// @param doc    The Data Connection.
    public void read(final DocRef docRef, final DataConnectionDoc doc) {
        readDoc = doc;
        view.setType(DataConnectionType.orDefault(doc.getSourceType()));
        readJdbc(doc);
        readAthena(doc);
        readLdap(doc);
        readCsv(doc);
        readExcel(doc);
        detection++;

        view.setTimeField(doc.getTimeField());
        view.setQueryTimeoutSeconds(Objects.requireNonNullElse(
                doc.getQueryTimeoutSeconds(),
                DataConnectionDoc.DEFAULT_QUERY_TIMEOUT_SECONDS));
        view.setMaxRows(Objects.requireNonNullElse(doc.getMaxRows(), DataConnectionDoc.DEFAULT_MAX_ROWS));
    }

    private void readJdbc(final DataConnectionDoc doc) {
        final JdbcSection section = view.getJdbc();
        final JdbcConnectionSettings jdbc = Objects.requireNonNullElse(
                doc.getJdbc(),
                JdbcConnectionSettings.builder().build());
        section.setUrl(jdbc.getUrl());
        section.setConnectionProperties(ConnectionPropertiesText.format(jdbc.getConnectionProperties()));
        section.setMaxPoolSize(Objects.requireNonNullElse(
                jdbc.getMaxPoolSize(),
                JdbcConnectionSettings.DEFAULT_MAX_POOL_SIZE));
        section.setFetchSize(Objects.requireNonNullElse(
                jdbc.getFetchSize(),
                JdbcConnectionSettings.DEFAULT_FETCH_SIZE));
        section.setMinRefreshMinutes(Objects.requireNonNullElse(jdbc.getMinRefreshMinutes(), 0));
        showCredential(jdbc.getCredentialName(), jdbcCredential, section::setCredential, section::setCredentialNote);
    }

    private void readAthena(final DataConnectionDoc doc) {
        final AthenaSection section = view.getAthena();
        final AthenaConnectionSettings athena = Objects.requireNonNullElse(
                doc.getAthena(),
                AthenaConnectionSettings.builder().build());
        section.setRegion(athena.getRegion());
        section.setWorkGroup(Objects.requireNonNullElse(
                athena.getWorkGroup(),
                AthenaConnectionSettings.DEFAULT_WORK_GROUP));
        section.setCatalog(Objects.requireNonNullElse(athena.getCatalog(), AthenaConnectionSettings.DEFAULT_CATALOG));
        section.setDatabase(Objects.requireNonNullElse(
                athena.getDatabase(),
                AthenaConnectionSettings.DEFAULT_DATABASE));
        section.setOutputLocation(athena.getOutputLocation());
        section.setResultReuseMinutes(Objects.requireNonNullElse(athena.getResultReuseMinutes(), 0));
        section.setMinRefreshMinutes(Objects.requireNonNullElse(
                athena.getMinRefreshMinutes(),
                AthenaConnectionSettings.DEFAULT_MIN_REFRESH_MINUTES));
        section.setMaxDataScannedMb(Objects.requireNonNullElse(athena.getMaxDataScannedMb(), 0));
        showCredential(athena.getCredentialName(),
                athenaCredential,
                section::setCredential,
                section::setCredentialNote);
    }

    private void readLdap(final DataConnectionDoc doc) {
        final LdapSection section = view.getLdap();
        final LdapSettings ldap = Objects.requireNonNullElse(doc.getLdap(), LdapSettings.builder().build());
        section.setUrl(ldap.getUrl());
        section.setStartTls(Boolean.TRUE.equals(ldap.getStartTls()));
        section.setBaseDn(ldap.getBaseDn());
        section.setScope(Objects.requireNonNullElse(ldap.getScope(), LdapSearchScope.SUBTREE));
        section.setPageSize(Objects.requireNonNullElse(ldap.getPageSize(), LdapSettings.DEFAULT_PAGE_SIZE));
        section.setMinRefreshMinutes(Objects.requireNonNullElse(ldap.getMinRefreshMinutes(), 0));
        section.setAttributes(NullSafe.list(ldap.getAttributes()));
        showCredential(ldap.getCredentialName(), ldapCredential, section::setCredential, section::setCredentialNote);
    }

    private void readCsv(final DataConnectionDoc doc) {
        final CsvSection section = view.getCsv();
        final CsvSettings csv = Objects.requireNonNullElse(doc.getCsv(), CsvSettings.builder().build());
        section.setDelimiter(csv.getDelimiter());
        section.setHeader(!Boolean.FALSE.equals(csv.getHeader()));
        section.setColumns(NullSafe.list(csv.getColumns()));
        csvEditor.setText(NullSafe.string(csv.getData()));
    }

    private void readExcel(final DataConnectionDoc doc) {
        final ExcelSection section = view.getExcel();
        final ExcelSettings excel = Objects.requireNonNullElse(doc.getExcel(), ExcelSettings.builder().build());
        excelUploadKey = null;
        excelUploadName = null;
        excelRemoved = false;
        excelFile.reset();
        section.setWorkbook(excel.getFileName(), excel.getFileName() != null);
        section.setSheet(excel.getSheet());
        section.setHeader(!Boolean.FALSE.equals(excel.getHeader()));
        section.setColumns(NullSafe.list(excel.getColumns()));
    }

    /// Shows the document's credential in its picker, once looked up. If it can't be shown, as it
    /// is gone or the user may not use it, a note says so and the document keeps it.
    private void showCredential(final String credentialName,
                                final CredentialChoice choice,
                                final Consumer<Credential> credentialConsumer,
                                final Consumer<String> noteConsumer) {
        choice.reset(credentialName);
        credentialConsumer.accept(null);
        noteConsumer.accept(null);
        if (!NullSafe.isBlankString(credentialName)) {
            restFactory
                    .create(CredentialClient.CREDENTIALS_RESOURCE)
                    .method(res -> res.getCredentialByName(credentialName))
                    .onSuccess(credential -> {
                        // The user may have picked another while the lookup ran, or the document
                        // may have been read again
                        if (choice.awaits(credentialName)) {
                            if (credential == null) {
                                noteConsumer.accept(choice.unresolvedNote());
                            } else {
                                credentialConsumer.accept(credential);
                            }
                        }
                    })
                    .onFailure(error -> {
                        if (choice.awaits(credentialName)) {
                            noteConsumer.accept(choice.unresolvedNote());
                        }
                    })
                    .owner(this)
                    .taskMonitorFactory(taskMonitor())
                    .exec();
        }
    }

    /// Writes the settings shown into a copy of a Data Connection.
    ///
    /// @param doc The Data Connection, e.g. a copy with a new UUID for Save As.
    /// @return A copy of it with the settings shown.
    public DataConnectionDoc write(final DataConnectionDoc doc) {
        // A value the document left unset is kept unset while its default is shown, so that a
        // document read and written back is equal to the one read, and isn't dirty
        final DataConnectionType type = view.getType();
        final int queryTimeoutSeconds = view.getQueryTimeoutSeconds();
        final int maxRows = view.getMaxRows();
        return doc.copy()
                .sourceType(doc.getSourceType() == null && type == DataConnectionType.JDBC
                        ? null
                        : type)
                .jdbc(writeJdbc(doc.getJdbc()))
                .athena(writeAthena(doc.getAthena()))
                .ldap(writeLdap(doc.getLdap()))
                .csv(writeCsv(doc.getCsv()))
                .excel(writeExcel(doc))
                .timeField(blankToNull(view.getTimeField()))
                .queryTimeoutSeconds(unsetIfDefault(doc.getQueryTimeoutSeconds(),
                        queryTimeoutSeconds,
                        DataConnectionDoc.DEFAULT_QUERY_TIMEOUT_SECONDS))
                .maxRows(unsetIfDefault(doc.getMaxRows(), maxRows, DataConnectionDoc.DEFAULT_MAX_ROWS))
                .build();
    }

    // The value shown, or null if the document had none and the default is shown
    private static Integer unsetIfDefault(final Integer stored, final int shown, final int defaultValue) {
        return stored == null && shown == defaultValue
                ? null
                : shown;
    }

    // The text shown, or null if the document had none and the default is shown
    private static String unsetIfDefault(final String stored, final String shown, final String defaultValue) {
        return stored == null && Objects.equals(shown, defaultValue)
                ? null
                : shown;
    }

    // No JDBC settings for a document that has never had any, unless something is set
    private JdbcConnectionSettings writeJdbc(final JdbcConnectionSettings stored) {
        final JdbcSection section = view.getJdbc();
        final JdbcConnectionSettings previous = Objects.requireNonNullElse(
                stored,
                JdbcConnectionSettings.builder().build());
        final Map<String, String> properties = ConnectionPropertiesText.parse(section.getConnectionProperties());
        final JdbcConnectionSettings written = JdbcConnectionSettings.builder()
                .url(blankToNull(section.getUrl()))
                .credentialName(jdbcCredential.getName())
                .connectionProperties(previous.getConnectionProperties() == null && properties.isEmpty()
                        ? null
                        : properties)
                .maxPoolSize(unsetIfDefault(previous.getMaxPoolSize(),
                        section.getMaxPoolSize(),
                        JdbcConnectionSettings.DEFAULT_MAX_POOL_SIZE))
                .fetchSize(unsetIfDefault(previous.getFetchSize(),
                        section.getFetchSize(),
                        JdbcConnectionSettings.DEFAULT_FETCH_SIZE))
                .minRefreshMinutes(unsetIfDefault(previous.getMinRefreshMinutes(), section.getMinRefreshMinutes(), 0))
                .build();
        return stored == null && written.equals(JdbcConnectionSettings.builder().build())
                ? null
                : written;
    }

    // No Athena settings for a document that has never had any, unless something is set
    private AthenaConnectionSettings writeAthena(final AthenaConnectionSettings stored) {
        final AthenaSection section = view.getAthena();
        final AthenaConnectionSettings previous = Objects.requireNonNullElse(
                stored,
                AthenaConnectionSettings.builder().build());
        final AthenaConnectionSettings written = AthenaConnectionSettings.builder()
                .region(blankToNull(section.getRegion()))
                .workGroup(unsetIfDefault(previous.getWorkGroup(),
                        blankToNull(section.getWorkGroup()),
                        AthenaConnectionSettings.DEFAULT_WORK_GROUP))
                .catalog(unsetIfDefault(previous.getCatalog(),
                        blankToNull(section.getCatalog()),
                        AthenaConnectionSettings.DEFAULT_CATALOG))
                .database(unsetIfDefault(previous.getDatabase(),
                        blankToNull(section.getDatabase()),
                        AthenaConnectionSettings.DEFAULT_DATABASE))
                .outputLocation(blankToNull(section.getOutputLocation()))
                .credentialName(athenaCredential.getName())
                .resultReuseMinutes(unsetIfDefault(previous.getResultReuseMinutes(),
                        section.getResultReuseMinutes(),
                        0))
                .minRefreshMinutes(unsetIfDefault(previous.getMinRefreshMinutes(),
                        section.getMinRefreshMinutes(),
                        AthenaConnectionSettings.DEFAULT_MIN_REFRESH_MINUTES))
                // No limit stays unset
                .maxDataScannedMb(unsetIfDefault(previous.getMaxDataScannedMb(), section.getMaxDataScannedMb(), 0))
                .build();
        return stored == null && written.equals(AthenaConnectionSettings.builder().build())
                ? null
                : written;
    }

    // No LDAP settings for a document that has never had any, as for CSV
    private LdapSettings writeLdap(final LdapSettings stored) {
        final LdapSection section = view.getLdap();
        final String url = blankToNull(section.getUrl());
        final String baseDn = blankToNull(section.getBaseDn());
        final List<DataColumn> attributes = section.getAttributes();
        if (stored == null
            && url == null
            && baseDn == null
            && ldapCredential.getName() == null
            && attributes.isEmpty()
            && view.getType() != DataConnectionType.LDAP) {
            return null;
        }
        final LdapSettings previous = Objects.requireNonNullElse(stored, LdapSettings.builder().build());
        final boolean startTls = section.isStartTls();
        final LdapSearchScope scope = section.getScope();
        final int pageSize = section.getPageSize();
        final int minRefreshMinutes = section.getMinRefreshMinutes();
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
                .attributes(DataColumns.same(previous.getAttributes(), attributes)
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

    // No CSV settings for a document that has never had any, so its JSON stays as it was
    private CsvSettings writeCsv(final CsvSettings stored) {
        final CsvSection section = view.getCsv();
        final String data = csvEditor.getText();
        final List<DataColumn> columns = section.getColumns();
        if (stored == null
            && NullSafe.isEmptyString(data)
            && columns.isEmpty()
            && view.getType() != DataConnectionType.CSV) {
            return null;
        }
        final CsvSettings previous = Objects.requireNonNullElse(stored, CsvSettings.builder().build());
        final String delimiter = section.getDelimiter();
        final boolean header = section.isHeader();
        return CsvSettings.builder()
                .delimiter(previous.getDelimiter() == null && CsvSettings.DEFAULT_DELIMITER.equals(delimiter)
                        ? null
                        : delimiter)
                .header(previous.getHeader() == null && header
                        ? null
                        : header)
                .columns(DataColumns.same(previous.getColumns(), columns)
                        ? previous.getColumns()
                        : columns)
                .data(NullSafe.isEmptyString(data)
                        ? null
                        : data)
                .build();
    }

    // No Excel settings for a document that has never had any, as for CSV. Saving a copy of the
    // document, which has a new UUID, names the document read so the server copies its workbook.
    private ExcelSettings writeExcel(final DataConnectionDoc doc) {
        final ExcelSection section = view.getExcel();
        final ExcelSettings stored = doc.getExcel();
        final ExcelSettings read = NullSafe.get(readDoc, DataConnectionDoc::getExcel);
        final List<DataColumn> columns = section.getColumns();
        final String sheet = blankToNull(section.getSheet());
        if (stored == null
            && read == null
            && excelUploadKey == null
            && sheet == null
            && columns.isEmpty()
            && view.getType() != DataConnectionType.EXCEL) {
            return null;
        }
        // Neither may be set (requireNonNullElse(stored, read) threw when both were null, e.g. on
        // choosing Excel for a document that has never had a workbook)
        final ExcelSettings previous;
        if (stored != null) {
            previous = stored;
        } else if (read != null) {
            previous = read;
        } else {
            previous = ExcelSettings.builder().build();
        }
        final boolean header = section.isHeader();
        final boolean savingACopy = readDoc != null && !Objects.equals(doc.getUuid(), readDoc.getUuid());
        return ExcelSettings.builder()
                // The server keeps the stored workbook while a file name is sent, and removes it when
                // none is
                .fileName(workbookName())
                .sheet(sheet)
                .header(previous.getHeader() == null && header
                        ? null
                        : header)
                .columns(DataColumns.same(previous.getColumns(), columns)
                        ? previous.getColumns()
                        : columns)
                .uploadKey(excelUploadKey)
                // The server copies nothing if the original holds no workbook
                .copyWorkbookFrom(savingACopy && excelUploadKey == null && !excelRemoved && read != null
                        ? readDoc.getUuid()
                        : null)
                .build();
    }

    private static String blankToNull(final String value) {
        return NullSafe.isBlankString(value)
                ? null
                : value.trim();
    }

    private void onTestConnection() {
        // Send the document as edited, with its real identity, as the server checks the user can
        // edit it before testing it with the credential it names
        final DataConnectionDoc doc = write(readDoc);
        restFactory
                .create(DATA_CONNECTION_RESOURCE)
                .method(res -> res.testConnection(doc))
                .onSuccess(result -> {
                    if (result.isOk()) {
                        AlertEvent.fireInfo(this, "Connection Success", result.getMessage(), null);
                    } else {
                        AlertEvent.fireError(this, "Connection Failure", result.getMessage(), null);
                    }
                })
                .owner(this)
                .taskMonitorFactory(view.getTestConnectionTaskMonitor())
                .exec();
    }

    private void detectColumns(final TaskMonitorFactory taskMonitorFactory) {
        // Send the data as edited, with the document's identity, as the server checks the user can
        // edit it
        final DataConnectionDoc doc = write(readDoc);
        final DataConnectionType type = doc.getSourceType();
        final int thisDetection = ++detection;
        restFactory
                .create(DATA_CONNECTION_RESOURCE)
                .method(res -> res.detectColumns(doc))
                .onSuccess(columns -> {
                    if (thisDetection == detection) {
                        if (type == DataConnectionType.LDAP) {
                            view.getLdap().setAttributes(columns);
                        } else if (type == DataConnectionType.EXCEL) {
                            view.getExcel().setColumns(columns);
                        } else {
                            view.getCsv().setColumns(columns);
                        }
                        markChanged();
                    }
                })
                .onFailure(error -> {
                    if (thisDetection == detection) {
                        AlertEvent.fireError(this,
                                type == DataConnectionType.LDAP
                                        ? "Unable to detect the attributes"
                                        : "Unable to detect the columns",
                                error.getMessage(),
                                null);
                    }
                })
                .owner(this)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    // The delimiter, header row or sheet changes the columns, so they are detected again if there
    // is data to detect them from
    private void onDataFormatChange(final TaskMonitorFactory taskMonitorFactory) {
        markChanged();
        if (hasData()) {
            detectColumns(taskMonitorFactory);
        }
    }

    private boolean hasData() {
        if (view.getType() == DataConnectionType.EXCEL) {
            return workbookName() != null;
        }
        return !NullSafe.isBlankString(csvEditor.getText());
    }

    // The file name of the workbook the document has as edited: a new upload's, none once removed, or
    // else the one read
    private String workbookName() {
        if (excelUploadKey != null) {
            return excelUploadName;
        }
        return excelRemoved
                ? null
                : NullSafe.get(readDoc, DataConnectionDoc::getExcel, ExcelSettings::getFileName);
    }

    private void onAddAttribute() {
        final List<String> existing = DataColumns.names(view.getLdap().getAttributes());
        // Owned by this until it is closed
        nameDialogProvider.get().show(this,
                "Add Attribute",
                null,
                name -> DataColumns.validateNewName(name, existing),
                name -> view.getLdap().addAttribute(name));
    }

    private void onCsvFileChosen() {
        csvFile.readText(MAX_CSV_FILE_BYTES,
                text -> {
                    csvEditor.setText(text);
                    markChanged();
                    detectColumns(view.getCsv().getDetectColumnsTaskMonitor());
                },
                message -> AlertEvent.fireError(this, "Unable to read the file", message, null));
    }

    // The file name is taken now, as the user may choose another before the upload finishes
    private void uploadExcel() {
        excelChosenName = excelFile.getFilename();
        excelFile.upload(ImportUtil.getImportFileURL(),
                this::onExcelUploaded,
                message -> AlertEvent.fireError(this, "Unable to upload the workbook", message, null));
    }

    // The workbook is held by the server under the key until the document is saved
    private void onExcelUploaded(final ResourceKey resourceKey) {
        excelUploadKey = resourceKey;
        excelRemoved = false;
        excelUploadName = excelChosenName;
        view.getExcel().setWorkbook(excelUploadName + " (uploaded; stored when you save)", false);
        markChanged();
        // The user may have chosen another type while the workbook was uploading
        if (view.getType() == DataConnectionType.EXCEL) {
            detectColumns(view.getExcel().getDetectColumnsTaskMonitor());
        }
    }

    private void onRemoveExcel() {
        excelUploadKey = null;
        excelUploadName = null;
        excelRemoved = true;
        excelFile.reset();
        view.getExcel().setWorkbook(null, false);
        markChanged();
    }

    private void onDownloadExcel() {
        final String uuid = readDoc.getUuid();
        restFactory
                .create(DATA_CONNECTION_RESOURCE)
                .method(res -> res.downloadExcelFile(uuid))
                .onSuccess(result -> ExportFileCompleteUtil.onSuccess(locationManager, this, result))
                .onFailure(error -> AlertEvent.fireError(this,
                        "Unable to download the workbook",
                        error.getMessage(),
                        null))
                .owner(this)
                .taskMonitorFactory(view.getExcel().getDownloadTaskMonitor())
                .exec();
    }
}
