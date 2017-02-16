/*
 * Copyright 2016 Crown Copyright
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

package stroom.db.migration.mysql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.server.ObjectMarshaller;
import stroom.query.api.DocRef;
import stroom.entity.shared.DocRefs;
import org.flywaydb.core.api.migration.jdbc.JdbcMigration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public class V5_0_0_12__Script implements JdbcMigration {
    private static final Logger LOGGER = LoggerFactory.getLogger(V5_0_0_12__Script.class);

    @Override
    public void migrate(Connection connection) throws Exception {
        // Change parent pipeline references to be document references.
        makeParentReferenceDocRef(connection);
    }

    private void makeParentReferenceDocRef(final Connection connection) throws Exception {
        try (final Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE SCRIPT ADD COLUMN DEP longtext;");
        }
        try (final Statement statement = connection.createStatement()) {
            // Create a map of document references.
            final Map<Long, DocRefs> map = new HashMap<>();
            try (final ResultSet resultSet = statement.executeQuery("SELECT s.ID, d.UUID, d.NAME FROM SCRIPT s JOIN SCRIPT_DEP sd ON (s.ID = sd.FK_SCRIPT_ID) JOIN SCRIPT d ON (d.ID = sd.DEP_FK_SCRIPT_ID);")) {
                while (resultSet.next()) {
                    final long id = resultSet.getLong(1);
                    final String uuid = resultSet.getString(2);
                    final String name = resultSet.getString(3);
                    final DocRef docRef = new DocRef("Script", uuid, name);

                    DocRefs docRefs = map.get(id);
                    if (docRefs == null) {
                        docRefs = new DocRefs();
                        map.put(id, docRefs);
                    }
                    docRefs.add(docRef);
                }
            }

            final ObjectMarshaller<DocRefs> objectMarshaller = new ObjectMarshaller<>(DocRefs.class);
            for (final Map.Entry<Long, DocRefs> entry : map.entrySet()) {
                final String xml = objectMarshaller.marshal(entry.getValue());
                try (final PreparedStatement preparedStatement = connection.prepareStatement("UPDATE SCRIPT SET DEP = ? WHERE ID = ?")) {
                    preparedStatement.setString(1, xml);
                    preparedStatement.setLong(2, entry.getKey());
                    preparedStatement.executeUpdate();
                }
            }
        }

        try (final Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE SCRIPT_DEP;");
        }
    }
}
