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

package stroom.explorer.server;

import org.junit.Test;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.shared.ModelStringUtil;

import javax.annotation.Resource;
import java.util.UUID;

public class TestExplorerTree extends AbstractCoreIntegrationTest {
    @Resource
    private ExplorerTreeDao explorerTreeDao;
    @Resource
    private ExplorerTreeModel explorerTreeModel;

    @Test
    public void testCreateTree() throws Exception {
        ExplorerTreeNode root = explorerTreeDao.createRoot(newTreePojo("System"));
        addChildren(root, 0);



        long now = System.currentTimeMillis();
        explorerTreeModel.createModel();
        System.out.println(ModelStringUtil.formatDurationString(System.currentTimeMillis() - now));

        now = System.currentTimeMillis();
        explorerTreeModel.createModel2();
        System.out.println(ModelStringUtil.formatDurationString(System.currentTimeMillis() - now));

        //commitDbTransaction(session, "insert tree nodes");
//        return root.getId();
    }

    private void addChildren(final ExplorerTreeNode parent, final int depth) throws Exception {
        for (int i = 0; i < 10; i++) {
            ExplorerTreeNode a = explorerTreeDao.addChild(parent, newTreePojo(parent.getName() + "-" + i));
            if (depth < 1) {
                addChildren(a, depth + 1);
            }
        }
    }

//
//    protected ClosureTableTreeDao newDao(final DbSession session)	{
//        ClosureTableTreeDao dao =
//                new ClosureTableTreeDao(
//                        ExplorerTreeNode.class,
//                        ExplorerTreePath.class,
//                        false,
//                        session);
//
//        dao.setRemoveReferencedNodes(true);
//
////        if (isTestCopy() == false)
////            dao.setUniqueTreeConstraint(newUniqueWholeTreeConstraintImpl());
//
//        return dao;
//    }

    private ExplorerTreeNode newTreePojo(final String name) {
        final ExplorerTreeNode explorerTreeNode = new ExplorerTreeNode();
        explorerTreeNode.setName(name);
        explorerTreeNode.setType("test");
        explorerTreeNode.setUuid(UUID.randomUUID().toString());
        return explorerTreeNode;
    }
}
