/*
 * Copyright 2017 Crown Copyright
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

package stroom.streamstore;

import org.junit.Assert;
import org.junit.Test;
import stroom.streamstore.shared.Feed;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.test.FileSystemTestUtil;

import javax.inject.Inject;

public class TestFeedServiceImpl extends AbstractCoreIntegrationTest {
    private static final int TEST_SIZE = 10;
    private static final int TEST_PAGE = 2;

    @Inject
    private FeedService feedService;

    /**
     * Test.
     */
    @Test
    public void test1() {
        final String feedName = FileSystemTestUtil.getUniqueTestString();
        Feed fd = feedService.getOrCreate(feedName);
//        fd = feedService.save(fd);
//        fd = feedService.save(fd);

        Assert.assertNotNull(feedService.get(feedName));
        Assert.assertNull(feedService.get(feedName + "void"));

        final Feed test1 = feedService.get(feedName);
        Assert.assertNotNull(test1.getCreateTime());
        Assert.assertNotNull(test1.getUpdateTime());

//        final HashSet<String> fetchSet = new HashSet<>();
//        fetchSet.add(StreamType.ENTITY_TYPE);
//        final Feed test2 = feedService.loadById(fd.getId(), fetchSet);
//        Assert.assertNotNull(test2.getCreateTime());
//        Assert.assertNotNull(test2.getUpdateTime());
////        Assert.assertNotNull(test2.getStreamType().getDisplayValue());
//
//        feedService.delete(fd);
//
//        Assert.assertNull(feedService.get(feedName));
    }

//    /**
//     * Added this test to ensure that deletion works on the database correctly
//     * and cascades to the join table. Previously this was broken as the
//     * deletion was violating the integrity constraint placed on the join table.
//     */
//    @Test
//    public void test2() {
//        Feed rfd = feedService.create("REF_FEED_1");
//        rfd.setDescription("Junit");
//        rfd.setReference(true);
//        rfd = feedService.save(rfd);
//
//        final Set<Feed> refFeed = new HashSet<>();
//        refFeed.add(rfd);
//
//        Feed fd = feedService.create("EVT_FEED_1");
//        fd.setDescription("Junit");
//        fd = feedService.save(fd);
//
//        // Find just reference feed.
//        FindFeedCriteria criteria = new FindFeedCriteria();
//        criteria.setReference(true);
//        List<Feed> list = feedService.find(criteria);
//        Assert.assertEquals(1, list.size());
//        Assert.assertEquals(rfd, list.get(0));
//
//        // Find just event feed.
//        criteria = new FindFeedCriteria();
//        criteria.setReference(false);
//        list = feedService.find(criteria);
//        Assert.assertEquals(1, list.size());
//        Assert.assertEquals(fd, list.get(0));
//
//        // Find both feeds.
//        criteria = new FindFeedCriteria();
//        list = feedService.find(criteria);
//        Assert.assertEquals(2, list.size());
//
//        final List<String> sortList = new ArrayList<>();
//        sortList.add(FindFeedCriteria.FIELD_NAME);
//        sortList.add(FindFeedCriteria.FIELD_TYPE);
//        sortList.add(FindFeedCriteria.FIELD_CLASSIFICATION);
//
//        // Test order by.
//        for (final String field : sortList) {
//            criteria = new FindFeedCriteria();
//            criteria.setSort(field);
//            list = feedService.find(criteria);
//            Assert.assertEquals(2, list.size());
//        }
//
//        criteria = new FindFeedCriteria();
//        criteria.getFeedIdSet().add(rfd);
//        list = feedService.find(criteria);
//        Assert.assertEquals(1, list.size());
//
//    }
//
//    /**
//     * Test some paging stuff.
//     */
//    @Test
//    public void testPaging() {
//        for (int i = 0; i < TEST_SIZE; i++) {
//            final Feed rfd = feedService.create("REF_FEED_" + i);
//            rfd.setDescription("Junit");
//            feedService.save(rfd);
//        }
//
//        final FindFeedCriteria criteria = new FindFeedCriteria();
//        criteria.obtainPageRequest().setLength(TEST_PAGE);
//        criteria.obtainPageRequest().setOffset(Long.valueOf(0));
//
//        BaseResultList<Feed> list = feedService.find(criteria);
//        Assert.assertEquals(TEST_PAGE, list.size());
//        Assert.assertEquals(0L, list.getPageResponse().getOffset().intValue());
//        Assert.assertEquals(TEST_PAGE, list.getPageResponse().getLength().intValue());
//        Assert.assertEquals(TEST_SIZE, list.getPageResponse().getTotal().intValue());
//        Assert.assertTrue(list.getPageResponse().isExact());
//
//        criteria.getPageRequest()
//                .setOffset(criteria.getPageRequest().getOffset() + criteria.getPageRequest().getLength());
//        list = feedService.find(criteria);
//        Assert.assertEquals(TEST_PAGE, list.size());
//        Assert.assertEquals(Long.valueOf(TEST_PAGE), list.getPageResponse().getOffset());
//        Assert.assertTrue(list.getPageResponse().isExact());
//    }
//
//    /**
//     * Check the relationships.
//     */
//    @Test
//    public void testNonLazyLoad() {
//        try {
//            final Feed fd1 = feedService.create("K1_12345");
//            fd1.setDescription("Junit");
//            feedService.save(fd1);
//
//            final Feed fd2 = feedService.create("K2_12345");
//            fd2.setDescription("Junit");
//            feedService.save(fd2);
//
//            // check for eager load up
//            final Feed dbFd = feedService.loadByName("K2_12345");
//
//            Assert.assertNotNull(dbFd);
//            // Assert.assertNotNull(dbFd.getFeed());
//            // Assert.assertNotNull(dbFd.getReferenceFeed().iterator().next());
//        } catch (final RuntimeException e) {
//            e.printStackTrace();
//            Assert.fail(e.getMessage());
//        }
//    }
}
