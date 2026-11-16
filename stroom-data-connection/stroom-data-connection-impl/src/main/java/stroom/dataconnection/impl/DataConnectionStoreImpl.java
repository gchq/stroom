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

import stroom.dataconnection.shared.AthenaConnectionSettings;
import stroom.dataconnection.shared.CsvSettings;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.dataconnection.shared.DataConnectionType;
import stroom.dataconnection.shared.ExcelSettings;
import stroom.dataconnection.shared.JdbcConnectionSettings;
import stroom.dataconnection.shared.LdapSettings;
import stroom.docref.DocRef;
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.StoreFactory;
import stroom.security.api.SecurityContext;
import stroom.util.io.ByteSize;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourceKey;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Stores [DataConnectionDoc] documents.
///
/// A JDBC or LDAP URL, or an Amazon Athena region and workgroup, is checked against the administrator's
/// controls when a user saves a document, so a mistake is reported at once. Imports are not checked,
/// so a content pack can be imported and then edited; its settings are checked again whenever it
/// connects.
@Singleton
public class DataConnectionStoreImpl
        extends AbstractDocumentStore<DataConnectionDoc>
        implements DataConnectionStore {

    /// The file name given to a stored workbook that has none, e.g. from an import.
    static final String DEFAULT_WORKBOOK_NAME = "workbook.xlsx";

    private final Provider<DataConnectionConfig> configProvider;
    private final ExcelUploads excelUploads;

    /// Creates the instance. Guice supplies every argument.
    @Inject
    DataConnectionStoreImpl(final StoreFactory storeFactory,
                                final SecurityContext securityContext,
                                final DataConnectionSerialiser serialiser,
                                final Provider<DataConnectionConfig> configProvider,
                                final ExcelUploads excelUploads) {
        super(storeFactory,
                securityContext,
                serialiser,
                DataConnectionDoc.TYPE,
                DataConnectionDoc::builder,
                DataConnectionDoc::copy);
        this.configProvider = configProvider;
        this.excelUploads = excelUploads;
    }

    /// Saves a document, refusing settings the administrator has not allowed: a JDBC URL or driver
    /// property, an Athena region and workgroup, an LDAP URL, plain LDAP, or CSV or a workbook larger
    /// than the stored data limit.
    ///
    /// A document with no URL or region yet may be saved, so it can be built up over several saves.
    /// Only the connection settings of the document's type are checked, but held data is checked
    /// whatever the type, as it stays in the document.
    ///
    /// The editor never has an Excel document's workbook. If it sends the key of a new upload, that
    /// workbook is stored. If it is saving a copy, the workbook is copied from the document it names,
    /// which the user must be able to read. Otherwise the workbook already stored is kept.
    ///
    /// @param document The document to save.
    /// @return The saved document.
    /// @throws EntityServiceException If the settings break the administrator's controls.
    @Override
    public DataConnectionDoc writeDocument(final DataConnectionDoc document) {
        final DataConnectionDoc withWorkbook = withWorkbook(document);
        final List<String> problems = findProblems(withWorkbook);
        if (!problems.isEmpty()) {
            throw new EntityServiceException(String.join("\n", problems));
        }
        final DataConnectionDoc saved = super.writeDocument(withWorkbook);
        final ResourceKey uploadKey = NullSafe.get(document.getExcel(), ExcelSettings::getUploadKey);
        if (uploadKey != null) {
            excelUploads.delete(uploadKey);
        }
        return saved;
    }

    // The workbook from a new upload, the document being copied, or else the one already stored, with
    // the upload key and the document to copy from cleared so they are never stored. A stored workbook
    // always has a file name, so settings with none and no new workbook remove it.
    private DataConnectionDoc withWorkbook(final DataConnectionDoc document) {
        final ExcelSettings excel = document.getExcel();
        if (excel == null) {
            return document;
        }
        final byte[] data;
        if (excel.getUploadKey() != null) {
            data = excelUploads.read(excel.getUploadKey());
        } else if (excel.getData() != null) {
            data = excel.getData();
        } else if (excel.getCopyWorkbookFrom() != null) {
            data = storedWorkbook(DataConnectionDoc.buildDocRef()
                    .uuid(excel.getCopyWorkbookFrom())
                    .build());
        } else if (NullSafe.isBlankString(excel.getFileName()) || document.getUuid() == null) {
            data = null;
        } else {
            data = storedWorkbook(document.asDocRef());
        }
        return document.copy()
                .excel(excel.copy()
                        .fileName(data == null
                                ? null
                                : NullSafe.nonBlankStringElse(excel.getFileName(), DEFAULT_WORKBOOK_NAME))
                        .uploadKey(null)
                        .copyWorkbookFrom(null)
                        .data(data)
                        .build())
                .build();
    }

    // Reading the document checks the user may read it.
    private byte[] storedWorkbook(final DocRef docRef) {
        final DataConnectionDoc stored = super.readDocument(docRef);
        if (stored == null) {
            throw new EntityServiceException("The Data Connection to take the workbook from was not found.");
        }
        return NullSafe.get(stored, DataConnectionDoc::getExcel, ExcelSettings::getData);
    }

    private List<String> findProblems(final DataConnectionDoc document) {
        final List<String> problems = new ArrayList<>(findStoredDataProblems(document));
        problems.addAll(findConnectionProblems(document));
        return problems;
    }

    // CSV is checked whatever the type, as it stays in the document after a switch to another type and
    // the editor can always clear it. A workbook is checked while the type is Excel; a new upload is
    // checked when read. So a workbook over the limit, say from an import, never stops another type
    // being saved, and can be removed in the editor.
    private List<String> findStoredDataProblems(final DataConnectionDoc document) {
        final List<String> problems = new ArrayList<>();
        final CsvSettings csv = document.getCsv();
        if (csv != null) {
            checkSize("CSV", CsvReader.utf8Length(csv.getData()), problems);
            try {
                CsvReader.format(csv);
            } catch (final IllegalArgumentException e) {
                problems.add(e.getMessage());
            }
        }
        final byte[] workbook = NullSafe.get(document.getExcel(), ExcelSettings::getData);
        if (workbook != null && DataConnectionType.EXCEL.equals(document.getSourceType())) {
            checkSize("workbook", workbook.length, problems);
        }
        return problems;
    }

    private void checkSize(final String dataName, final long size, final List<String> problems) {
        final ByteSize limit = configProvider.get().getMaxStoredDataSize();
        if (size > limit.getBytes()) {
            problems.add("The " + dataName + " is " + ModelStringUtil.formatIECByteSizeString(size)
                         + ", more than the limit of " + limit + " set by the property "
                         + "stroom.dataConnection.maxStoredDataSize. Data Connections are for lookup "
                         + "tables and reference lists; send bulk data into Stroom as a feed.");
        }
    }

    private List<String> findConnectionProblems(final DataConnectionDoc document) {
        final DataConnectionType sourceType = Objects.requireNonNullElse(
                document.getSourceType(),
                DataConnectionType.JDBC);
        if (sourceType == DataConnectionType.CSV || sourceType == DataConnectionType.EXCEL) {
            return List.of();
        }
        if (sourceType == DataConnectionType.LDAP) {
            final LdapSettings ldap = document.getLdap();
            return NullSafe.isNonBlankString(NullSafe.get(ldap, LdapSettings::getUrl))
                    ? LdapConnectionValidator.findProblems(ldap, configProvider.get())
                    : List.of();
        }
        if (sourceType == DataConnectionType.ATHENA) {
            final AthenaConnectionSettings athena = document.getAthena();
            return NullSafe.isNonBlankString(NullSafe.get(athena, AthenaConnectionSettings::getRegion))
                    ? AthenaConnectionValidator.findProblems(athena, configProvider.get())
                    : List.of();
        }
        final JdbcConnectionSettings jdbc = document.getJdbc();
        return NullSafe.isNonBlankString(NullSafe.get(jdbc, JdbcConnectionSettings::getUrl))
                ? JdbcConnectionValidator.findProblems(jdbc, configProvider.get())
                : List.of();
    }
}
