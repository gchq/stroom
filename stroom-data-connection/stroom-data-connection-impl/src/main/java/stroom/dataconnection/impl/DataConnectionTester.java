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
    private final Provider<DataConnectionStore> storeProvider;
    private final LdapConnector ldapConnector;

    /// Creates the instance. Guice supplies every argument.
    @Inject
    DataConnectionTester(final Provider<SecurityContext> securityContextProvider,
                                       final Connectors connectors,
                                       final Provider<DataConnectionConfig> configProvider,
                                       final Provider<DataConnectionStore> storeProvider,
                                       final LdapConnector ldapConnector) {
        this.securityContextProvider = securityContextProvider;
        this.connectors = connectors;
        this.configProvider = configProvider;
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
            // An imported workbook is not checked on save.
            StoredDataLimit.findProblem("workbook", excel.getData().length, configProvider.get())
                    .ifPresent(problem -> {
                        throw new EntityServiceException(problem);
                    });
            return ColumnDetector.detect(
                    ExcelReader.read(excel.getData(), excel, ColumnDetector.SAMPLE_ROWS),
                    excel.getColumns());
        }
        final CsvSettings csv = doc.getCsv();
        if (csv == null || NullSafe.isBlankString(csv.getData())) {
            throw new EntityServiceException("There is no CSV to detect the columns of.");
        }
        StoredDataLimit.findProblem("CSV", CsvReader.utf8Length(csv.getData()), configProvider.get())
                .ifPresent(problem -> {
                    throw new EntityServiceException(problem);
                });
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

    // The workbook the document will have once saved: a new upload, the one it is copied from, or else
    // the one stored. The editor never sends a workbook, so any sent is ignored: a test of one the user
    // sent would say nothing of the one queried.
    private DataConnectionDoc withWorkbook(final DataConnectionDoc doc) {
        final ExcelSettings excel = doc.getExcel();
        final DataConnectionDoc withoutSentData = excel == null || excel.getData() == null
                ? doc
                : doc.copy()
                        .excel(excel.copy()
                                .data(null)
                                .build())
                        .build();
        return storeProvider.get().withWorkbook(withoutSentData);
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
