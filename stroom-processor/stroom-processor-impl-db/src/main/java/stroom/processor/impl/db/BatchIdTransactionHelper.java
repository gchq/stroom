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

package stroom.processor.impl.db;

import org.jooq.BatchBindStep;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import stroom.db.util.JooqUtil;

import javax.sql.DataSource;
import java.util.List;


// @Transactional
public class BatchIdTransactionHelper {
    private final DataSource dataSource;
    private final Table<Record> tempIdTable;
    private final Field<Long> idField;

    BatchIdTransactionHelper(final DataSource dataSource, final String tempIdTableName) {
        this.dataSource = dataSource;

        tempIdTable = DSL.table(DSL.name(tempIdTableName));
        idField = DSL.field(DSL.name(tempIdTableName, "id"), Long.class);
    }

    public int createTempIdTable(final String tempIdTable) {
        return JooqUtil.contextResult(dataSource, context ->
                context
                        .createTableIfNotExists(tempIdTable)
                        .column(idField)
                        .execute());


//        final SqlBuilder sql = new SqlBuilder();
//
//        sql.append("CREATE TABLE ");
//        sql.append("IF NOT EXISTS ");
//        sql.append(tempIdTable);
//        sql.append(" (");
//        sql.append("ID");
//        sql.append(" bigint(20) NOT NULL");
//        sql.append(")");
//        sql.append(" ENGINE=InnoDB DEFAULT CHARSET=latin1;");
//
//        return executeUpdate(sql);
    }

    public int insertIntoTempIdTable(final List<Long> idList) {
        if (idList == null || idList.size() == 0) {
            return 0;
        }

        return JooqUtil.contextResult(dataSource, context -> {
            final BatchBindStep batchBindStep = context.batch(
                    context
                            .insertInto(tempIdTable)
                            .columns(idField)
                            .values((Long) null));

            for (Long id : idList) {
                batchBindStep.bind(id);
            }

            batchBindStep.execute();

            return idList.size();
        });

//        final SqlBuilder sql = new SqlBuilder();
//        sql.append("INSERT INTO ");
//        sql.append(tempIdTable);
//        sql.append(" (ID)");
//        sql.append(" VALUES (");
//
//        if (idList.size() > 0) {
//            for (Long id : idList) {
//                sql.arg(id);
//                sql.append(",");
//            }
//            sql.setLength(sql.length() - 1);
//            sql.append(")");
//        }
//
//        return executeUpdate(sql);
    }

    public int getTempIdCount(final String tempIdTable) {
        return JooqUtil.contextResult(dataSource, context -> context.selectCount().from(tempIdTable).execute());


//        final SqlBuilder sql = new SqlBuilder();
//        sql.append("SELECT COUNT(");
//        sql.append("ID");
//        sql.append(") FROM ");
//        sql.append(tempIdTable);
//        return stroomEntityManager.executeNativeQueryLongResult(sql);
    }

    public int deleteWithJoin(final Table<?> fromTable, final Field<Long> fromColumn) {
        return JooqUtil.contextResult(dataSource, context -> context
                .deleteFrom(fromTable)
                .where(fromColumn.in(context.select(idField).from(tempIdTable)))
                .execute());


//        final SqlBuilder sql = new SqlBuilder();
//        // MySQL does a delete much faster if we join to the table
//        // containing the ids to delete.
//        sql.append("DELETE ");
//        sql.append(fromTable);
//        sql.append(" FROM ");
//        sql.append(fromTable);
//        sql.append(" INNER JOIN ");
//        sql.append(joinTable);
//        sql.append(" ON ");
//        sql.append("(");
//        sql.append(fromTable);
//        sql.append(".");
//        sql.append(fromColumn);
//        sql.append(" = ");
//        sql.append(joinTable);
//        sql.append(".");
//        sql.append(joinColumn);
//        sql.append(")");
//        return executeUpdate(sql);
    }

    public int truncateTempIdTable(final String tempIdTable) {
        return JooqUtil.contextResult(dataSource, context -> context
                .truncate(tempIdTable)
                .execute());


//        final SqlBuilder sql = new SqlBuilder();
//
//        sql.append("TRUNCATE TABLE ");
//        sql.append(tempIdTable);
//
//        return executeUpdate(sql);
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
//
//    private Long executeUpdate(final SqlBuilder sql) {
//        return stroomEntityManager.executeNativeUpdate(sql);
//    }
}
