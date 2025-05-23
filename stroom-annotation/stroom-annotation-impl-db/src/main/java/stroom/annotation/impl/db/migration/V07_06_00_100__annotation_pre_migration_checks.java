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

package stroom.annotation.impl.db.migration;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class V07_06_00_100__annotation_pre_migration_checks extends BaseJavaMigration {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory
            .getLogger(V07_06_00_100__annotation_pre_migration_checks.class);

    @Override
    public void migrate(final Context context) throws Exception {
        if (stroomUserExists(context)) {
            boolean error = false;

            // Detect any annotation entries that we can't find users for.
            try (final PreparedStatement preparedStatement = context.getConnection().prepareStatement(
                    """
                            SELECT DISTINCT(ae.create_user)
                            FROM annotation_entry ae
                            WHERE NOT EXISTS (
                                SELECT NULL
                                FROM stroom_user su
                                WHERE su.name = ae.create_user);""")) {
                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        try {
                            final String user = resultSet.getString(1);
                            LOGGER.error(() ->
                                    "Pre migration check failure:\n`annotation_entry.create_user` '" +
                                    user +
                                    "' not found in `stroom_user`");
                            error = true;
                        } catch (final RuntimeException e) {
                            LOGGER.error(e.getMessage(), e);
                        }
                    }
                }
            }

            // Detect any annotation entry assignments that we can't find users for.
            try (final PreparedStatement preparedStatement = context.getConnection().prepareStatement(
                    """
                            SELECT DISTINCT(ae.data)
                            FROM annotation_entry ae
                            WHERE ae.data IS NOT NULL
                            AND ae.type = "Assigned"
                            AND NOT EXISTS (
                                SELECT NULL
                                FROM stroom_user su
                                WHERE su.uuid = ae.data);""")) {
                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        try {
                            final String user = resultSet.getString(1);
                            LOGGER.error(() ->
                                    "Pre migration check failure:\n`annotation_entry.data` for 'Assigned' " +
                                    user +
                                    "' not found in `stroom_user`");
                            error = true;
                        } catch (final RuntimeException e) {
                            LOGGER.error(e.getMessage(), e);
                        }
                    }
                }
            }

            if (error) {
                throw new RuntimeException("Pre migration check failure");
            }
        }
    }

    private boolean stroomUserExists(final Context context) throws SQLException {
        try (final PreparedStatement preparedStatement = context.getConnection().prepareStatement(
                """
                        SELECT COUNT(1)
                        FROM information_schema.tables
                        WHERE table_schema = database()
                        AND table_name = 'stroom_user';""")) {
            try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1) > 0;
                } else {
                    return false;
                }
            }
        }
    }
}
