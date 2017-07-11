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

package stroom.node.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import stroom.entity.server.util.ConnectionUtil;
import stroom.entity.server.util.StroomDatabaseInfo;
import stroom.entity.shared.BaseCriteria.OrderByDirection;
import stroom.entity.shared.OrderBy;
import stroom.node.shared.DBTableService;
import stroom.node.shared.DBTableStatus;
import stroom.security.Secured;

import javax.inject.Inject;
import javax.inject.Named;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

@Transactional(readOnly = true)
@Secured(DBTableStatus.MANAGE_DB_PERMISSION)
@Component("dbTableService")
public class DBTableServiceImpl implements DBTableService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DBTableServiceImpl.class);

    private final DataSource dataSource;
    private final DataSource statisticsDataSource;
    private final StroomDatabaseInfo stroomDatabaseInfo;

    @Inject
    DBTableServiceImpl(final DataSource dataSource, @Named("statisticsDataSource") final DataSource statisticsDataSource, final StroomDatabaseInfo stroomDatabaseInfo) {
        this.dataSource = dataSource;
        this.statisticsDataSource = statisticsDataSource;
        this.stroomDatabaseInfo = stroomDatabaseInfo;
    }

    @Override
    public List<DBTableStatus> findSystemTableStatus(final OrderBy orderBy, final OrderByDirection orderByDirection) {
        final List<DBTableStatus> rtnList = new ArrayList<>();
        addTableStatus(dataSource, rtnList);
        addTableStatus(statisticsDataSource, rtnList);

        rtnList.sort((o1, o2) -> {
            int diff = 0;

            if (orderBy == null || DBTableStatus.DATABASE.equals(orderBy)) {
                diff = o1.getDb().compareToIgnoreCase(o2.getDb());

                if (OrderByDirection.DESCENDING.equals(orderByDirection)) {
                    diff = diff * -1;
                }

                if (diff == 0) {
                    diff = o1.getTable().compareToIgnoreCase(o2.getTable());
                }
            } else {
                if (DBTableStatus.TABLE.equals(orderBy)) {
                    diff = o1.getTable().compareToIgnoreCase(o2.getTable());
                } else if (DBTableStatus.ROW_COUNT.equals(orderBy)) {
                    diff = Long.compare(o1.getCount(), o2.getCount());
                } else if (DBTableStatus.DATA_SIZE.equals(orderBy)) {
                    diff = Long.compare(o1.getDataSize(), o2.getDataSize());
                } else if (DBTableStatus.INDEX_SIZE.equals(orderBy)) {
                    diff = Long.compare(o1.getIndexSize(), o2.getIndexSize());
                }

                if (OrderByDirection.DESCENDING.equals(orderByDirection)) {
                    diff = diff * -1;
                }
            }

            return diff;
        });

        return rtnList;
    }

    private void addTableStatus(final DataSource dataSource, final List<DBTableStatus> rtnList) {
        Connection connection = null;

        try {
            connection = dataSource.getConnection();
            connection.setReadOnly(true);

            if (stroomDatabaseInfo.isMysql()) {
                try (PreparedStatement ps = connection.prepareStatement("show table status where comment != 'VIEW'")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            final DBTableStatus status = new DBTableStatus();
                            status.setDb(connection.getCatalog());
                            status.setTable(rs.getString("Name"));
                            status.setCount(rs.getLong("Rows"));
                            status.setDataSize(rs.getLong("Data_length"));
                            status.setIndexSize(rs.getLong("Index_length"));

                            rtnList.add(status);
                        }
                        rs.close();
                    }
                    ps.close();
                }

            } else {
                final DatabaseMetaData databaseMetaData = connection.getMetaData();

                final ResultSet resultSet = databaseMetaData.getTables(null, null, null, new String[]{"TABLE"});

                while (resultSet.next()) {
                    final DBTableStatus status = new DBTableStatus();
                    status.setTable(resultSet.getString("TABLE_NAME"));
                    rtnList.add(status);
                }

                resultSet.close();
            }

        } catch (final Exception ex) {
            LOGGER.error("findSystemTableStatus()", ex);
        } finally {
            ConnectionUtil.close(connection);
        }
    }
}
