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

import stroom.feed.FeedService;
import stroom.feed.shared.Feed;

import javax.inject.Inject;
import javax.inject.Named;


// @Transactional
class EntityServiceImplTestTransactionHelper {
    private final FeedService feedService;
    private final FeedService cachedFeedService;

    private Feed feed;

    @Inject
    EntityServiceImplTestTransactionHelper(final FeedService feedService,
                                           @Named("cachedFeedService") final FeedService cachedFeedService) {
        this.feedService = feedService;
        this.cachedFeedService = cachedFeedService;
    }

    public void init() {
        feed = feedService.create("FEED_" + System.currentTimeMillis());
    }

    // @Transactional
    public void test1() {
        Feed feed1 = feedService.loadById(feed.getId());
        Feed feed2 = cachedFeedService.loadById(feed.getId());

        Assert.assertTrue("This method is transactional but loadById should start a new one", feed1 != feed2);
        Assert.assertEquals(feed1, feed2);
    }

    public void test2() {
        Feed feed1 = cachedFeedService.loadById(feed.getId());
        Feed feed2 = cachedFeedService.loadById(feed.getId());

        Assert.assertTrue(feed1 == feed2);
        Assert.assertEquals(feed1, feed2);
    }
}
