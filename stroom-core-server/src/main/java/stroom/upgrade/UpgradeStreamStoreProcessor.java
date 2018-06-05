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
 *
 */

package stroom.upgrade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.util.ConnectionUtil;
import stroom.entity.util.PreparedStatementUtil;
import stroom.feed.MetaMap;
import stroom.streamstore.api.StreamSource;
import stroom.streamstore.api.StreamStore;
import stroom.streamstore.api.StreamTarget;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamAttributeConstants;
import stroom.streamtask.StreamProcessorTaskExecutor;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.streamtask.shared.StreamTask;
import stroom.util.date.DateUtil;
import stroom.util.logging.LogExecutionTime;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

class UpgradeStreamStoreProcessor implements StreamProcessorTaskExecutor {
    private static final String TASK_TABLE = "TRANSLATION_STREAM_TASK";
    private static final String TASK_ARCHIVE_TABLE = TASK_TABLE + "_ARCHIVE";

    private static final Logger LOGGER = LoggerFactory.getLogger(UpgradeStreamStoreProcessor.class);
    private static volatile String query;
    private static volatile int args;
    private static ReentrantLock lock = new ReentrantLock();

    private final StreamStore streamStore;
    private final DataSource dataSource;

    @Inject
    UpgradeStreamStoreProcessor(final StreamStore streamStore,
                                final DataSource dataSource) {
        this.streamStore = streamStore;
        this.dataSource = dataSource;
    }

    private static void buildQuery(final StringBuilder builder, final String taskTable) {
        builder.append("SELECT ");
        builder.append("S.RECEIVED_MS, "); // 1
        builder.append("S.EFFECTIVE_MS, ");
        builder.append("S.FILE_SIZE, "); // 3
        builder.append("S.STREAM_SIZE, ");
        builder.append("F.NAME, "); // 5
        builder.append("ST.END_TIME_MS - ST.START_TIME_MS, ");
        builder.append("ST.REC_READ, "); // 7
        builder.append("ST.REC_WRITTEN, ");
        builder.append("ST.REC_WARNING, "); // 9
        builder.append("ST.REC_ERROR, ");
        builder.append("N.NAME, "); // 11
        builder.append(" 'END' FROM STREAM S ");
        builder.append(" LEFT OUTER JOIN ");
        builder.append(taskTable);
        builder.append(" ST ON (ST.FK_TARGET_STREAM_ID = S.ID) ");
        builder.append(" JOIN FEED F ON (F.ID = S.FK_FEED_ID)");
        builder.append(" LEFT OUTER JOIN NODE N ON (N.ID = ST.FK_NODE_ID) ");
        builder.append(" WHERE S.ID = ?");
    }

    private static void fillMap(final ResultSet resultSet, final MetaMap map) throws SQLException {
        int i = 1;
        fillMapTime(resultSet, map, i++, StreamAttributeConstants.CREATE_TIME); // 1
        fillMapTime(resultSet, map, i++, StreamAttributeConstants.EFFECTIVE_TIME);
        fillMapLong(resultSet, map, i++, StreamAttributeConstants.FILE_SIZE); // 3
        fillMapLong(resultSet, map, i++, StreamAttributeConstants.STREAM_SIZE);
        fillMapString(resultSet, map, i++, StreamAttributeConstants.FEED); // 5
        fillMapLong(resultSet, map, i++, StreamAttributeConstants.DURATION);
        fillMapLong(resultSet, map, i++, StreamAttributeConstants.REC_READ); // 7
        fillMapLong(resultSet, map, i++, StreamAttributeConstants.REC_WRITE);
        fillMapLong(resultSet, map, i++, StreamAttributeConstants.REC_WARN); // 9
        fillMapLong(resultSet, map, i++, StreamAttributeConstants.REC_ERROR);
        fillMapString(resultSet, map, i++, StreamAttributeConstants.NODE); // 11

    }

