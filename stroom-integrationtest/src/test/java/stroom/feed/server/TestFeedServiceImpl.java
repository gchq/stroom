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

package stroom.feed.server;

import org.junit.Assert;
import org.junit.Test;
import stroom.AbstractCoreIntegrationTest;
import stroom.CommonTestScenarioCreator;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.Folder;
import stroom.entity.shared.FolderService;
import stroom.entity.shared.PermissionInheritance;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FeedService;
import stroom.feed.shared.FindFeedCriteria;
import stroom.pipeline.shared.FindPipelineEntityCriteria;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.PipelineEntityService;
import stroom.streamstore.shared.StreamType;
import stroom.util.test.FileSystemTestUtil;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TestFeedServiceImpl extends AbstractCoreIntegrationTest {
    private static final int TEST_SIZE = 10;
    private static final int TEST_PAGE = 2;

    @Resource
    private FeedService feedService;
    @Resource
    private PipelineEntityService pipelineEntityService;
    @Resource
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Resource
    private FolderService folderService;

    /**
     * Test.
     */
    @Test
    public void test1() {
        final String feedName = FileSystemTestUtil.getUniqueTestString();
        Feed fd = feedService.create(commonTestScenarioCreator.getTestFolder(), feedName);
        fd = feedService.save(fd);
        fd = feedService.save(fd);

        Assert.assertNotNull(feedService.loadByName(feedName));
        Assert.assertNull(feedService.loadByName(feedName + "void"));

        final Feed test1 = feedService.loadByName(feedName);
        Assert.assertNotNull(test1.getCreateTime());
        Assert.assertNotNull(test1.getUpdateTime());

        final HashSet<String> fetchSet = new HashSet<>();
        fetchSet.add(StreamType.ENTITY_TYPE);
        final Feed test2 = feedService.loadById(fd.getId(), fetchSet);
        Assert.assertNotNull(test2.getCreateTime());
        Assert.assertNotNull(test2.getUpdateTime());
        Assert.assertNotNull(test2.getStreamType().getDisplayValue());

        feedService.delete(fd);

        Assert.assertNull(feedService.loadByName(feedName));
    }

    /**
     * Test.
     */
    @Test
    public void testSaveAs() {
        final String feedName = FileSystemTestUtil.getUniqueTestString();
        Feed fd = feedService.create(commonTestScenarioCreator.getTestFolder(), feedName);

        fd = feedService.copy(fd, DocRefUtil.create(fd.getFolder()), fd.getName() + "COPY", PermissionInheritance.INHERIT);

        feedService.save(fd);
    }

    /**
     * Added this test to ensure that deletion works on the database correctly
     * and cascades to the join table. Previously this was broken as the
     * deletion was violating the integrity constraint placed on the join table.
     */
    @Test
    public void test2() {
        Feed rfd = feedService.create(commonTestScenarioCreator.getTestFolder(), "REF_FEED_1");
        rfd.setDescription("Junit");
        rfd.setReference(true);
        rfd = feedService.save(rfd);

        final Set<Feed> refFeed = new HashSet<>();
        refFeed.add(rfd);

        Feed fd = feedService.create(commonTestScenarioCreator.getTestFolder(), "EVT_FEED_1");
        fd.setDescription("Junit");
        fd = feedService.save(fd);

        // Find just reference feed.
        FindFeedCriteria criteria = new FindFeedCriteria();
        criteria.setReference(true);
        List<Feed> list = feedService.find(criteria);
        Assert.assertEquals(1, list.size());
        Assert.assertEquals(rfd, list.get(0));

        // Find just event feed.
        criteria = new FindFeedCriteria();
        criteria.setReference(false);
        list = feedService.find(criteria);
        Assert.assertEquals(1, list.size());
        Assert.assertEquals(fd, list.get(0));

        // Find both feeds.
        criteria = new FindFeedCriteria();
        list = feedService.find(criteria);
        Assert.assertEquals(2, list.size());

        final List<String> sortList = new ArrayList<>();
        sortList.add(FindFeedCriteria.FIELD_NAME);
        sortList.add(FindFeedCriteria.FIELD_FOLDER);
        sortList.add(FindFeedCriteria.FIELD_TYPE);
        sortList.add(FindFeedCriteria.FIELD_CLASSIFICATION);

        // Test order by.
        for (final String field : sortList) {
            criteria = new FindFeedCriteria();
            criteria.setSort(field);
            list = feedService.find(criteria);
            Assert.assertEquals(2, list.size());
        }

        criteria = new FindFeedCriteria();
        criteria.getFeedIdSet().add(rfd);
        list = feedService.find(criteria);
        Assert.assertEquals(1, list.size());

    }

    /**
     * Test some paging stuff.
     */
    @Test
    public void testPaging() {
        for (int i = 0; i < TEST_SIZE; i++) {
            final Feed rfd = feedService.create(commonTestScenarioCreator.getTestFolder(), "REF_FEED_" + i);
            rfd.setDescription("Junit");
            feedService.save(rfd);
        }

        final FindFeedCriteria criteria = new FindFeedCriteria();
        criteria.obtainPageRequest().setLength(TEST_PAGE);
        criteria.obtainPageRequest().setOffset(Long.valueOf(0));

        BaseResultList<Feed> list = feedService.find(criteria);
        Assert.assertEquals(TEST_PAGE, list.size());
        Assert.assertTrue(list.getPageResponse().isMore());

        criteria.getPageRequest()
                .setOffset(criteria.getPageRequest().getOffset() + criteria.getPageRequest().getLength());
        list = feedService.find(criteria);
        Assert.assertEquals(TEST_PAGE, list.size());
        Assert.assertEquals(Long.valueOf(TEST_PAGE), list.getPageResponse().getOffset());
        Assert.assertTrue(list.getPageResponse().isMore());
    }

    /**
     * Check the relationships.
     */
    @Test
    public void testNonLazyLoad() {
        try {
            final Feed fd1 = feedService.create(commonTestScenarioCreator.getTestFolder(), "K1_12345");
            fd1.setDescription("Junit");
            feedService.save(fd1);

            final Feed fd2 = feedService.create(commonTestScenarioCreator.getTestFolder(), "K2_12345");
            fd2.setDescription("Junit");
            feedService.save(fd2);

            // check for eager load up
            final Feed dbFd = feedService.loadByName("K2_12345");

            Assert.assertNotNull(dbFd);
            // Assert.assertNotNull(dbFd.getFeed());
            // Assert.assertNotNull(dbFd.getReferenceFeed().iterator().next());
        } catch (final Exception ex) {
            ex.printStackTrace();
            Assert.fail(ex.getMessage());
        }
    }

    /**
     * Check the relationships.
     */
    @Test
    public void testParentJPAStuff() {
        Folder folder = folderService.create(null, "JUNIT");
        folder = folderService.save(folder);

        PipelineEntity translation1 = pipelineEntityService.create(DocRefUtil.create(folder), "JUNIT");
        translation1.setDescription("Junit");
        translation1 = pipelineEntityService.save(translation1);

        final FindPipelineEntityCriteria findTranslationCriteria = new FindPipelineEntityCriteria();
        findTranslationCriteria.getName().setString("JUNIT");
        translation1 = pipelineEntityService.find(findTranslationCriteria).getFirst();

        Assert.assertNotNull(translation1.getFolder().getId());
    }
}
