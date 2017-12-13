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

package stroom.entity.server.util;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.feed.shared.Feed;
import stroom.streamstore.shared.StreamType;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestBaseEntityBeanWrapper extends StroomUnitTest {
    @Test
    public void testSimple() {
        final Feed eventFeed = new Feed();
        // eventFeed.setFeed(new HashSet<Feed>());
        // eventFeed.getFeed().add(new Feed());
        final BaseEntityBeanWrapper wrapper = new BaseEntityBeanWrapper(eventFeed);

        // Assert.assertTrue(wrapper.isPropertyBaseEntitySet("referenceFeed"));
        Assert.assertTrue(wrapper.isPropertyBaseEntity("streamType"));

        // Assert.assertEquals(1, eventFeed.getReferenceFeed().size());
        // wrapper.clearPropertySet("referenceFeed");
        // Assert.assertEquals(0, eventFeed.getReferenceFeed().size());
        // wrapper.addToPropertySet("referenceFeed", new Feed());
        // wrapper.addToPropertySet("referenceFeed", new Feed());
        // wrapper.addToPropertySet("referenceFeed", new Feed());

        // Assert.assertEquals(3, eventFeed.getReferenceFeed().size());

        // Assert.assertEquals(Feed.class,
        // wrapper.getPropertyBaseEntityType("referenceFeed"));
        Assert.assertEquals(StreamType.class, wrapper.getPropertyBaseEntityType("streamType"));

    }
}