    private static void fillMapTime(final ResultSet resultSet, final MetaMap map, final int col, final String key)
            throws SQLException {
        final Long timeMs = resultSet.getLong(col);
        if (timeMs != null) {
            map.put(key, DateUtil.createNormalDateTimeString(timeMs));
        }
    }

    private static void fillMapLong(final ResultSet resultSet, final MetaMap map, final int col, final String key)
            throws SQLException {
        final Long num = resultSet.getLong(col);
        if (num != null) {
            map.put(key, num.toString());
        }
    }

    private static void fillMapString(final ResultSet resultSet, final MetaMap map, final int col, final String key)
            throws SQLException {
        final String str = resultSet.getString(col);
        if (str != null) {
            map.put(key, str);
        }
    }

    public static void main(final String[] args) {
        System.out.println(buildQueryWithArchiveTable());
    }

    public static String buildQueryWithArchiveTable() {
        final StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("( ");
        buildQuery(queryBuilder, TASK_TABLE);
        queryBuilder.append(") UNION ( ");
        buildQuery(queryBuilder, TASK_ARCHIVE_TABLE);
        queryBuilder.append(") ");
        return queryBuilder.toString();
    }

    public static String buildQueryWithoutArchiveTable() {
        final StringBuilder queryBuilder = new StringBuilder();
        buildQuery(queryBuilder, TASK_TABLE);
        return queryBuilder.toString();
    }

    private String getQuery(final Connection connection) {
        if (query != null) {
            return query;
        }

        lock.lock();
        try {
            if (query != null) {
                return query;
            }
            try {
                final boolean streamExists = ConnectionUtil.tableExists(connection, "STREAM");
                if (streamExists) {
                    final boolean archiveExists = ConnectionUtil.tableExists(connection, TASK_ARCHIVE_TABLE);
                    if (archiveExists) {
                        query = buildQueryWithArchiveTable();
                        args = 2;
                    } else {
                        query = buildQueryWithoutArchiveTable();
                        args = 1;
                    }
                }

            } catch (final SQLException sqlEx) {
                LOGGER.error("getQuery()", sqlEx);
            }
        } finally {
            lock.unlock();
        }

        return query;
    }

    @Override
    public void exec(final StreamProcessor streamProcessor, final StreamProcessorFilter streamProcessorFilter,
                     final StreamTask streamTask, final StreamSource streamSource) {
        final Stream stream = streamSource.getStream();
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        final String streamTime = DateUtil.createNormalDateTimeString(stream.getCreateMs());
        LOGGER.info("exec() - Processing stream {} {} - Start", stream, streamTime);

        final MetaMap metaMap = new MetaMap();
        try (final Connection connection = dataSource.getConnection()) {
            final String query = getQuery(connection);

            final ArrayList<Object> argObjs = new ArrayList<>();
            for (int i = 0; i < args; i++) {
                argObjs.add(stream.getId());
            }

            if (query != null && !query.isEmpty()) {
                try (final PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                    PreparedStatementUtil.setArguments(preparedStatement, argObjs);
                    try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                        while (resultSet.next()) {
                            fillMap(resultSet, metaMap);
                        }
                    }
                } catch (final SQLException e) {
                    LOGGER.error(e.getMessage(), e);
                    throw e;
                }
            }
        } catch (final SQLException sqlEx) {
            LOGGER.error("exec() {} {}", new Object[]{query, args}, sqlEx);
        }

        if (metaMap.size() > 0) {
            final StreamTarget streamTarget = streamStore.openExistingStreamTarget(stream.getId());
            streamTarget.getAttributeMap().putAll(metaMap);
            streamStore.closeStreamTarget(streamTarget);
        } else {
            LOGGER.warn("exec() - No attributes added for stream {}", stream);
        }

        LOGGER.info("exec() - Processing stream {} {} - Finished in {} added {} attributes", stream, streamTime,
                logExecutionTime, metaMap.size());
    }
}
