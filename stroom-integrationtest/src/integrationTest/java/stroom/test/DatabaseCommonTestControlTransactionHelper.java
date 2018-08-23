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

package stroom.test;

import stroom.entity.StroomEntityManager;
import stroom.entity.util.ConnectionUtil;
import stroom.entity.util.HqlBuilder;
import stroom.entity.util.SqlBuilder;

import javax.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * Class to help with testing.
 * </p>
 */
public class DatabaseCommonTestControlTransactionHelper {
    private final StroomEntityManager entityManager;

    @Inject
    DatabaseCommonTestControlTransactionHelper(final StroomEntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Clear a HIBERNATE context.
     */
    void clearContext() {
        entityManager.clearContext();
    }

    /**
     * Count the records.
     *
     * @param clazz to count
     * @return the count
     */
    public int countEntity(final Class<?> clazz) {
        final HqlBuilder sql = new HqlBuilder();
        sql.append("SELECT count(*) FROM ");
        sql.append(clazz.getName());
        return (int) entityManager.executeQueryLongResult(sql);
    }

//    int countDocs(final String type) {
//        final SqlBuilder sql = new SqlBuilder();
//        sql.append("SELECT count(*) FROM doc WHERE type = ");
//        sql.arg(type);
//        sql.append(" AND ext = ");
//        sql.arg("meta");
//        return (int) entityManager.executeNativeQueryLongResult(sql);
//    }
//
//    public void shutdown() {
//        entityManager.shutdown();
//    }
//
//    public void truncateTable(final String tableName) {
//        truncateTables(Collections.singletonList(tableName));
//    }
//
//    public void truncateTables(final List<String> tableNames) {
//        List<String> truncateStatements = tableNames.stream()
//                .map(tableName -> "TRUNCATE TABLE " + tableName)
//                .collect(Collectors.toList());
//
//        executeStatementsWithNoConstraints(truncateStatements);
//    }

    void clearTables(final List<String> tableNames) {
        List<String> deleteStatements = tableNames.stream()
                .map(tableName -> "DELETE FROM " + tableName)
                .collect(Collectors.toList());

        executeStatementsWithNoConstraints(deleteStatements);
    }

    void enableConstraints() {
        final String sql = "SET FOREIGN_KEY_CHECKS=1";
        try (final Connection connection = ConnectionUtil.getConnection()) {
            ConnectionUtil.executeStatement(connection, sql);
        } catch (SQLException e) {
            throw new RuntimeException(String.format("Error executing %s", sql), e);
        }
    }

    private void executeStatementsWithNoConstraints(final List<String> statements) {
        final List<String> allStatements = new ArrayList<>();
        allStatements.add("SET FOREIGN_KEY_CHECKS=0");
        allStatements.addAll(statements);
        allStatements.add("SET FOREIGN_KEY_CHECKS=1");

        try (final Connection connection = ConnectionUtil.getConnection()) {
            ConnectionUtil.executeStatements(connection, allStatements);
        } catch (SQLException e) {
            throw new RuntimeException(String.format("Error executing %s", allStatements), e);
        }
    }
}
