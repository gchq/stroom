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

import stroom.AbstractCoreIntegrationTest;
import stroom.entity.server.util.StroomDatabaseInfo;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.FindFolderCriteria;
import stroom.entity.shared.Folder;
import stroom.entity.shared.FolderService;
import stroom.entity.shared.PermissionInheritance;
import stroom.util.test.FileSystemTestUtil;
import org.junit.Assert;
import org.junit.Test;

import javax.annotation.Resource;

public class TestFolderServiceImpl extends AbstractCoreIntegrationTest {
    @Resource
    private FolderService folderService;
    @Resource
    private StroomDatabaseInfo stroomDatabaseInfo;

    @Test
    public void testSimple() {
        final Folder root1 = folderService.create(null, FileSystemTestUtil.getUniqueTestString());
        final Folder root2 = folderService.create(null, FileSystemTestUtil.getUniqueTestString());

        final Folder root1child1 = folderService.create(DocRef.create(root1), FileSystemTestUtil.getUniqueTestString());
        final Folder root1child1child1 = folderService.create(DocRef.create(root1child1), FileSystemTestUtil.getUniqueTestString());

        FindFolderCriteria findFolderCriteria = new FindFolderCriteria();
        findFolderCriteria.getFolderIdSet().setMatchNull(Boolean.TRUE);

        BaseResultList<Folder> list = folderService.find(findFolderCriteria);
        Assert.assertTrue(list.contains(root1));
        Assert.assertTrue(list.contains(root2));
        Assert.assertFalse(list.contains(root1child1));

        findFolderCriteria = new FindFolderCriteria();
        findFolderCriteria.setSelf(true);
        findFolderCriteria.getFolderIdSet().add(root1);
        list = folderService.find(findFolderCriteria);
        Assert.assertTrue(list.contains(root1));
        Assert.assertFalse(list.contains(root2));
        Assert.assertFalse(list.contains(root1child1));
        Assert.assertFalse(list.contains(root1child1child1));

        findFolderCriteria = new FindFolderCriteria();
        findFolderCriteria.getFolderIdSet().setDeep(true);
        findFolderCriteria.getFolderIdSet().add(root1);
        list = folderService.find(findFolderCriteria);
        Assert.assertFalse(list.contains(root1));
        Assert.assertTrue(list.contains(root1child1));
        Assert.assertTrue(list.contains(root1child1child1));
    }

    @Test
    public void testVersionRolling() {
        // This only works in mysql :(
        if (stroomDatabaseInfo.isMysql()) {
            // commonTestControl.deleteAll();

            Folder group = folderService.create(null, "Testing");

            for (int i = 0; i < 130; i++) {
                group.setName("Testing" + group.getVersion());
                group = folderService.save(group);
            }

            Assert.assertTrue("Expected version to roll", group.getVersion() < 0);
        }
    }

    @Test
    public void testVersionCheck() {
        // commonTestControl.deleteAll();
        Folder dbGroupV1 = folderService.create(null, "Testing");
        Folder dbGroupV2 = folderService.copy(dbGroupV1, null, "TestingV2", PermissionInheritance.DESTINATION);
        Folder dbGroupV3 = folderService.copy(dbGroupV2, null, "TestingV3", PermissionInheritance.DESTINATION);

        // Make sure we can't save a stale object.
        try {
            dbGroupV2.setName("TestingVersionCheck");
            folderService.save(dbGroupV2);
            dbGroupV2.setName("TestingVersionCheck");
            folderService.save(dbGroupV2);
            Assert.fail("Expected Version Check");
        } catch (final Exception ex) {
        }

        // Make sure we can't delete a stale object.
        try {
            dbGroupV2.setName("TestingVersionCheck");
            folderService.delete(dbGroupV2);
            Assert.fail("Expected Version Check");
        } catch (final Exception ex) {
        }

        // Try a final load save and delete.
        dbGroupV2 = folderService.load(dbGroupV2);
        dbGroupV2 = folderService.save(dbGroupV2);
        folderService.delete(dbGroupV2);
    }
}
