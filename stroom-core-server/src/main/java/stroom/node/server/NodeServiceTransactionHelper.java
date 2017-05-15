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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import stroom.entity.server.util.SQLBuilder;
import stroom.entity.server.util.StroomDatabaseInfo;
import stroom.entity.server.util.StroomEntityManager;
import stroom.node.shared.Node;
import stroom.node.shared.Rack;

import javax.inject.Inject;
import java.util.List;

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

    @Inject
    NodeServiceTransactionHelper(final StroomEntityManager entityManager, final StroomDatabaseInfo stroomDatabaseInfo) {
        this.entityManager = entityManager;
        this.stroomDatabaseInfo = stroomDatabaseInfo;
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
}
