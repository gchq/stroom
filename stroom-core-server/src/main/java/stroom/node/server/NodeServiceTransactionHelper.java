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

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.server.util.StroomDatabaseInfo;
import stroom.entity.server.util.StroomEntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import stroom.entity.server.util.ConnectionUtil;
import stroom.entity.server.util.SQLBuilder;
import stroom.node.shared.Node;
import stroom.node.shared.Rack;
import stroom.node.shared.SystemTableStatus;

/**
 * Helper class so that we can split out some transactions away from
 * NodeService.
 */
@Transactional
@Component
public class NodeServiceTransactionHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(NodeServiceTransactionHelper.class);

    private final StroomEntityManager entityManager;
    private final StroomDatabaseInfo stroomDatabaseInfo;
    private final DataSource dataSource;

    @Inject
    NodeServiceTransactionHelper(final StroomEntityManager entityManager, final StroomDatabaseInfo stroomDatabaseInfo,
                                 final DataSource dataSource) {
        this.entityManager = entityManager;
        this.stroomDatabaseInfo = stroomDatabaseInfo;
        this.dataSource = dataSource;
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public Node getNode(final String name) {
        final SQLBuilder sql = new SQLBuilder();
        sql.append("SELECT r FROM ");
        sql.append(Node.class.getName());
        sql.append(" r where r.name = ");
        sql.arg(name);

        // This should just bring back 1
        final List<Node> results = entityManager.executeQueryResultList(sql);

        if (results == null || results.size() == 0) {
            return null;
        }
        return results.get(0);
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public Rack getRack(final String name) {
        final SQLBuilder sql = new SQLBuilder();
        sql.append("SELECT r FROM ");
        sql.append(Rack.class.getName());
        sql.append(" r where r.name = ");
        sql.arg(name);

        // This should just bring back 1
        final List<Rack> results = entityManager.executeQueryResultList(sql);

        if (results == null || results.size() == 0) {
            return null;
        }
        return results.get(0);
    }

    /**
     * Create a new transaction to create the node .... only ever called once at
     * initial deployment time.
     */
    @Transactional(readOnly = false, propagation = Propagation.REQUIRES_NEW)
    public Node buildNode(final String nodeName, final String rackName) {
        Node node = getNode(nodeName);

        if (node == null) {
            Rack rack = getRack(rackName);
            if (rack == null) {
                rack = Rack.create(rackName);
                rack = entityManager.saveEntity(rack);
            }

            node = Node.create(rack, nodeName);
            node = entityManager.saveEntity(node);

            LOGGER.info("Unable to find default node " + nodeName + ", so I created it in rack " + rackName);
        }

        return node;
    }

    @Transactional(readOnly = true)
    public List<SystemTableStatus> findSystemTableStatus() {
        final List<SystemTableStatus> rtnList = new ArrayList<>();
        Connection connection = null;

        try {
            connection = dataSource.getConnection();
            connection.setReadOnly(true);

            if (stroomDatabaseInfo.isMysql()) {
                try (PreparedStatement ps = connection.prepareStatement("show table status where comment != 'VIEW'")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            final SystemTableStatus status = new SystemTableStatus();
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

                final ResultSet resultSet = databaseMetaData.getTables(null, null, null, new String[] { "TABLE" });

                while (resultSet.next()) {
                    final SystemTableStatus status = new SystemTableStatus();
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
        return rtnList;
    }
}
