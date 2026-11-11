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

package stroom.externaldatasource.impl;

import stroom.docref.DocRef;
import stroom.externaldatasource.shared.CsvSettings;
import stroom.externaldatasource.shared.DataColumn;
import stroom.externaldatasource.shared.ExcelSettings;
import stroom.externaldatasource.shared.ExternalDataSourceDoc;
import stroom.externaldatasource.shared.ExternalDataSourceTestResponse;
import stroom.externaldatasource.shared.ExternalDataSourceType;
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

/// Tests and inspects the settings of an [ExternalDataSourceDoc] being edited, which need not have
/// been saved: its connection, and the columns of its CSV or workbook.
///
/// The editor never has an Excel document's workbook, so it is filled in here: from the upload the
/// editor names, or else from the stored document.
@Singleton
public class ExternalDataSourceConnectionTester {

    private final Provider<SecurityContext> securityContextProvider;
    private final Connectors connectors;
    private final Provider<ExternalDataSourceConfig> configProvider;
    private final ExcelUploads excelUploads;
    private final Provider<ExternalDataSourceStore> storeProvider;

    /// Creates the instance. Guice supplies every argument.
    @Inject
    ExternalDataSourceConnectionTester(final Provider<SecurityContext> securityContextProvider,
                                       final Connectors connectors,
                                       final Provider<ExternalDataSourceConfig> configProvider,
                                       final ExcelUploads excelUploads,
                                       final Provider<ExternalDataSourceStore> storeProvider) {
        this.securityContextProvider = securityContextProvider;
        this.connectors = connectors;
        this.configProvider = configProvider;
        this.excelUploads = excelUploads;
        this.storeProvider = storeProvider;
    }

    /// Works out the columns of the document's CSV or workbook, with [ColumnDetector], from their first
    /// [ColumnDetector#SAMPLE_ROWS] rows.
    ///
    /// @param doc The document, with its CSV settings and data, or its Excel settings.
    /// @return The columns, in the order their values appear in each row.
    /// @throws EntityServiceException   If no document is supplied, it has no data, or the data is over
    ///                                  the stored data limit.
    /// @throws PermissionException      If the current user may not edit the document.
    /// @throws IllegalArgumentException If the data cannot be read.
    public List<DataColumn> detectColumns(final ExternalDataSourceDoc doc) {
        checkEditPermission(doc);
        if (ExternalDataSourceType.EXCEL.equals(doc.getSourceType())) {
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
                                             + " set by the property stroom.externalDataSource.maxStoredDataSize.");
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
    public ExternalDataSourceTestResponse test(final ExternalDataSourceDoc doc) {
        checkEditPermission(doc);
        final ExternalDataSourceDoc toTest = ExternalDataSourceType.EXCEL.equals(doc.getSourceType())
                ? withWorkbook(doc)
                : doc;
        return connectors.get(toTest).test(toTest);
    }

    // The workbook from the upload the editor names, or else the one stored with the document. The user
    // may edit the document, so may read it.
    private ExternalDataSourceDoc withWorkbook(final ExternalDataSourceDoc doc) {
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
            final ExternalDataSourceDoc stored = storeProvider.get().readDocument(doc.asDocRef());
            data = NullSafe.get(stored, ExternalDataSourceDoc::getExcel, ExcelSettings::getData);
            // An imported workbook is not checked on save.
            final ByteSize limit = configProvider.get().getMaxStoredDataSize();
            if (data != null && data.length > limit.getBytes()) {
                throw new EntityServiceException("The workbook is more than the limit of " + limit
                                                 + " set by the property "
                                                 + "stroom.externalDataSource.maxStoredDataSize.");
            }
        }
        return doc.copy()
                .excel(excel.copy()
                        .data(data)
                        .build())
                .build();
    }

    private void checkEditPermission(final ExternalDataSourceDoc doc) {
        if (doc == null || doc.getUuid() == null) {
            throw new EntityServiceException("No external data source supplied");
        }
        final SecurityContext securityContext = securityContextProvider.get();
        final DocRef docRef = ExternalDataSourceDoc.buildDocRef()
                .uuid(doc.getUuid())
                .build();
        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to test this external data source");
        }
    }
}
