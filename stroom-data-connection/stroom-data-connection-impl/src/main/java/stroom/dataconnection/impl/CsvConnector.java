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
import stroom.dataconnection.shared.DataConnectionType;
import stroom.dataconnection.shared.StoredDataSettings;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/// Queries CSV held in the document itself. See [StoredDataConnector].
@Singleton
class CsvConnector extends StoredDataConnector {

    /// Creates the connector. Guice supplies every argument.
    @Inject
    CsvConnector(final DataTableCache tableCache,
                 final Provider<DataConnectionConfig> configProvider) {
        super(tableCache, configProvider);
    }

    @Override
    public DataConnectionType getType() {
        return DataConnectionType.CSV;
    }

    @Override
    StoredDataSettings settings(final DataConnectionDoc doc) {
        return doc.getCsv();
    }

    @Override
    String dataName() {
        return "CSV";
    }

    @Override
    boolean hasData(final DataConnectionDoc doc) {
        return doc.getCsv() != null && !NullSafe.isBlankString(doc.getCsv().getData());
    }

    @Override
    long dataSize(final DataConnectionDoc doc) {
        return doc.getCsv() == null
                ? 0
                : CsvReader.utf8Length(doc.getCsv().getData());
    }

    // The data's length and hash stand for the data, so the key does not hold the CSV itself.
    @Override
    Object dataKey(final DataConnectionDoc doc) {
        final CsvSettings csv = doc.getCsv();
        final String data = NullSafe.string(csv.getData());
        return new CsvKey(csv.getDelimiter(), csv.getHeader(), data.length(), data.hashCode());
    }

    @Override
    DataTable read(final DataConnectionDoc doc, final int maxRows) {
        return CsvReader.parse(doc.getCsv(), maxRows);
    }


    // --------------------------------------------------------------------------------


    private record CsvKey(String delimiter, Boolean header, int dataLength, int dataHash) {

    }
}
