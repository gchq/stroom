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

package stroom.sqldatasource.impl;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.importexport.api.ImportExportDocument;
import stroom.sqldatasource.shared.SqlDataSourceDoc;

import jakarta.inject.Inject;

import java.io.IOException;

/// Reads and writes [SqlDataSourceDoc] documents for storage and import/export.
public class SqlDataSourceSerialiser implements DocumentSerialiser2<SqlDataSourceDoc> {

    private final Serialiser2<SqlDataSourceDoc> delegate;

    /// Creates the instance. Guice supplies every argument.
    @Inject
    SqlDataSourceSerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(SqlDataSourceDoc.class);
    }

    /// Reads a document.
    ///
    /// @param importExportDocument The stored or imported form of the document.
    /// @return The document.
    /// @throws IOException If the document cannot be read.
    @Override
    public SqlDataSourceDoc read(final ImportExportDocument importExportDocument) throws IOException {
        return delegate.read(importExportDocument);
    }

    /// Writes a document.
    ///
    /// @param document The document.
    /// @return The stored or exported form of the document.
    /// @throws IOException If the document cannot be written.
    @Override
    public ImportExportDocument write(final SqlDataSourceDoc document) throws IOException {
        return delegate.write(document);
    }
}
