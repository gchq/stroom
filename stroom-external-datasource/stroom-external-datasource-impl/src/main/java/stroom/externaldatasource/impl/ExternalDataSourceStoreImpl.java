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
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.StoreFactory;
import stroom.externaldatasource.shared.AthenaConnectionSettings;
import stroom.externaldatasource.shared.CsvSettings;
import stroom.externaldatasource.shared.ExcelSettings;
import stroom.externaldatasource.shared.ExternalDataSourceDoc;
import stroom.externaldatasource.shared.ExternalDataSourceType;
import stroom.externaldatasource.shared.JdbcConnectionSettings;
import stroom.externaldatasource.shared.LdapSettings;
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

/// Stores [ExternalDataSourceDoc] documents.
///
/// A JDBC or LDAP URL, or an Amazon Athena region and workgroup, is checked against the administrator's
/// controls when a user saves a document, so a mistake is reported at once. Imports are not checked,
/// so a content pack can be imported and then edited; its settings are checked again whenever it
/// connects.
@Singleton
public class ExternalDataSourceStoreImpl
        extends AbstractDocumentStore<ExternalDataSourceDoc>
        implements ExternalDataSourceStore {

    /// The file name given to a stored workbook that has none, e.g. from an import.
    static final String DEFAULT_WORKBOOK_NAME = "workbook.xlsx";

    private final Provider<ExternalDataSourceConfig> configProvider;
    private final ExcelUploads excelUploads;

    /// Creates the instance. Guice supplies every argument.
    @Inject
    ExternalDataSourceStoreImpl(final StoreFactory storeFactory,
                                final SecurityContext securityContext,
                                final ExternalDataSourceSerialiser serialiser,
                                final Provider<ExternalDataSourceConfig> configProvider,
                                final ExcelUploads excelUploads) {
        super(storeFactory,
                securityContext,
                serialiser,
                ExternalDataSourceDoc.TYPE,
                ExternalDataSourceDoc::builder,
                ExternalDataSourceDoc::copy);
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
    public ExternalDataSourceDoc writeDocument(final ExternalDataSourceDoc document) {
        final ExternalDataSourceDoc withWorkbook = withWorkbook(document);
        final List<String> problems = findProblems(withWorkbook);
        if (!problems.isEmpty()) {
            throw new EntityServiceException(String.join("\n", problems));
        }
        final ExternalDataSourceDoc saved = super.writeDocument(withWorkbook);
        final ResourceKey uploadKey = NullSafe.get(document.getExcel(), ExcelSettings::getUploadKey);
        if (uploadKey != null) {
            excelUploads.delete(uploadKey);
        }
        return saved;
    }

    // The workbook from a new upload, the document being copied, or else the one already stored, with
    // the upload key and the document to copy from cleared so they are never stored. A stored workbook
    // always has a file name, so settings with none and no new workbook remove it.
    private ExternalDataSourceDoc withWorkbook(final ExternalDataSourceDoc document) {
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
            data = storedWorkbook(ExternalDataSourceDoc.buildDocRef()
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
        final ExternalDataSourceDoc stored = super.readDocument(docRef);
        if (stored == null) {
            throw new EntityServiceException("The External Data Source to take the workbook from was not found.");
        }
        return NullSafe.get(stored, ExternalDataSourceDoc::getExcel, ExcelSettings::getData);
    }

    private List<String> findProblems(final ExternalDataSourceDoc document) {
        final List<String> problems = new ArrayList<>(findStoredDataProblems(document));
        problems.addAll(findConnectionProblems(document));
        return problems;
    }

    // CSV is checked whatever the type, as it stays in the document after a switch to another type and
    // the editor can always clear it. A workbook is checked while the type is Excel; a new upload is
    // checked when read. So a workbook over the limit, say from an import, never stops another type
    // being saved, and can be removed in the editor.
    private List<String> findStoredDataProblems(final ExternalDataSourceDoc document) {
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
        if (workbook != null && ExternalDataSourceType.EXCEL.equals(document.getSourceType())) {
            checkSize("workbook", workbook.length, problems);
        }
        return problems;
    }

    private void checkSize(final String dataName, final long size, final List<String> problems) {
        final ByteSize limit = configProvider.get().getMaxStoredDataSize();
        if (size > limit.getBytes()) {
            problems.add("The " + dataName + " is " + ModelStringUtil.formatIECByteSizeString(size)
                         + ", more than the limit of " + limit + " set by the property "
                         + "stroom.externalDataSource.maxStoredDataSize. External Data Sources are for lookup "
                         + "tables and reference lists; send bulk data into Stroom as a feed.");
        }
    }

    private List<String> findConnectionProblems(final ExternalDataSourceDoc document) {
        final ExternalDataSourceType sourceType = Objects.requireNonNullElse(
                document.getSourceType(),
                ExternalDataSourceType.JDBC);
        if (sourceType == ExternalDataSourceType.CSV || sourceType == ExternalDataSourceType.EXCEL) {
            return List.of();
        }
        if (sourceType == ExternalDataSourceType.LDAP) {
            final LdapSettings ldap = document.getLdap();
            return NullSafe.isNonBlankString(NullSafe.get(ldap, LdapSettings::getUrl))
                    ? LdapConnectionValidator.findProblems(ldap, configProvider.get())
                    : List.of();
        }
        if (sourceType == ExternalDataSourceType.ATHENA) {
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
