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

package stroom.core.db;

import stroom.node.shared.DBTableStatus;
import stroom.node.shared.FindDBTableCriteria;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CompareUtil;
import stroom.util.shared.CompareUtil.FieldComparators;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;

class DBTableService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DBTableService.class);

    private final Set<DataSource> dataSources;
    private final SecurityContext securityContext;

    // This ought to live in DBTableStatus but GWT won't allow it
    // Maps criteria field names to comparator for that field
    private static final FieldComparators<DBTableStatus> FIELD_COMPARATORS = FieldComparators.builder(
                    DBTableStatus.class)
            .addStringComparator(DBTableStatus.FIELD_DATABASE, DBTableStatus::getDb)
            .addStringComparator(DBTableStatus.FIELD_TABLE, DBTableStatus::getTable)
            .addLongComparator(DBTableStatus.FIELD_ROW_COUNT, DBTableStatus::getCount)
            .addLongComparator(DBTableStatus.FIELD_DATA_SIZE, DBTableStatus::getDataSize)
            .addLongComparator(DBTableStatus.FIELD_INDEX_SIZE, DBTableStatus::getIndexSize)
            .build();

    @Inject
    DBTableService(final Set<DataSource> dataSources,
                   final SecurityContext securityContext) {
        this.dataSources = dataSources;
        this.securityContext = securityContext;
    }

    public ResultPage<DBTableStatus> getSystemTableStatus() {
        return securityContext.secureResult(() -> doFind(new FindDBTableCriteria()));
    }

    public ResultPage<DBTableStatus> findSystemTableStatus(final FindDBTableCriteria criteria) {
        return securityContext.secureResult(() -> doFind(criteria));
    }

    private ResultPage<DBTableStatus> doFind(final FindDBTableCriteria criteria) {
        return securityContext.secureResult(AppPermission.MANAGE_DB_PERMISSION, () -> {
            // We need the results distinct by key (db name, table name)
            final Map<TableKey, DBTableStatus> rtnMap = new HashMap<>();

            if (dataSources != null) {
                dataSources.forEach(dataSource -> addTableStatus(dataSource, rtnMap));
            }

            final List<DBTableStatus> sortedList = rtnMap.values()
                    .stream()
                    .sorted(buildComparator(criteria))
                    .collect(Collectors.toList());

            return ResultPage.createPageLimitedList(sortedList, criteria.getPageRequest());
        });
    }

    private Comparator<DBTableStatus> buildComparator(final BaseCriteria criteria) {

        final Comparator<DBTableStatus> comparator;
        if (criteria != null
            && criteria.getSortList() != null
            && !criteria.getSortList().isEmpty()) {

            comparator = CompareUtil.buildCriteriaComparator(FIELD_COMPARATORS, criteria);
        } else {
            // default sort of db then table name
            comparator = Comparator
                    .comparing(DBTableStatus::getDb, String::compareToIgnoreCase)
                    .thenComparing(DBTableStatus::getTable, String::compareToIgnoreCase);
        }
        return comparator;
    }


    private void addTableStatus(final DataSource dataSource,
                                final Map<TableKey, DBTableStatus> rtnMap) {
        try (final Connection connection = dataSource.getConnection()) {
            connection.setReadOnly(true);

            // Filter out any legacy tables
            final String sql = "" +
                               "show table status " +
                               "where comment != 'VIEW' " +
                               "and Name not like 'OLD_%' ";
            try (final PreparedStatement preparedStatement = connection.prepareStatement(
                    sql,
                    ResultSet.TYPE_FORWARD_ONLY,
                    ResultSet.CONCUR_READ_ONLY,
                    ResultSet.CLOSE_CURSORS_AT_COMMIT)) {
                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        final DBTableStatus status = new DBTableStatus(
                                connection.getCatalog(),
                                resultSet.getString("Name"),
                                resultSet.getLong("Rows"),
                                resultSet.getLong("Data_length"),
                                resultSet.getLong("Index_length"));

                        // We have a lot of db connections and they may be able to see each other's
                        // tables so use a map to distinct them
                        final TableKey tableKey = new TableKey(status.getDb(), status.getTable());
                        rtnMap.putIfAbsent(tableKey, status);
                    }
                }
            }
        } catch (final SQLException e) {
            LOGGER.error("findSystemTableStatus()", e);
        }
    }

    private static class TableKey {

        private final String dbName;
        private final String tableName;

        TableKey(final String dbName, final String tableName) {
            this.dbName = Objects.requireNonNull(dbName);
            this.tableName = Objects.requireNonNull(tableName);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final TableKey tableKey = (TableKey) o;
            return dbName.equals(tableKey.dbName) &&
                   tableName.equals(tableKey.tableName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dbName, tableName);
        }
    }
}
