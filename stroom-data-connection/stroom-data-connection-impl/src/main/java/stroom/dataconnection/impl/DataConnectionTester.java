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

package stroom.dataconnection.impl;

import stroom.dataconnection.shared.CsvSettings;
import stroom.dataconnection.shared.DataColumn;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.dataconnection.shared.DataConnectionTestResponse;
import stroom.dataconnection.shared.DataConnectionType;
import stroom.dataconnection.shared.ExcelSettings;
import stroom.docref.DocRef;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.io.ByteSize;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.List;

/// Tests and inspects the settings of an [DataConnectionDoc] being edited, which need not have
/// been saved: its connection, and the columns of its CSV or workbook, or the attributes of its LDAP
/// directory.
///
/// The editor never has an Excel document's workbook, so it is filled in here: from the upload the
/// editor names, or else from the stored document.
@Singleton
public class DataConnectionTester {

    private final Provider<SecurityContext> securityContextProvider;
    private final Connectors connectors;
    private final Provider<DataConnectionConfig> configProvider;
    private final ExcelUploads excelUploads;
    private final Provider<DataConnectionStore> storeProvider;
    private final LdapConnector ldapConnector;

    /// Creates the instance. Guice supplies every argument.
    @Inject
    DataConnectionTester(final Provider<SecurityContext> securityContextProvider,
                                       final Connectors connectors,
                                       final Provider<DataConnectionConfig> configProvider,
                                       final ExcelUploads excelUploads,
                                       final Provider<DataConnectionStore> storeProvider,
                                       final LdapConnector ldapConnector) {
        this.securityContextProvider = securityContextProvider;
        this.connectors = connectors;
        this.configProvider = configProvider;
        this.excelUploads = excelUploads;
        this.storeProvider = storeProvider;
        this.ldapConnector = ldapConnector;
    }

    /// Works out the columns of the document's CSV or workbook, with [ColumnDetector], from their first
    /// [ColumnDetector#SAMPLE_ROWS] rows; or for an LDAP directory, the attributes of its first
    /// [LdapConnector#DETECT_SAMPLE_ENTRIES] entries, with the document's own credential.
    ///
    /// @param doc The document, with its CSV settings and data, its Excel settings or its LDAP settings.
    /// @return The columns, in the order their values appear in each row, or the attributes in the order
    ///         they were first seen.
    /// @throws EntityServiceException   If no document is supplied, it has no data, or the data is over
    ///                                  the stored data limit.
    /// @throws PermissionException      If the current user may not edit the document.
    /// @throws IllegalArgumentException If the data cannot be read.
    public List<DataColumn> detectColumns(final DataConnectionDoc doc) {
        checkEditPermission(doc);
        if (DataConnectionType.LDAP.equals(doc.getSourceType())) {
            return ldapConnector.detectAttributes(doc);
        }
        if (DataConnectionType.EXCEL.equals(doc.getSourceType())) {
            final ExcelSettings excel = withWorkbook(doc).getExcel();
            if (excel == null || excel.getData() == null) {
                throw new EntityServiceException("There is no workbook to detect the columns of. Upload one.");
            }
            return ColumnDetector.detect(
                    ExcelReader.read(excel.getData(), excel, ColumnDetector.SAMPLE_ROWS),
                    excel.getColumns());
        }
        final CsvSettings csv = doc.getCsv();
        if (csv == null || NullSafe.isBlankString(csv.getData())) {
            throw new EntityServiceException("There is no CSV to detect the columns of.");
        }
        final ByteSize limit = configProvider.get().getMaxStoredDataSize();
        if (CsvReader.utf8Length(csv.getData()) > limit.getBytes()) {
            throw new EntityServiceException("The CSV is more than the limit of " + limit
                                             + " set by the property stroom.dataConnection.maxStoredDataSize.");
        }
        return ColumnDetector.detect(CsvReader.parse(csv, ColumnDetector.SAMPLE_ROWS), csv.getColumns());
    }

    /// Tests the document's connection with its type's [Connector], and reports what it connected to.
    ///
    /// The test uses the settings in the request, which may not be saved, with the credential they
    /// name. So only a user who could save those settings may run it; otherwise a user could send a
    /// credential they can use to a server of their choosing.
    ///
    /// @param doc The document whose connection is tested.
    /// @return Whether the connection worked, with what it connected to or the error.
    /// @throws EntityServiceException If no document is supplied.
    /// @throws PermissionException    If the current user may not edit the document.
    public DataConnectionTestResponse test(final DataConnectionDoc doc) {
        checkEditPermission(doc);
        final DataConnectionDoc toTest = DataConnectionType.EXCEL.equals(doc.getSourceType())
                ? withWorkbook(doc)
                : doc;
        return connectors.get(toTest).test(toTest);
    }

    // The workbook from the upload the editor names, or else the one stored with the document. The user
    // may edit the document, so may read it.
    private DataConnectionDoc withWorkbook(final DataConnectionDoc doc) {
        final ExcelSettings excel = doc.getExcel();
        if (excel == null) {
            return doc;
        }
        final byte[] data;
        if (excel.getUploadKey() != null) {
            data = excelUploads.read(excel.getUploadKey());
        } else if (NullSafe.isBlankString(excel.getFileName())) {
            // Removed in the editor, or never uploaded.
            data = null;
        } else {
            final DataConnectionDoc stored = storeProvider.get().readDocument(doc.asDocRef());
            data = NullSafe.get(stored, DataConnectionDoc::getExcel, ExcelSettings::getData);
            // An imported workbook is not checked on save.
            final ByteSize limit = configProvider.get().getMaxStoredDataSize();
            if (data != null && data.length > limit.getBytes()) {
                throw new EntityServiceException("The workbook is more than the limit of " + limit
                                                 + " set by the property "
                                                 + "stroom.dataConnection.maxStoredDataSize.");
            }
        }
        return doc.copy()
                .excel(excel.copy()
                        .data(data)
                        .build())
                .build();
    }

    private void checkEditPermission(final DataConnectionDoc doc) {
        if (doc == null || doc.getUuid() == null) {
            throw new EntityServiceException("No Data Connection supplied");
        }
        final SecurityContext securityContext = securityContextProvider.get();
        final DocRef docRef = DataConnectionDoc.buildDocRef()
                .uuid(doc.getUuid())
                .build();
        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to test this Data Connection");
        }
    }
}
