/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.docstore.impl.db.migration;

import stroom.docstore.impl.db.migration.v710.pipeline.legacy.PipelineDataMigration;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@SuppressWarnings("unused")
public class V07_10_07_100__pipeline_migration extends BaseJavaMigration {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory
            .getLogger(V07_10_07_100__pipeline_migration.class);

    @Override
    public void migrate(final Context context) throws Exception {
        final PipelineDataMigration pipelineDataMigration = new PipelineDataMigration();
        try (final PreparedStatement preparedStatement = context.getConnection()
                .prepareStatement("SELECT" +
                                  " `id`," +
                                  " `uuid`," +
                                  " `name`," +
                                  " `data`" +
                                  " FROM `doc`" +
                                  " WHERE `type` = ?" +
                                  " AND `ext` = ?")) {
            preparedStatement.setString(1, "Pipeline");
            preparedStatement.setString(2, "xml");

            try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                Exception lastException = null;
                while (resultSet.next()) {
                    try {
                        final long id = resultSet.getLong(1);
                        final String uuid = resultSet.getString(2);
                        final String name = resultSet.getString(3);
                        final String data = resultSet.getString(4);

                        // Check there is no json already.
                        final String existingJson = getPipelineJson(context, uuid);
                        if (NullSafe.isNonBlankString(existingJson)) {
                            LOGGER.info("Pipeline {} has already been migrated, deleting XML", name);
                            if (!deleteDocEntry(context, id)) {
                                LOGGER.error("Error deleting pipeline {} XML", name);
                            }
                        } else {
                            // Perform migration.
                            final String json = pipelineDataMigration.xmlToJson(data);
                            // Update record.
                            LOGGER.info("Updating pipeline {} to JSON structure", name);
                            if (!updatePipelineJson(context, json, id)) {
                                LOGGER.error("Error updating pipeline {} to json", name);
                            }
                        }
                    } catch (final Exception e) {
                        LOGGER.error("Error migrating pipeline", e);
                        lastException = e;
                    }
                }

                if (lastException != null) {
                    throw lastException;
                }
            }
        }
    }

    private String getPipelineJson(final Context context, final String uuid) throws SQLException {
        // Detect any annotation entries that we can't find users for.
        try (final PreparedStatement preparedStatement = context.getConnection()
                .prepareStatement("SELECT" +
                                  " `data`" +
                                  " FROM `doc`" +
                                  " WHERE `uuid` = ?" +
                                  " AND `type` = ?" +
                                  " AND `ext` = ?")) {
            preparedStatement.setString(1, uuid);
            preparedStatement.setString(2, "Pipeline");
            preparedStatement.setString(3, "json");

            try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString(1);
                }
            }
        }
        return null;
    }

    private boolean updatePipelineJson(final Context context, final String json, final long id) throws SQLException {
        try (final PreparedStatement preparedStatement = context.getConnection()
                .prepareStatement("UPDATE `doc` " +
                                  "SET `data` = ?, `ext` = ? " +
                                  "WHERE `id` = ?")) {
            preparedStatement.setString(1, json);
            preparedStatement.setString(2, "json");
            preparedStatement.setLong(3, id);
            return preparedStatement.executeUpdate() == 1;
        }
    }

    private boolean deleteDocEntry(final Context context, final long id) throws SQLException {
        try (final PreparedStatement preparedStatement = context.getConnection()
                .prepareStatement("DELETE FROM `doc` " +
                                  "WHERE `id` = ?")) {
            preparedStatement.setLong(1, id);
            return preparedStatement.executeUpdate() == 1;
        }
    }
}
