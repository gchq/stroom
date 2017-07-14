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

package stroom.security.server;

import org.junit.Assert;
import org.junit.Test;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.Folder;
import stroom.entity.shared.FolderService;
import stroom.importexport.server.EntityPathResolver;
import stroom.util.test.FileSystemTestUtil;

import javax.annotation.Resource;

public class TestFolderEntityManager extends AbstractCoreIntegrationTest {
    @Resource
    private FolderService folderService;
    @Resource
    private EntityPathResolver folderEntityManager;

    @Test
    public void test1() {
        final String l1Name = "L1_" + FileSystemTestUtil.getUniqueTestString();
        final String l2Name = "L2_" + FileSystemTestUtil.getUniqueTestString();
        final String l3Name = "L3_" + FileSystemTestUtil.getUniqueTestString();

        final Folder l1Node = folderService.create(null, l1Name);
        final Folder l2Node = folderService.create(DocRefUtil.create(l1Node), l2Name);
        final Folder l3Node = folderService.create(DocRefUtil.create(l2Node), l3Name);

        // Simple name's
        Assert.assertEquals(l1Node, folderEntityManager.getEntity(Folder.ENTITY_TYPE, null, l1Name, null));
        Assert.assertEquals(l2Node, folderEntityManager.getEntity(Folder.ENTITY_TYPE, l1Node, l2Name, null));
        Assert.assertEquals(l3Node, folderEntityManager.getEntity(Folder.ENTITY_TYPE, l2Node, l3Name, null));

        // Full paths
        Assert.assertEquals(l1Node, folderEntityManager.getEntity(Folder.ENTITY_TYPE, null, "/" + l1Name, null));
        Assert.assertEquals(l2Node,
                folderEntityManager.getEntity(Folder.ENTITY_TYPE, null, "/" + l1Name + "/" + l2Name, null));
        Assert.assertEquals(l3Node, folderEntityManager.getEntity(Folder.ENTITY_TYPE, null,
                "/" + l1Name + "/" + l2Name + "/" + l3Name, null));

        // Relative Paths
        Assert.assertEquals(l3Node,
                folderEntityManager.getEntity(Folder.ENTITY_TYPE, l1Node, l2Name + "/" + l3Name, null));

        Assert.assertEquals("/" + l1Name, folderEntityManager.getEntityPath(Folder.ENTITY_TYPE, null, l1Node));
        Assert.assertEquals("/" + l1Name + "/" + l2Name + "/" + l3Name,
                folderEntityManager.getEntityPath(Folder.ENTITY_TYPE, null, l3Node));

        Assert.assertEquals(l2Name + "/" + l3Name,
                folderEntityManager.getEntityPath(Folder.ENTITY_TYPE, l1Node, l3Node));
        Assert.assertEquals(l3Name, folderEntityManager.getEntityPath(Folder.ENTITY_TYPE, l2Node, l3Node));

        Assert.assertNull(folderEntityManager.getEntity(Folder.ENTITY_TYPE, l1Node, l3Name, null));
        Assert.assertNull(folderEntityManager.getEntity(Folder.ENTITY_TYPE, l2Node, l2Name, null));
    }
}
