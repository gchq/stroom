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


import stroom.explorer.impl.ExplorerTreeDao;
import stroom.explorer.impl.ExplorerTreeNode;
import stroom.test.AbstractCoreIntegrationTest;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class TestExplorerTree extends AbstractCoreIntegrationTest {

    @Inject
    private ExplorerTreeDao explorerTreeDao;

    @Test
    void testCreateTree() {
        final ExplorerTreeNode root = explorerTreeDao.createRoot(newTreePojo("System"));
        final ExplorerTreeNode a = explorerTreeDao.addChild(root, newTreePojo("A"));
        final ExplorerTreeNode b = explorerTreeDao.addChild(root, newTreePojo("B"));
        final ExplorerTreeNode c = explorerTreeDao.addChild(root, newTreePojo("C"));
        explorerTreeDao.addChild(b, newTreePojo("B1"));
        explorerTreeDao.addChild(b, newTreePojo("B2"));
        explorerTreeDao.addChild(a, newTreePojo("A1"));
        final ExplorerTreeNode c1 = explorerTreeDao.addChild(c, newTreePojo("C1"));
        explorerTreeDao.addChild(c1, newTreePojo("C11"));

        // Check create model.
        explorerTreeDao.createModel(0, System.currentTimeMillis());
    }

    private ExplorerTreeNode newTreePojo(final String name) {
        final ExplorerTreeNode explorerTreeNode = new ExplorerTreeNode();
        explorerTreeNode.setName(name);
        explorerTreeNode.setType("test");
        explorerTreeNode.setUuid(UUID.randomUUID().toString());
        return explorerTreeNode;
    }
}
