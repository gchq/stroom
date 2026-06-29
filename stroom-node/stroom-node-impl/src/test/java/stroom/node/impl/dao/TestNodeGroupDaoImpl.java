/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.node.impl.dao;

import stroom.node.impl.NodeDao;
import stroom.node.impl.NodeGroupDao;
import stroom.node.impl.db.NodeDbConnProvider;
import stroom.node.impl.db.NodeDbModule;
import stroom.node.impl.db.jooq.tables.NodeGroupLink;
import stroom.node.shared.FindNodeGroupRequest;
import stroom.node.shared.Node;
import stroom.node.shared.NodeGroup;
import stroom.node.shared.NodeGroupChange;
import stroom.node.shared.NodeGroupState;
import stroom.test.common.util.db.DbTestModule;
import stroom.test.common.util.db.DbTestUtil;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static stroom.node.impl.db.jooq.tables.Node.NODE;
import static stroom.node.impl.db.jooq.tables.NodeGroup.NODE_GROUP;
import static stroom.node.impl.db.jooq.tables.NodeGroupLink.NODE_GROUP_LINK;

class TestNodeGroupDaoImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestNodeGroupDaoImpl.class);

    private static final List<Table<?>> TABLES = List.of(
            NODE_GROUP_LINK,
            NODE_GROUP,
            NODE);

    @Inject
    private NodeGroupDao nodeGroupDao;
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

    // ---- CRUD ----

    @Test
    void testCreate() {
        final NodeGroup created = createGroup("testGroup", true);
        assertThat(created).isNotNull();
        assertThat(created.getId()).isPositive();
        assertThat(created.getName()).isEqualTo("testGroup");
        assertThat(created.isEnabled()).isTrue();
        assertThat(created.getVersion()).isEqualTo(1);
        assertThat(created.getCreateUser()).isEqualTo("test");
        assertThat(created.getUpdateUser()).isEqualTo("test");
    }

    @Test
    void testFetchById() {
        final NodeGroup created = createGroup("fetchById", true);
        final NodeGroup fetched = nodeGroupDao.fetchById(created.getId());
        assertThat(fetched).isNotNull();
        assertThat(fetched.getId()).isEqualTo(created.getId());
        assertThat(fetched.getName()).isEqualTo("fetchById");
    }

    @Test
    void testFetchById_notFound() {
        final NodeGroup fetched = nodeGroupDao.fetchById(99999);
        assertThat(fetched).isNull();
    }

    @Test
    void testFetchByName() {
        createGroup("byName", false);
        final NodeGroup fetched = nodeGroupDao.fetchByName("byName");
        assertThat(fetched).isNotNull();
        assertThat(fetched.getName()).isEqualTo("byName");
        assertThat(fetched.isEnabled()).isFalse();
    }

    @Test
    void testFetchByName_notFound() {
        final NodeGroup fetched = nodeGroupDao.fetchByName("nonexistent");
        assertThat(fetched).isNull();
    }

    @Test
    void testUpdate() {
        final NodeGroup created = createGroup("updateMe", true);
        final NodeGroup updated = nodeGroupDao.update(
                created.copy()
                        .name("updatedName")
                        .enabled(false)
                        .updateUser("updater")
                        .updateTimeMs(System.currentTimeMillis())
                        .build());

        assertThat(updated.getName()).isEqualTo("updatedName");
        assertThat(updated.isEnabled()).isFalse();
    }

    @Test
    void testUpdate_duplicateName() {
        createGroup("existingName", true);
        final NodeGroup second = createGroup("secondName", true);

        assertThatThrownBy(() -> nodeGroupDao.update(
                second.copy()
                        .name("existingName")
                        .updateUser("test")
                        .updateTimeMs(System.currentTimeMillis())
                        .build()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("existingName");
    }

    @Test
    void testDelete() {
        final NodeGroup created = createGroup("deleteMe", true);
        assertThat(nodeGroupDao.fetchById(created.getId())).isNotNull();

        nodeGroupDao.delete(created.getId());
        assertThat(nodeGroupDao.fetchById(created.getId())).isNull();
    }

    // ---- Find ----

    @Test
    void testFind_all() {
        createGroup("alpha", true);
        createGroup("bravo", false);
        createGroup("charlie", true);

        final ResultPage<NodeGroup> result = nodeGroupDao.find(
                new FindNodeGroupRequest(null, null, null));
        assertThat(result.getValues()).hasSize(3);
    }

    @Test
    void testFind_withFilter() {
        createGroup("prod-group", true);
        createGroup("prod-workers", true);
        createGroup("staging-group", false);

        final ResultPage<NodeGroup> result = nodeGroupDao.find(
                new FindNodeGroupRequest(null, null, "prod"));
        assertThat(result.getValues()).hasSize(2);
        assertThat(result.getValues())
                .extracting(NodeGroup::getName)
                .allMatch(name -> name.startsWith("prod"));
    }

    @Test
    void testFind_withFilter_noMatch() {
        createGroup("alpha", true);

        final ResultPage<NodeGroup> result = nodeGroupDao.find(
                new FindNodeGroupRequest(null, null, "zzz"));
        assertThat(result.getValues()).isEmpty();
    }

    // ---- Node Group State ----

    @Test
    void testGetNodeGroupState_allNodesReturned() {
        // Create nodes and a group
        final Node nodeA = createNode("nodeA");
        final Node nodeB = createNode("nodeB");
        final Node nodeC = createNode("nodeC");
        final NodeGroup group = createGroup("stateGroup", true);

        // Link only nodeA to the group
        nodeGroupDao.updateNodeGroupState(new NodeGroupChange(nodeA.getId(), group.getId(), true));

        // All three nodes should be returned, but only nodeA is included
        final ResultPage<NodeGroupState> result = nodeGroupDao.getNodeGroupState(group.getId());
        assertThat(result.getValues()).hasSize(3);

        final Map<String, Boolean> stateMap = result.getValues().stream()
                .collect(Collectors.toMap(s -> s.getNode().getName(), NodeGroupState::isIncluded));
        assertThat(stateMap.get("nodeA")).isTrue();
        assertThat(stateMap.get("nodeB")).isFalse();
        assertThat(stateMap.get("nodeC")).isFalse();
    }

    @Test
    void testGetNodeGroupState_noLinks() {
        createNode("lonelyNode");
        final NodeGroup group = createGroup("emptyGroup", true);

        final ResultPage<NodeGroupState> result = nodeGroupDao.getNodeGroupState(group.getId());
        assertThat(result.getValues()).hasSize(1);
        assertThat(result.getValues().getFirst().isIncluded()).isFalse();
    }

    @Test
    void testGetNodeGroupState_orderedByName() {
        createNode("zeta");
        createNode("alpha");
        createNode("mu");
        final NodeGroup group = createGroup("orderGroup", true);

        final ResultPage<NodeGroupState> result = nodeGroupDao.getNodeGroupState(group.getId());
        final List<String> names = result.getValues().stream()
                .map(s -> s.getNode().getName())
                .collect(Collectors.toList());
        assertThat(names).isSorted();
    }

    // ---- Update Node Group State (link/unlink) ----

    @Test
    void testUpdateNodeGroupState_include() {
        final Node node = createNode("includeMe");
        final NodeGroup group = createGroup("linkGroup", true);

        final boolean result = nodeGroupDao.updateNodeGroupState(
                new NodeGroupChange(node.getId(), group.getId(), true));
        assertThat(result).isTrue();

        // Verify via getNodeGroupIncludedNodes
        final Set<String> included = nodeGroupDao.getNodeGroupIncludedNodes(group.getId());
        assertThat(included).containsExactly("includeMe");
    }

    @Test
    void testUpdateNodeGroupState_exclude() {
        final Node node = createNode("excludeMe");
        final NodeGroup group = createGroup("unlinkGroup", true);

        // Include then exclude
        nodeGroupDao.updateNodeGroupState(new NodeGroupChange(node.getId(), group.getId(), true));
        nodeGroupDao.updateNodeGroupState(new NodeGroupChange(node.getId(), group.getId(), false));

        final Set<String> included = nodeGroupDao.getNodeGroupIncludedNodes(group.getId());
        assertThat(included).isEmpty();
    }

    @Test
    void testUpdateNodeGroupState_includeIdempotent() {
        final Node node = createNode("idempotent");
        final NodeGroup group = createGroup("idempotentGroup", true);

        // Including twice should not fail (uses ON DUPLICATE KEY UPDATE)
        nodeGroupDao.updateNodeGroupState(new NodeGroupChange(node.getId(), group.getId(), true));
        nodeGroupDao.updateNodeGroupState(new NodeGroupChange(node.getId(), group.getId(), true));

        final Set<String> included = nodeGroupDao.getNodeGroupIncludedNodes(group.getId());
        assertThat(included).containsExactly("idempotent");
    }

    @Test
    void testUpdateNodeGroupState_excludeWhenNotIncluded() {
        final Node node = createNode("notIncluded");
        final NodeGroup group = createGroup("noLinkGroup", true);

        // Excluding when not included should return false (no rows affected)
        final boolean result = nodeGroupDao.updateNodeGroupState(
                new NodeGroupChange(node.getId(), group.getId(), false));
        assertThat(result).isFalse();
    }

    // ---- getNodeGroupIncludedNodes ----

    @Test
    void testGetNodeGroupIncludedNodes_empty() {
        final NodeGroup group = createGroup("emptyIncludes", true);
        final Set<String> included = nodeGroupDao.getNodeGroupIncludedNodes(group.getId());
        assertThat(included).isEmpty();
    }

    @Test
    void testGetNodeGroupIncludedNodes_multipleNodes() {
        final Node nodeX = createNode("nodeX");
        final Node nodeY = createNode("nodeY");
        final Node nodeZ = createNode("nodeZ");
        final NodeGroup group = createGroup("multiGroup", true);

        nodeGroupDao.updateNodeGroupState(new NodeGroupChange(nodeX.getId(), group.getId(), true));
        nodeGroupDao.updateNodeGroupState(new NodeGroupChange(nodeZ.getId(), group.getId(), true));

        final Set<String> included = nodeGroupDao.getNodeGroupIncludedNodes(group.getId());
        assertThat(included).containsExactlyInAnyOrder("nodeX", "nodeZ");
    }

    @Test
    void testGetNodeGroupIncludedNodes_isolatedBetweenGroups() {
        final Node node = createNode("sharedNode");
        final NodeGroup groupA = createGroup("groupA", true);
        final NodeGroup groupB = createGroup("groupB", true);

        // Link node to groupA only
        nodeGroupDao.updateNodeGroupState(new NodeGroupChange(node.getId(), groupA.getId(), true));

        assertThat(nodeGroupDao.getNodeGroupIncludedNodes(groupA.getId())).containsExactly("sharedNode");
        assertThat(nodeGroupDao.getNodeGroupIncludedNodes(groupB.getId())).isEmpty();
    }

    @Test
    void testGetNodeGroupIncludedNodes_returnsImmutableSet() {
        final NodeGroup group = createGroup("immutableGroup", true);
        final Set<String> included = nodeGroupDao.getNodeGroupIncludedNodes(group.getId());

        assertThatThrownBy(() -> included.add("shouldFail"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ---- Delete cascades links ----

    @Test
    void testDelete_cascadesLinks() {
        final Node node = createNode("cascadeNode");
        final NodeGroup group = createGroup("cascadeGroup", true);

        nodeGroupDao.updateNodeGroupState(new NodeGroupChange(node.getId(), group.getId(), true));
        assertThat(nodeGroupDao.getNodeGroupIncludedNodes(group.getId())).hasSize(1);

        // Deleting the group should also remove the links
        nodeGroupDao.delete(group.getId());
        // The node should still exist
        assertThat(nodeDao.getNode("cascadeNode")).isNotNull();
    }

    // ---- Helpers ----

    private NodeGroup createGroup(final String name, final boolean enabled) {
        return nodeGroupDao.create(
                NodeGroup.builder()
                        .name(name)
                        .enabled(enabled)
                        .stampAudit("test")
                        .build());
    }

    private Node createNode(final String name) {
        return nodeDao.tryCreate(
                Node.builder()
                        .name(name)
                        .priority(1)
                        .stampAudit("test")
                        .build());
    }
}
