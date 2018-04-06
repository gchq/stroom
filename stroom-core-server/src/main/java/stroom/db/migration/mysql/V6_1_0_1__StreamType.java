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

import org.flywaydb.core.api.migration.jdbc.JdbcMigration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.SQLNameConstants;
import stroom.entity.util.PreparedStatementUtil;
import stroom.entity.util.SqlBuilder;
import stroom.streamstore.shared.StreamType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class V6_1_0_1__StreamType implements JdbcMigration {
    private static final Logger LOGGER = LoggerFactory.getLogger(V6_1_0_1__StreamType.class);

    @Override
    public void migrate(final Connection connection) throws Exception {
        final long now = System.currentTimeMillis();

        for (final StreamType streamType : StreamType.initialValues()) {
            final long id = streamType.getId();

            boolean exists;
            try (final PreparedStatement preparedStatement = connection.prepareStatement("SELECT ID FROM " + StreamType.TABLE_NAME + " WHERE ID = ?")) {
                preparedStatement.setLong(1, id);
                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    exists = resultSet.next();
                }
            }

            if (!exists) {
                try {
                    // We use SQL to insert because we need a predefined key.
                    final SqlBuilder sql = new SqlBuilder();
                    sql.append("INSERT INTO ");
                    sql.append(StreamType.TABLE_NAME);
                    sql.append(" (");
                    sql.append(StreamType.ID);
                    sql.append(",");
                    sql.append(StreamType.VERSION);
                    sql.append(",");
                    sql.append(StreamType.UPDATE_TIME);
                    sql.append(",");
                    sql.append(StreamType.CREATE_TIME);
                    sql.append(",");
                    sql.append(StreamType.UPDATE_USER);
                    sql.append(",");
                    sql.append(StreamType.CREATE_USER);
                    sql.append(",");
                    sql.append(StreamType.PATH);
                    sql.append(",");
                    sql.append(StreamType.EXTENSION);
                    sql.append(",");
                    sql.append(SQLNameConstants.NAME);
                    sql.append(",");
                    sql.append(SQLNameConstants.PURPOSE);
                    sql.append(") VALUES (");
                    sql.arg(streamType.getId());
                    sql.append(",");
                    sql.arg(1);
                    sql.append(",");
                    sql.arg(now);
                    sql.append(",");
                    sql.arg(now);
                    sql.append(",");
                    sql.arg("upgrade");
                    sql.append(",");
                    sql.arg("upgrade");
                    sql.append(",");
                    sql.arg(streamType.getPath());
                    sql.append(",");
                    sql.arg(streamType.getExtension());
                    sql.append(",");
                    sql.arg(streamType.getName());
                    sql.append(",");
                    sql.arg(streamType.getPpurpose());
                    sql.append(")");

                    try (final PreparedStatement preparedStatement = connection.prepareStatement(sql.toString())) {
                        PreparedStatementUtil.setArguments(preparedStatement, sql.getArgs());
                        preparedStatement.executeUpdate();
                    }
                } catch (final Exception ex) {
                    LOGGER.error("init() - Added initial stream type {}", streamType, ex);
                }
            }
        }
    }
}
