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

package stroom.feed;


import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.common.util.test.FileSystemTestUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestFeedStoreImpl extends AbstractCoreIntegrationTest {

    private static final int TEST_SIZE = 10;
    private static final int TEST_PAGE = 2;

    @Inject
    private FeedStore feedStore;
//    @Inject
//    private PipelineStore pipelineStore;
//    @Inject
//    private CommonTestScenarioCreator commonTestScenarioCreator;

    /**
     * Test.
     */
    @Test
    void test1() {
        final String feedName = FileSystemTestUtil.getUniqueTestString();
        final DocRef feedRef = feedStore.createDocument(feedName);
        FeedDoc feedDoc = feedStore.readDocument(feedRef);
        feedDoc.setStreamType(StreamTypeNames.RAW_EVENTS);
        feedDoc = feedStore.writeDocument(feedDoc);
        feedStore.writeDocument(feedDoc);

        assertThat(feedStore.findByName(feedName).size()).isEqualTo(1);
        assertThat(feedStore.findByName(feedName + "void").size()).isEqualTo(0);

        final List<DocRef> list = feedStore.findByName(feedName);
        assertThat(list.size()).isEqualTo(1);
        feedDoc = feedStore.readDocument(list.getFirst());
        assertThat(feedDoc.getCreateTimeMs()).isNotNull();
        assertThat(feedDoc.getUpdateTimeMs()).isNotNull();
        assertThat(feedDoc.getStreamType()).isNotNull();

        feedStore.deleteDocument(list.getFirst());

        assertThat(feedStore.findByName(feedName).size()).isEqualTo(0);
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
//        assertThat(list.size()).isEqualTo(1);
//        assertThat(list.get(0)).isEqualTo(rfd);
//
//        // Find just event feed.
//        criteria = new FindFeedCriteria();
//        criteria.setReference(false);
//        list = feedService.find(criteria);
//        assertThat(list.size()).isEqualTo(1);
//        assertThat(list.get(0)).isEqualTo(fd);
//
//        // Find both feeds.
//        criteria = new FindFeedCriteria();
//        list = feedService.find(criteria);
//        assertThat(list.size()).isEqualTo(2);
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
//            assertThat(list.size()).isEqualTo(2);
//        }
//
//        criteria = new FindFeedCriteria();
//        criteria.getFeedIdSet().add(rfd);
//        list = feedService.find(criteria);
//        assertThat(list.size()).isEqualTo(1);
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
//        assertThat(list.size()).isEqualTo(TEST_PAGE);
//        assertThat(list.getPageResponse().getOffset()).isEqualTo(0L);
//        assertThat(list.getPageResponse().getLength()).isEqualTo(TEST_PAGE);
//        assertThat(list.getPageResponse().getTotal()).isEqualTo(TEST_SIZE);
//        assertThat(list.getPageResponse().isExact()).isTrue();
//
//        criteria.getPageRequest()
//                .setOffset(criteria.getPageRequest().getOffset() + criteria.getPageRequest().getLength());
//        list = feedService.find(criteria);
//        assertThat(list.size()).isEqualTo(TEST_PAGE);
//        assertThat(list.getPageResponse().getOffset()).isEqualTo(Long.valueOf(TEST_PAGE));
//        assertThat(list.getPageResponse().isExact()).isTrue();
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
//            assertThat(dbFd).isNotNull();
//            // assertThat(dbFd.getFeed()).isNotNull();
//            // assertThat(dbFd.getReferenceFeed().iterator().next()).isNotNull();
//        } catch (final RuntimeException e) {
//            e.printStackTrace();
//            fail(e.getMessage());
//        }
//    }
}
