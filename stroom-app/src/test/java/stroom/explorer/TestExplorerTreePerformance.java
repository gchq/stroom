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

package stroom.explorer;

import stroom.explorer.api.ExplorerActionHandler;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.impl.ExplorerTreeDao;
import stroom.explorer.impl.ExplorerTreeNode;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.FetchExplorerNodeResult;
import stroom.explorer.shared.FetchExplorerNodesRequest;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserService;
import stroom.security.impl.DocumentPermissionServiceImpl;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.User;
import stroom.svg.shared.SvgImage;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled
        // manual testing only
class TestExplorerTreePerformance extends AbstractCoreIntegrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestExplorerTreePerformance.class);
    private static final int MAX_CHILDREN = 5;
    private static final int MAX_TREE_DEPTH = 2;
    public static final String TYPE_TEST = "test";

    @Inject
    private ExplorerTreeDao explorerTreeDao;
    @Inject
    private ExplorerService explorerService;
    @Inject
    private UserService userService;
    @Inject
    private DocumentPermissionServiceImpl documentPermissionService;
    @Inject
    private SecurityContext securityContext;
    @Inject
    private Set<ExplorerActionHandler> explorerActionHandlers;

    private final Map<String, SvgImage> typeToIconMap = new HashMap<>();

    @Test
    void testLargeTreePerformance() {
        securityContext.asProcessingUser(() -> {
            final FetchExplorerNodesRequest findExplorerNodeCriteria = new FetchExplorerNodesRequest(
                    new HashSet<>(),
                    new HashSet<>(),
                    new ExplorerTreeFilter(
                            Collections.singleton("test"),
                            Collections.singleton("test"),
                            null,
                            null,
                            Collections.singleton(DocumentPermission.VIEW),
                            null,
                            false,
                            null),
                    Integer.MAX_VALUE,
                    new HashSet<>(),
                    true);

            LOGGER.logDurationIfInfoEnabled(() -> {
                explorerService.clear();
                final FetchExplorerNodeResult result = explorerService.getData(findExplorerNodeCriteria);
                assertThat(result.getRootNodes()).hasSize(2);
                assertThat(result.getRootNodes().get(0).getDocRef())
                        .isEqualTo(ExplorerConstants.FAVOURITES_DOC_REF);
                assertThat(result.getRootNodes().get(1).getDocRef())
                        .isEqualTo(ExplorerConstants.SYSTEM_DOC_REF);
            }, "Checked empty tree");

            final int count = (int) Math.pow(MAX_CHILDREN, MAX_TREE_DEPTH) + MAX_CHILDREN + 1;
            LOGGER.info(() -> "Creating " + count + " tree nodes");
            LOGGER.logDurationIfInfoEnabled(() -> {
                final ExplorerTreeNode root = explorerTreeDao.createRoot(newTreeNode("test_node"));
                addChildren(root, 1, MAX_TREE_DEPTH);
            }, "Created " + count + " tree nodes");

            LOGGER.logDurationIfInfoEnabled(() -> {
                // Check create model.
                explorerTreeDao.createModel(
                        0,
                        System.currentTimeMillis());
            }, "Create model");

            final ExplorerNode lastChild = LOGGER.logDurationIfInfoEnabled(
                    () ->
                            expandTree(findExplorerNodeCriteria, count),
                    "Expand all");

            final User user = userService.getOrCreateUser("testuser");
            final User userGroup = userService.getOrCreateUserGroup("testusergroup");
            userService.addUserToGroup(user.asRef(), userGroup.asRef());
            documentPermissionService.setPermission(
                    lastChild.getDocRef(),
                    user.asRef(),
                    DocumentPermission.VIEW);
            documentPermissionService.setPermission(
                    lastChild.getDocRef(),
                    userGroup.asRef(),
                    DocumentPermission.VIEW);

            LOGGER.logDurationIfInfoEnabled(() -> {
                securityContext.asUser(
                        user.asRef(),
                        () -> {
                            // See what we get back with a user with limited permissions.
                            expandTree(findExplorerNodeCriteria, 3);
                        });
            }, "Expand all as user with empty cache");

            LOGGER.logDurationIfInfoEnabled(() -> {
                securityContext.asUser(user.asRef(), () -> {
                    // See what we get back with a user with limited permissions.
                    expandTree(findExplorerNodeCriteria, 3);
                });
            }, "Expand all as user with full cache");
        });
    }

    private ExplorerNode expandTree(final FetchExplorerNodesRequest findExplorerNodeCriteria,
                                    final int expected) {
        final AtomicInteger count = new AtomicInteger();
        final AtomicReference<ExplorerNode> lastChild = new AtomicReference<>();

        explorerService.clear();
        final FetchExplorerNodeResult result = explorerService.getData(findExplorerNodeCriteria);
        if (expected < 100) {
            LOGGER.debug("tree:\n{}", result.dumpTree());
        }

        count(result.getRootNodes(), count, lastChild);

        // Add one to expected to account for empty favourites & system roots
        assertThat(count.get())
                .isEqualTo(expected + 2);

        return lastChild.get();
    }

    private void count(final List<ExplorerNode> parents,
                       final AtomicInteger count,
                       final AtomicReference<ExplorerNode> lastChild) {
        if (parents != null) {
            for (final ExplorerNode parent : parents) {
                lastChild.set(parent);
                count.incrementAndGet();
                final List<ExplorerNode> children = parent.getChildren();
                count(children, count, lastChild);
            }
        }
    }

//    private ExplorerNode openAll(final ExplorerNode parent,
//                                 final FetchExplorerNodeResult result,
//                                 final FindExplorerNodeCriteria findExplorerNodeCriteria) {
//        ExplorerNode lastChild = null;
//
//        final List<ExplorerNode> children = parent.getChildren();
//        if (children != null && children.size() > 0) {
//            for (final ExplorerNode child : children) {
//                findExplorerNodeCriteria.getOpenItems().add(child.getUniqueKey());
//                lastChild = openAll(child, result, findExplorerNodeCriteria);
//                if (lastChild == null) {
//                    lastChild = child;
//                }
//            }
//        }
//
//        return lastChild;
//    }

    private void addChildren(final ExplorerTreeNode parent, final int depth, final int maxDepth) {
        for (int i = 1; i <= MAX_CHILDREN; i++) {
            final ExplorerTreeNode child = explorerTreeDao.addChild(parent, newTreeNode(parent.getName() + "-" + i));
            if (depth < maxDepth) {
                addChildren(child, depth + 1, maxDepth);
            }
        }
    }

    private ExplorerTreeNode newTreeNode(final String name) {
        final ExplorerTreeNode explorerTreeNode = new ExplorerTreeNode();
        explorerTreeNode.setName(name);
        explorerTreeNode.setType(TYPE_TEST);
        explorerTreeNode.setUuid(UUID.randomUUID().toString());
        return explorerTreeNode;
    }
}
