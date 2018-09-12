/*
 * Copyright 2018 Crown Copyright
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
import stroom.db.migration.EntityReferenceReplacer;
import stroom.entity.server.ObjectMarshaller;
import stroom.entity.shared.DocRef;
import stroom.util.logging.StroomLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class V5_5_0_1__PipelineProperties implements JdbcMigration {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(V5_5_0_1__PipelineProperties.class);

    @Override
    public void migrate(Connection connection) throws Exception {
        // Update pipeline data.
        updatePipelineData(connection);
    }

    private void updatePipelineData(final Connection connection) throws Exception {
        try (final Statement statement = connection.createStatement()) {
            try (final ResultSet resultSet = statement.executeQuery("SELECT ID, NAME, DAT FROM PIPE;")) {
                while (resultSet.next()) {
                    final long id = resultSet.getLong(1);
                    final String name = resultSet.getString(2);
                    final String data = resultSet.getString(3);

                    LOGGER.info("Starting pipeline upgrade: " + name);

                    if (data == null) {
                        LOGGER.info("Incomplete configuration found");

                    } else {
                        String newData = data;
                        newData = newData.replaceAll("splitWhenBiggerThan", "rollSize");
                        newData = newData.replaceAll("maxSize", "rollSize");

                        if (!newData.equals(data)) {
                            LOGGER.info("Modifying pipeline");

                            try (final PreparedStatement preparedStatement = connection.prepareStatement("UPDATE PIPE SET DAT = ? WHERE ID = ?")) {
                                preparedStatement.setString(1, newData);
                                preparedStatement.setLong(2, id);
                                preparedStatement.executeUpdate();
                            }
                        } else {
                            LOGGER.info("No change required");
                        }
                    }

                    LOGGER.info("Finished pipeline upgrade: " + name);
                }
            }
        }
    }
}
