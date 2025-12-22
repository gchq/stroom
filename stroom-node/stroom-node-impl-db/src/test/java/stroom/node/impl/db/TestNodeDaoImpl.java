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

package stroom.node.impl.db;

import stroom.node.api.FindNodeCriteria;
import stroom.node.impl.NodeDao;
import stroom.node.shared.Node;
import stroom.test.common.util.db.DbTestModule;
import stroom.test.common.util.db.DbTestUtil;
import stroom.util.AuditUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;

import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.jooq.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.node.impl.db.jooq.tables.Node.NODE;

class TestNodeDaoImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestNodeDaoImpl.class);

    private static final List<Table<?>> TABLES = List.of(
            NODE);

    @Inject
    private NodeDao nodeDao;
    @Inject
    private NodeDbConnProvider nodeDbConnProvider;

    @BeforeEach
    void setUp() throws SQLException {
        final Injector injector = Guice.createInjector(
                new DbTestModule(),
                new NodeDbModule(),
                new NodeDaoModule());

        injector.injectMembers(this);

        try (final Connection connection = nodeDbConnProvider.getConnection()) {
            for (final Table<?> table : TABLES) {
                final String tableName = table.getName();
                LOGGER.debug("Clearing table {}", tableName);
                DbTestUtil.clearTables(connection, List.of(tableName));
            }
        }
    }

    @Test
    void tryCreate() {

        final String nodeName = "node1";

        assertThat(nodeDao.getNode(nodeName))
                .isNull();

        final Node node2 = createSkeletonNodeObj(nodeName);

        final Node node3 = nodeDao.tryCreate(node2);

        assertThat(nodeDao.getNode(nodeName))
                .isEqualTo(node3);

        final Node node4 = nodeDao.tryCreate(node2);

        assertThat(node4)
                .isEqualTo(node3);

        assertThat(nodeDao.getNode(nodeName))
                .isEqualTo(node3);
    }

    @Test
    void update() {
        final String nodeName = "node1";

        assertThat(nodeDao.getNode(nodeName))
                .isNull();

        final Node node2 = createSkeletonNodeObj(nodeName);

        final Node node3 = nodeDao.tryCreate(node2);
        assertThat(node3.getUrl())
                .isNull();

        node3.setUrl("myUrl");
        final Node node4 = nodeDao.update(node3);
        assertThat(node4.getUrl())
                .isEqualTo("myUrl");
    }

    private Node createSkeletonNode(final String nodeName) {
        return nodeDao.tryCreate(createSkeletonNodeObj(nodeName));
    }

    private static Node createSkeletonNodeObj(final String nodeName) {
        final Node node2 = new Node();
        node2.setName(nodeName);
        node2.setPriority(1);
        AuditUtil.stamp(() -> "test", node2);
        return node2;
    }

    @Test
    void find() {
        final Node enabledNode1 = createSkeletonNode("enabledNode1");
        final Node enabledNode2 = createSkeletonNode("enabledNode2");
        final Node disabledNode1 = createSkeletonNode("disabledNode1");
        final Node disabledNode2 = createSkeletonNode("disabledNode2");

        Stream.of(enabledNode1, enabledNode2)
                .forEach(node -> {
                    node.setEnabled(true);
                    nodeDao.update(node);
                });

        Stream.of(disabledNode1, disabledNode2)
                .forEach(node -> {
                    node.setEnabled(false);
                    nodeDao.update(node);
                });

        final FindNodeCriteria findNodeCriteria = new FindNodeCriteria();
        findNodeCriteria.setEnabled(true);

        final ResultPage<Node> nodeResultPage = nodeDao.find(findNodeCriteria);

        assertThat(nodeResultPage.getValues())
                .extracting(Node::getName)
                .containsExactlyInAnyOrder(
                        enabledNode1.getName(),
                        enabledNode2.getName());
    }

    @Test
    void getNode() {
        final String nodeName = "node1";

        assertThat(nodeDao.getNode(nodeName))
                .isNull();

        final Node node2 = createSkeletonNodeObj(nodeName);

        final Node node3 = nodeDao.tryCreate(node2);

        assertThat(node3)
                .isNotNull();
        assertThat(node3.getName())
                .isEqualTo(nodeName);
    }
}
