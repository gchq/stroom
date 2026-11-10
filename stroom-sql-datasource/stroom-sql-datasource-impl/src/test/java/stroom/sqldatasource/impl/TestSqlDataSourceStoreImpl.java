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

import stroom.docref.DocRef;
import stroom.docstore.impl.Serialiser2FactoryImpl;
import stroom.docstore.impl.StoreFactoryImpl;
import stroom.docstore.impl.memory.MemoryPersistence;
import stroom.security.mock.MockSecurityContext;
import stroom.sqldatasource.shared.AthenaConnectionSettings;
import stroom.sqldatasource.shared.JdbcConnectionSettings;
import stroom.sqldatasource.shared.SqlConnectionType;
import stroom.sqldatasource.shared.SqlDataSourceDoc;
import stroom.util.shared.EntityServiceException;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSqlDataSourceStoreImpl {

    private static SqlDataSourceStoreImpl store() {
        final MockSecurityContext securityContext = new MockSecurityContext();
        final SqlDataSourceConfig config = new SqlDataSourceConfig(
                List.of("^jdbc:mysql://reporting-db:3306/.*$"), null, null, null, null, null, null, null);
        return new SqlDataSourceStoreImpl(
                new StoreFactoryImpl(new MemoryPersistence(), null, securityContext, null, () -> null),
                securityContext,
                new SqlDataSourceSerialiser(new Serialiser2FactoryImpl()),
                () -> config);
    }

    private static SqlDataSourceDoc withUrl(final SqlDataSourceDoc doc, final String url) {
        return doc.copy()
                .jdbc(JdbcConnectionSettings.builder()
                        .url(url)
                        .build())
                .build();
    }

    @Test
    void createdDocumentHasDefaults() {
        final SqlDataSourceStoreImpl store = store();

        final DocRef docRef = store.createDocument("HR");
        final SqlDataSourceDoc doc = store.readDocument(docRef);

        assertThat(doc.getType()).isEqualTo(SqlDataSourceDoc.TYPE);
        assertThat(doc.getName()).isEqualTo("HR");
        assertThat(doc.getConnectionType()).isEqualTo(SqlConnectionType.JDBC);
        assertThat(doc.getMaxRows()).isEqualTo(SqlDataSourceDoc.DEFAULT_MAX_ROWS);
        assertThat(store.list()).containsExactly(docRef);
    }

    @Test
    void allowedUrlIsSaved() {
        final SqlDataSourceStoreImpl store = store();
        final SqlDataSourceDoc doc = store.readDocument(store.createDocument("HR"));

        store.writeDocument(withUrl(doc, "jdbc:mysql://reporting-db:3306/hr"));

        assertThat(store.readDocument(doc.asDocRef()).getJdbc().getUrl())
                .isEqualTo("jdbc:mysql://reporting-db:3306/hr");
    }

    @Test
    void urlNotAllowedIsRefusedOnSave() {
        final SqlDataSourceStoreImpl store = store();
        final SqlDataSourceDoc doc = store.readDocument(store.createDocument("HR"));

        assertThatThrownBy(() -> store.writeDocument(withUrl(doc, "jdbc:mysql://elsewhere:3306/hr")))
                .isInstanceOf(EntityServiceException.class)
                .hasMessageContaining("allowedUrlPatterns");
        assertThat(store.readDocument(doc.asDocRef()).getJdbc())
                .as("nothing was saved")
                .isNull();
    }

    @Test
    void deniedPropertyIsRefusedOnSave() {
        final SqlDataSourceStoreImpl store = store();
        final SqlDataSourceDoc doc = store.readDocument(store.createDocument("HR"));

        assertThatThrownBy(() -> store.writeDocument(
                withUrl(doc, "jdbc:mysql://reporting-db:3306/hr?allowLoadLocalInfile=true")))
                .isInstanceOf(EntityServiceException.class)
                .hasMessageContaining("allowLoadLocalInfile");
    }

    @Test
    void documentWithNoUrlYetCanBeSaved() {
        final SqlDataSourceStoreImpl store = store();
        final SqlDataSourceDoc doc = store.readDocument(store.createDocument("HR"));

        store.writeDocument(doc.copy()
                .description("Not finished")
                .build());

        assertThat(store.readDocument(doc.asDocRef()).getDescription()).isEqualTo("Not finished");
    }

    @Test
    void athenaDocumentIgnoresLeftoverJdbcSettings() {
        // A JDBC URL left behind after switching the connection type is not used, so it is not checked.
        final SqlDataSourceStoreImpl store = store();
        final SqlDataSourceDoc doc = store.readDocument(store.createDocument("Events"));

        store.writeDocument(withUrl(doc, "jdbc:mysql://elsewhere:3306/hr").copy()
                .connectionType(SqlConnectionType.ATHENA)
                .athena(AthenaConnectionSettings.builder()
                        .region("eu-west-2")
                        .build())
                .build());

        assertThat(store.readDocument(doc.asDocRef()).getConnectionType()).isEqualTo(SqlConnectionType.ATHENA);
    }
}
