/*
 * Copyright 2017 Crown Copyright
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

import org.flywaydb.core.api.migration.jdbc.JdbcMigration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.ObjectMarshaller;
import stroom.query.api.v2.DocRef;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class V5_0_0_11__Visualisations implements JdbcMigration {
    private static final Logger LOGGER = LoggerFactory.getLogger(V5_0_0_11__Visualisations.class);

    @Override
    public void migrate(Connection connection) throws Exception {
        // Change parent pipeline references to be document references.
        makeParentReferenceDocRef(connection);
    }

    private void makeParentReferenceDocRef(final Connection connection) throws Exception {
        final ObjectMarshaller<DocRef> objectMarshaller = new ObjectMarshaller<>(DocRef.class);

        try (final Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE VIS ADD COLUMN SCRIPT longtext;");
        }
        try (final Statement statement = connection.createStatement()) {
            try (final ResultSet resultSet = statement.executeQuery("SELECT v.ID, s.UUID, s.NAME FROM VIS v JOIN SCRIPT s ON (s.ID = v.FK_SCRIPT_ID);")) {
                while (resultSet.next()) {
                    final long id = resultSet.getLong(1);
                    final String scriptUUID = resultSet.getString(2);
                    final String scriptName = resultSet.getString(3);
                    final DocRef docRef = new DocRef("Script", scriptUUID, scriptName);
                    final String refXML = objectMarshaller.marshal(docRef);

                    try (final PreparedStatement preparedStatement = connection.prepareStatement("UPDATE VIS SET SCRIPT = ? WHERE ID = ?")) {
                        preparedStatement.setString(1, refXML);
                        preparedStatement.setLong(2, id);
                        preparedStatement.executeUpdate();
                    }
                }
            }
        }
        try (final Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE VIS DROP FOREIGN KEY VIS_FK_SCRIPT_ID");
        }
        try (final Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE VIS DROP COLUMN FK_SCRIPT_ID;");
        }
    }
}
