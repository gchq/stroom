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

package stroom.policy.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public class DataRetentionStreamDeleter implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataRetentionStreamDeleter.class);

    private static final String SQL_DELETE = "" +
            "UPDATE " +
            Stream.TABLE_NAME +
            " SET " +
            Stream.STATUS +
            " = ?" +
            ", " +
            Stream.STATUS_MS +
            " = ?" +
            " WHERE " +
            Stream.ID +
            " IN ";

    private final Connection connection;
    private PreparedStatement preparedStatement;
    private int currentBatchSize;

    public DataRetentionStreamDeleter(final Connection connection) {
        Objects.requireNonNull(connection, "No connection");
        this.connection = connection;
    }

    void deleteStreams(final List<Long> streamIds) throws SQLException {
        final int batchSize = streamIds.size();
        if (currentBatchSize != batchSize) {
            closePreparedStatement();
            final String sql = SQL_DELETE + getBatchSql(batchSize);
            LOGGER.debug(sql);
            preparedStatement = connection.prepareStatement(sql);
            currentBatchSize = batchSize;
        }

        // Ensure the prepared statement is cleaned after last use
        try {
            preparedStatement.clearParameters();
        } catch (SQLException e) {
            throw new RuntimeException("Error clearing parameters on preparedStatement", e);
        }

        int parameterIndex = 1;
        preparedStatement.setByte(parameterIndex++, StreamStatus.DELETED.getPrimitiveValue());
        preparedStatement.setLong(parameterIndex++, System.currentTimeMillis());
        for (final long streamId : streamIds) {
            preparedStatement.setLong(parameterIndex++, streamId);
        }

        final int count = preparedStatement.executeUpdate();

        LOGGER.debug("Updated " + count + " rows");
    }

    private String getBatchSql(final int batchSize) {
        final StringBuilder sql = new StringBuilder();
        sql.append("(");
        for (int i = 0; i < batchSize; i++) {
            sql.append("?,");
        }
        sql.setLength(sql.length() - 1);
        sql.append(")");
        return sql.toString();
    }

    private void closePreparedStatement() {
        if (preparedStatement != null) {
            try {
                preparedStatement.close();
            } catch (SQLException e) {
                LOGGER.error("Error closing preparedStatement", e);
            }
            preparedStatement = null;
        }
    }

    @Override
    public void close() {
        closePreparedStatement();
    }
}
