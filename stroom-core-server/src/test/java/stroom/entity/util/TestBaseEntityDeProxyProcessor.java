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

package stroom.entity.util;

import org.junit.Ignore;
import org.junit.runner.RunWith;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

@RunWith(StroomJUnit4ClassRunner.class)
@Ignore
public class TestBaseEntityDeProxyProcessor extends StroomUnitTest {
//    @Test
//    public void testDeProxy() {
//        final DummyStreamType streamType = new DummyStreamType();
//        streamType.setId(100L);
//        final StreamEntity stream = new StreamEntity();
//        stream.setStreamType(streamType);
//
//        final StreamEntity deproxy = (StreamEntity) (new BaseEntityDeProxyProcessor(true).process(stream));
//
//        Assert.assertEquals(deproxy.getStreamType().getClass(), StreamTypeEntity.class);
//        Assert.assertEquals(100L, deproxy.getStreamType().getId());
//    }
//
//    public static class DummyStreamType extends StreamTypeEntity implements HibernateProxy {
//        private static final long serialVersionUID = -338271738308074875L;
//
//        @Override
//        public LazyInitializer getHibernateLazyInitializer() {
//            final Object proxy = Proxy.newProxyInstance(this.getClass().getClassLoader(),
//                    new Class<?>[]{LazyInitializer.class}, (proxy1, method, args) -> {
//                        if (method.getName().equals("getEntityName")) {
//                            return StreamTypeEntity.class.getName();
//                        }
//                        return null;
//                    });
//            return (LazyInitializer) proxy;
//        }
//
//        @Override
//        public Object writeReplace() {
//            return null;
//        }
//    }
}
