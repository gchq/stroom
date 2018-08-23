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

package stroom.node;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.StroomDatabaseInfo;
import stroom.entity.shared.Sort;
import stroom.entity.shared.Sort.Direction;
import stroom.guice.StroomBeanStore;
import stroom.node.shared.DBTableService;
import stroom.node.shared.DBTableStatus;
import stroom.node.shared.FindDBTableCriteria;
import stroom.security.Security;
import stroom.security.shared.PermissionNames;
import stroom.util.shared.CompareUtil;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DBTableServiceImpl implements DBTableService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DBTableServiceImpl.class);

    private final StroomBeanStore beanStore;
    private final StroomDatabaseInfo stroomDatabaseInfo;
    private final Security security;

    @Inject
    DBTableServiceImpl(final StroomBeanStore beanStore,
                       final StroomDatabaseInfo stroomDatabaseInfo,
                       final Security security) {
        this.beanStore = beanStore;
        this.stroomDatabaseInfo = stroomDatabaseInfo;
        this.security = security;
    }

    @Override
    public List<DBTableStatus> findSystemTableStatus(final FindDBTableCriteria criteria) {
        return security.secureResult(PermissionNames.MANAGE_DB_PERMISSION, () -> {
            final List<DBTableStatus> rtnList = new ArrayList<>();

            if (beanStore != null) {
                final Object dataSource = beanStore.getInstance("dataSource");
                // TODO : @66 Reinstate DB table status for all DBs

//                final Object statisticsDataSource = beanStore.getInstance("statisticsDataSource");

                addTableStatus(dataSource, rtnList);
//                addTableStatus(statisticsDataSource, rtnList);
            }

            rtnList.sort((o1, o2) -> {
                if (criteria.getSortList() != null && criteria.getSortList().size() > 0) {
                    for (final Sort sort : criteria.getSortList()) {
                        final String field = sort.getField();

                        int compare = 0;
                        if (DBTableStatus.FIELD_DATABASE.equals(field)) {
                            compare = CompareUtil.compareString(o1.getDb(), o2.getDb());
                        } else if (DBTableStatus.FIELD_TABLE.equals(field)) {
                            compare = CompareUtil.compareString(o1.getTable(), o2.getTable());
                        } else if (DBTableStatus.FIELD_ROW_COUNT.equals(field)) {
                            compare = CompareUtil.compareLong(o1.getCount(), o2.getCount());
                        } else if (DBTableStatus.FIELD_DATA_SIZE.equals(field)) {
                            compare = CompareUtil.compareLong(o1.getDataSize(), o2.getDataSize());
                        } else if (DBTableStatus.FIELD_INDEX_SIZE.equals(field)) {
                            compare = CompareUtil.compareLong(o1.getIndexSize(), o2.getIndexSize());
                        }
                        if (Direction.DESCENDING.equals(sort.getDirection())) {
                            compare = compare * -1;
                        }

                        if (compare != 0) {
                            return compare;
                        }
                    }
                } else {
                    int compare = o1.getDb().compareToIgnoreCase(o2.getDb());
                    if (compare == 0) {
                        compare = o1.getTable().compareToIgnoreCase(o2.getTable());
                    }
                    return compare;
                }

                return 0;
            });

            return rtnList;
        });
    }


    private void addTableStatus(final Object bean, final List<DBTableStatus> rtnList) {
        if (!(bean instanceof DataSource)) {
            return;
        }

        final DataSource dataSource = (DataSource) bean;
        try (final Connection connection = dataSource.getConnection()) {
            connection.setReadOnly(true);

            if (stroomDatabaseInfo.isMysql()) {
                try (final PreparedStatement preparedStatement = connection.prepareStatement("show table status where comment != 'VIEW'",
                        ResultSet.TYPE_FORWARD_ONLY,
                        ResultSet.CONCUR_READ_ONLY,
                        ResultSet.CLOSE_CURSORS_AT_COMMIT)) {
                    try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                        while (resultSet.next()) {
                            final DBTableStatus status = new DBTableStatus();
                            status.setDb(connection.getCatalog());
                            status.setTable(resultSet.getString("Name"));
                            status.setCount(resultSet.getLong("Rows"));
                            status.setDataSize(resultSet.getLong("Data_length"));
                            status.setIndexSize(resultSet.getLong("Index_length"));

                            rtnList.add(status);
                        }
                    }
                }

            } else {
                final DatabaseMetaData databaseMetaData = connection.getMetaData();
                try (final ResultSet resultSet = databaseMetaData.getTables(null, null, null, new String[]{"TABLE"})) {
                    while (resultSet.next()) {
                        final DBTableStatus status = new DBTableStatus();
                        status.setTable(resultSet.getString("TABLE_NAME"));
                        rtnList.add(status);
                    }
                }
            }

        } catch (final SQLException e) {
            LOGGER.error("findSystemTableStatus()", e);
        }
    }
}
