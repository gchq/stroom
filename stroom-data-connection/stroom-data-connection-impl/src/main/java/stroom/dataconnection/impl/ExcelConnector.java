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

import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.dataconnection.shared.DataConnectionType;
import stroom.dataconnection.shared.ExcelSettings;
import stroom.dataconnection.shared.StoredDataSettings;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Arrays;

/// Queries a sheet of an Excel workbook held in the document itself. See [StoredDataConnector] and
/// [ExcelReader].
///
/// The document must hold the workbook: read from the store, or with it filled in from an upload, as
/// the editor does not send it.
@Singleton
class ExcelConnector extends StoredDataConnector {

    /// Creates the connector. Guice supplies every argument.
    @Inject
    ExcelConnector(final DataTableCache tableCache,
                   final Provider<DataConnectionConfig> configProvider) {
        super(tableCache, configProvider);
    }

    @Override
    public DataConnectionType getType() {
        return DataConnectionType.EXCEL;
    }

    @Override
    StoredDataSettings settings(final DataConnectionDoc doc) {
        return doc.getExcel();
    }

    @Override
    String dataName() {
        return "workbook";
    }

    @Override
    boolean hasData(final DataConnectionDoc doc) {
        return dataSize(doc) > 0;
    }

    @Override
    long dataSize(final DataConnectionDoc doc) {
        final byte[] data = doc.getExcel() == null
                ? null
                : doc.getExcel().getData();
        return data == null
                ? 0
                : data.length;
    }

    // The workbook's length and hash stand for it, so the key does not hold the workbook itself.
    @Override
    Object dataKey(final DataConnectionDoc doc) {
        final ExcelSettings excel = doc.getExcel();
        return new ExcelKey(excel.getSheet(), excel.getHeader(), excel.getData().length,
                Arrays.hashCode(excel.getData()));
    }

    @Override
    DataTable read(final DataConnectionDoc doc, final int maxRows) {
        return ExcelReader.read(doc.getExcel().getData(), doc.getExcel(), maxRows);
    }


    // --------------------------------------------------------------------------------


    private record ExcelKey(String sheet, Boolean header, int dataLength, int dataHash) {

    }
}
