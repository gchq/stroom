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

package stroom.streamtask.server;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import stroom.entity.server.util.SqlBuilder;
import stroom.entity.server.util.StroomDatabaseInfo;
import stroom.entity.server.util.StroomEntityManager;
import stroom.streamstore.shared.Stream;

import javax.inject.Inject;

@Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
@Component
public class BatchIdTransactionHelper {
    private final StroomDatabaseInfo stroomDatabaseInfo;
    private final StroomEntityManager stroomEntityManager;

    @Inject
    public BatchIdTransactionHelper(final StroomDatabaseInfo stroomDatabaseInfo, final StroomEntityManager stroomEntityManager) {
        this.stroomDatabaseInfo = stroomDatabaseInfo;
        this.stroomEntityManager = stroomEntityManager;
    }

    public long createTempIdTable(final String tempIdTable) {
        final SqlBuilder sql = new SqlBuilder();

        sql.append("CREATE TABLE ");
        sql.append("IF NOT EXISTS ");
        sql.append(tempIdTable);
        sql.append(" (");
        sql.append("ID");
        if (stroomDatabaseInfo.isMysql()) {
            sql.append(" bigint(20) NOT NULL");
        } else {
            sql.append(" BIGINT");
        }
        sql.append(")");
        if (stroomDatabaseInfo.isMysql()) {
            sql.append(" ENGINE=InnoDB DEFAULT CHARSET=latin1;");
        }

        return executeUpdate(sql);
    }

    public long insertIntoTempIdTable(final String tempIdTable, final SqlBuilder select) {
        final SqlBuilder sql = new SqlBuilder();

        sql.append("INSERT INTO ");
        sql.append(tempIdTable);
        sql.append(" (");
        sql.append(Stream.ID);
        sql.append(") ");
        sql.append(select);

        if (stroomDatabaseInfo.isMysql()) {
            return executeUpdate(sql);
        } else {
            // sql.append(") WITH DATA");
            executeUpdate(sql);
            // HSQLDB does not return the number of rows added so return 1 so
            // that the rest of the code runs.
            return getTempIdCount(tempIdTable);
        }
    }

    public Long getTempIdCount(final String tempIdTable) {
        final SqlBuilder sql = new SqlBuilder();
        sql.append("SELECT COUNT(");
        sql.append("ID");
        sql.append(") FROM ");
        sql.append(tempIdTable);
        return stroomEntityManager.executeNativeQueryLongResult(sql);
    }

    public long deleteWithJoin(final String fromTable, final String fromColumn, final String joinTable,
                               final String joinColumn) {
        final SqlBuilder sql = new SqlBuilder();
        if (stroomDatabaseInfo.isMysql()) {
            // MySQL does a delete much faster if we join to the table
            // containing the ids to delete.
            sql.append("DELETE ");
            sql.append(fromTable);
            sql.append(" FROM ");
            sql.append(fromTable);
            sql.append(" INNER JOIN ");
            sql.append(joinTable);
            sql.append(" ON ");
            sql.append("(");
            sql.append(fromTable);
            sql.append(".");
            sql.append(fromColumn);
            sql.append(" = ");
            sql.append(joinTable);
            sql.append(".");
            sql.append(joinColumn);
            sql.append(")");
        } else {
            // HSQLDB cannot do a delete with a join so instead use WHERE IN and
            // select the ids to delete.
            sql.append("DELETE FROM ");
            sql.append(fromTable);
            sql.append(" WHERE ");
            sql.append(fromColumn);
            sql.append(" IN (");
            sql.append("SELECT ");
            sql.append(joinColumn);
            sql.append(" FROM ");
            sql.append(joinTable);
            sql.append(")");
        }
        return executeUpdate(sql);
    }

    public long truncateTempIdTable(final String tempIdTable) {
        final SqlBuilder sql = new SqlBuilder();

        sql.append("TRUNCATE TABLE ");
        sql.append(tempIdTable);

        return executeUpdate(sql);
    }

    // private long dropTempStreamIdTable() {
    // final SQLBuilder sql = new SQLBuilder();
    // sql.append("DROP TABLE ");
    // if (!stroomDatabaseInfo.isMysql()) {
    // sql.append("module.");
    // }
    // sql.append(tempIdTable);
    //
    // return executeUpdate(sql);
    // }

    private Long executeUpdate(final SqlBuilder sql) {
        return stroomEntityManager.executeNativeUpdate(sql);
    }
}
