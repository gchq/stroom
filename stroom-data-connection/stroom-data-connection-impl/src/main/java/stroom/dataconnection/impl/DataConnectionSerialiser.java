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
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.dataconnection.shared.ExcelSettings;
import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.docstore.shared.DocDataType;
import stroom.importexport.api.ByteArrayImportExportAsset;
import stroom.importexport.api.ImportExportDocument;
import stroom.util.shared.NullSafe;
import stroom.util.string.EncodingUtil;

import jakarta.inject.Inject;

import java.io.IOException;

/// Reads and writes [DataConnectionDoc] documents for storage and import/export.
///
/// Data held in a document is kept apart from its JSON, as an XSLT document keeps its XSL: the CSV of a
/// CSV document in a part named `csv`, and the workbook of an Excel document in a part named `xlsx`.
/// So they are stored and exported as plain files.
public class DataConnectionSerialiser implements DocumentSerialiser2<DataConnectionDoc> {

    static final String CSV = "csv";
    static final String XLSX = "xlsx";

    private final Serialiser2<DataConnectionDoc> delegate;

    /// Creates the instance. Guice supplies every argument.
    @Inject
    DataConnectionSerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(DataConnectionDoc.class);
    }

    /// Reads a document, with its CSV from the `csv` part and its workbook from the `xlsx` part, if it
    /// has them.
    ///
    /// @param importExportDocument The stored or imported form of the document.
    /// @return The document.
    /// @throws IOException If the document cannot be read.
    @Override
    public DataConnectionDoc read(final ImportExportDocument importExportDocument) throws IOException {
        final DataConnectionDoc doc = delegate.read(importExportDocument);
        final byte[] csvBytes = importExportDocument.getExtAssetData(CSV);
        final byte[] xlsxBytes = importExportDocument.getExtAssetData(XLSX);
        if (csvBytes == null && xlsxBytes == null) {
            return doc;
        }
        final DataConnectionDoc.Builder builder = doc.copy();
        if (csvBytes != null) {
            final CsvSettings csv = doc.getCsv() == null
                    ? CsvSettings.builder().build()
                    : doc.getCsv();
            builder.csv(csv.copy()
                    .data(EncodingUtil.asString(csvBytes))
                    .build());
        }
        if (xlsxBytes != null) {
            final ExcelSettings excel = doc.getExcel() == null
                    ? ExcelSettings.builder().build()
                    : doc.getExcel();
            // A stored workbook always has a file name, which the editor shows and sends back to keep
            // it. A pack's JSON could hold an upload key or a document to copy from; neither applies.
            builder.excel(excel.copy()
                    .fileName(NullSafe.nonBlankStringElse(
                            excel.getFileName(),
                            DataConnectionStoreImpl.DEFAULT_WORKBOOK_NAME))
                    .uploadKey(null)
                    .copyWorkbookFrom(null)
                    .data(xlsxBytes)
                    .build());
        }
        return builder.build();
    }

    /// Writes a document, with its CSV and workbook, if it has them, in their own parts rather than in
    /// its JSON.
    ///
    /// @param document The document.
    /// @return The stored or exported form of the document.
    /// @throws IOException If the document cannot be written.
    @Override
    public ImportExportDocument write(final DataConnectionDoc document) throws IOException {
        final String csvData = document.getCsv() == null
                ? null
                : document.getCsv().getData();
        // The workbook is never in the JSON, so it needs no removing.
        final byte[] xlsxData = document.getExcel() == null
                ? null
                : document.getExcel().getData();
        final ImportExportDocument importExportDocument = delegate.write(csvData == null
                ? document
                : document.copy()
                        .csv(document.getCsv().copy()
                                .data(null)
                                .build())
                        .build());
        if (csvData != null) {
            importExportDocument.addExtAsset(
                    new ByteArrayImportExportAsset(CSV, DocDataType.TEXT, EncodingUtil.asBytes(csvData)));
        }
        if (xlsxData != null) {
            importExportDocument.addExtAsset(new ByteArrayImportExportAsset(XLSX, DocDataType.BINARY, xlsxData));
        }
        return importExportDocument;
    }
}
