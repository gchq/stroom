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

import java.lang.reflect.Proxy;
import java.util.HashSet;

import stroom.util.test.StroomUnitTest;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import stroom.feed.shared.Feed;
import stroom.streamstore.shared.Stream;
import stroom.util.test.StroomJUnit4ClassRunner;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestBaseEntityDeProxyProcessor extends StroomUnitTest {
    public static class OurSet<T> extends HashSet<T> {
        private static final long serialVersionUID = -6963635707336015922L;
    }

    public static class DummyFeed extends Feed implements HibernateProxy {
        private static final long serialVersionUID = -338271738308074875L;

        @Override
        public LazyInitializer getHibernateLazyInitializer() {
            final Object proxy = Proxy.newProxyInstance(this.getClass().getClassLoader(),
                    new Class<?>[] { LazyInitializer.class }, (proxy1, method, args) -> {
                        if (method.getName().equals("getEntityName")) {
                            return Feed.class.getName();
                        }
                        return null;
                    });
            return (LazyInitializer) proxy;
        }

        @Override
        public Object writeReplace() {
            return null;
        }
    }

    @Test
    public void testDeProxy() {
        final DummyFeed dummyFeed = new DummyFeed();
        dummyFeed.setId(Long.valueOf(100));
        final Stream stream = Stream.createStream(null, dummyFeed, null);

        final Stream deproxy = (Stream) (new BaseEntityDeProxyProcessor(true).process(stream));

        Assert.assertTrue(deproxy.getFeed().getClass().equals(Feed.class));
        Assert.assertEquals(100L, deproxy.getFeed().getId());
    }
}
