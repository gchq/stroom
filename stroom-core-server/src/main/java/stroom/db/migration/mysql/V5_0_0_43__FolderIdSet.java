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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class V5_0_0_43__FolderIdSet implements JdbcMigration {
    private static final Logger LOGGER = LoggerFactory.getLogger(V5_0_0_43__FolderIdSet.class);

    @Override
    public void migrate(final Connection connection) throws Exception {
        try (final Statement statement = connection.createStatement()) {
            try (final ResultSet resultSet = statement.executeQuery("SELECT ID, DAT FROM STRM_PROC_FILT;")) {
                while (resultSet.next()) {
                    final long id = resultSet.getLong(1);
                    final String data = resultSet.getString(2);

                    LOGGER.info("Starting stream processor filter upgrade: " + id);

                    if (data == null) {
                        LOGGER.info("Incomplete configuration found");

                    } else {
                        String newData = data;
                        newData = newData.replaceAll("systemGroupIdSet", "folderIdSet");

                        if (!newData.equals(data)) {
                            LOGGER.info("Modifying stream processor filter");

                            try (final PreparedStatement preparedStatement = connection.prepareStatement("UPDATE STRM_PROC_FILT SET DAT = ? WHERE ID = ?")) {
                                preparedStatement.setString(1, newData);
                                preparedStatement.setLong(2, id);
                                preparedStatement.executeUpdate();
                            }
                        } else {
                            LOGGER.info("No change required");
                        }
                    }

                    LOGGER.info("Finished stream processor filter upgrade: " + id);
                }
            }
        }
    }
}
