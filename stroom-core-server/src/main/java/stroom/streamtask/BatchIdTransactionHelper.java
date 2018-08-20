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

package stroom.streamtask;

import stroom.entity.StroomEntityManager;
import stroom.entity.util.SqlBuilder;

import javax.inject.Inject;
import java.util.List;


// @Transactional
public class BatchIdTransactionHelper {
    private final StroomEntityManager stroomEntityManager;

    @Inject
    BatchIdTransactionHelper(final StroomEntityManager stroomEntityManager) {
        this.stroomEntityManager = stroomEntityManager;
    }

    public long createTempIdTable(final String tempIdTable) {
        final SqlBuilder sql = new SqlBuilder();

        sql.append("CREATE TABLE ");
        sql.append("IF NOT EXISTS ");
        sql.append(tempIdTable);
        sql.append(" (");
        sql.append("ID");
        sql.append(" bigint(20) NOT NULL");
        sql.append(")");
        sql.append(" ENGINE=InnoDB DEFAULT CHARSET=latin1;");

        return executeUpdate(sql);
    }

    public long insertIntoTempIdTable(final String tempIdTable, final List<Long> idList) {
        if (idList == null || idList.size() == 0) {
            return 0;
        }

        final SqlBuilder sql = new SqlBuilder();
        sql.append("INSERT INTO ");
        sql.append(tempIdTable);
        sql.append(" (ID)");
        sql.append(" VALUES (");

        if (idList.size() > 0) {
            for (Long id : idList) {
                sql.arg(id);
                sql.append(",");
            }
            sql.setLength(sql.length() - 1);
            sql.append(")");
        }

        return executeUpdate(sql);
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
