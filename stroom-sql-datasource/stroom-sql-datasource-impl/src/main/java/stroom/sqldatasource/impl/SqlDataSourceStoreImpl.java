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

import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.StoreFactory;
import stroom.security.api.SecurityContext;
import stroom.sqldatasource.shared.AthenaConnectionSettings;
import stroom.sqldatasource.shared.JdbcConnectionSettings;
import stroom.sqldatasource.shared.SqlConnectionType;
import stroom.sqldatasource.shared.SqlDataSourceDoc;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Objects;

/// Stores [SqlDataSourceDoc] documents.
///
/// A JDBC URL, or an Amazon Athena region and workgroup, is checked against the administrator's
/// controls when a user saves a document, so a mistake is reported at once. Imports are not checked,
/// so a content pack can be imported and then edited; its settings are checked again whenever it
/// connects.
@Singleton
public class SqlDataSourceStoreImpl
        extends AbstractDocumentStore<SqlDataSourceDoc>
        implements SqlDataSourceStore {

    private final Provider<SqlDataSourceConfig> configProvider;

    /// Creates the instance. Guice supplies every argument.
    @Inject
    SqlDataSourceStoreImpl(final StoreFactory storeFactory,
                           final SecurityContext securityContext,
                           final SqlDataSourceSerialiser serialiser,
                           final Provider<SqlDataSourceConfig> configProvider) {
        super(storeFactory,
                securityContext,
                serialiser,
                SqlDataSourceDoc.TYPE,
                SqlDataSourceDoc::builder,
                SqlDataSourceDoc::copy);
        this.configProvider = configProvider;
    }

    /// Saves a document, refusing settings the administrator has not allowed: a JDBC URL or driver
    /// property, or an Athena region and workgroup.
    ///
    /// A document with no URL or region yet may be saved, so it can be built up over several saves.
    /// Only the settings of the document's connection type are checked.
    ///
    /// @param document The document to save.
    /// @return The saved document.
    /// @throws EntityServiceException If the settings break the administrator's controls.
    @Override
    public SqlDataSourceDoc writeDocument(final SqlDataSourceDoc document) {
        final List<String> problems = findProblems(document);
        if (!problems.isEmpty()) {
            throw new EntityServiceException(String.join("\n", problems));
        }
        return super.writeDocument(document);
    }

    private List<String> findProblems(final SqlDataSourceDoc document) {
        final SqlConnectionType connectionType = Objects.requireNonNullElse(
                document.getConnectionType(),
                SqlConnectionType.JDBC);
        if (connectionType == SqlConnectionType.ATHENA) {
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
