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
/// A JDBC URL is checked against the administrator's controls when a user saves a document, so a
/// mistake is reported at once. Imports are not checked, so a content pack can be imported and then
/// edited; its URL is checked again whenever it connects.
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

    /// Saves a document, refusing a JDBC URL or driver property the administrator has not allowed.
    ///
    /// A document with no URL yet may be saved, so it can be built up over several saves.
    ///
    /// @param document The document to save.
    /// @return The saved document.
    /// @throws EntityServiceException If the JDBC settings break the administrator's controls.
    @Override
    public SqlDataSourceDoc writeDocument(final SqlDataSourceDoc document) {
        checkJdbcSettings(document);
        return super.writeDocument(document);
    }

    private void checkJdbcSettings(final SqlDataSourceDoc document) {
        final SqlConnectionType connectionType = Objects.requireNonNullElse(
                document.getConnectionType(),
                SqlConnectionType.JDBC);
        final JdbcConnectionSettings jdbc = document.getJdbc();
        if (connectionType == SqlConnectionType.JDBC
            && NullSafe.isNonBlankString(NullSafe.get(jdbc, JdbcConnectionSettings::getUrl))) {
            final List<String> problems = JdbcConnectionValidator.findProblems(jdbc, configProvider.get());
            if (!problems.isEmpty()) {
                throw new EntityServiceException(String.join("\n", problems));
            }
        }
    }
}
