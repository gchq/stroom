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
 *
 */

package stroom.entity;

import org.junit.Assert;

import stroom.streamstore.FdService;
import stroom.feed.shared.FeedDoc;

import javax.inject.Inject;
import javax.inject.Named;


// @Transactional
class EntityServiceImplTestTransactionHelper {
    private final FdService feedService;
    private final FdService cachedFeedService;

    private FeedDoc feed;

    @Inject
    EntityServiceImplTestTransactionHelper(final FdService feedService,
                                           @Named("cachedFeedService") final FdService cachedFeedService) {
        this.feedService = feedService;
        this.cachedFeedService = cachedFeedService;
    }

    public void init() {
        feed = feedService.create("FEED_" + System.currentTimeMillis());
    }

    // @Transactional
    public void test1() {
        FeedDoc feed1 = feedService.loadById(feed.getId());
        FeedDoc feed2 = cachedFeedService.loadById(feed.getId());

        Assert.assertTrue("This method is transactional but loadById should start a new one", feed1 != feed2);
        Assert.assertEquals(feed1, feed2);
    }

    public void test2() {
        FeedDoc feed1 = cachedFeedService.loadById(feed.getId());
        FeedDoc feed2 = cachedFeedService.loadById(feed.getId());

        Assert.assertTrue(feed1 == feed2);
        Assert.assertEquals(feed1, feed2);
    }
}
