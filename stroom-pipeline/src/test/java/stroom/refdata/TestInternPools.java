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

package stroom.refdata;

import org.junit.Assert;
import org.junit.Test;
import stroom.pipeline.filter.EventListInternPool;
import stroom.util.test.StroomUnitTest;
import stroom.util.thread.ThreadUtil;
import stroom.xml.event.EventList;

public class TestInternPools extends StroomUnitTest {
    @Test
    public void testEventListEquality() {
        final EventList eventList1 = MapStoreTestUtil.createEventList();
        final EventList eventList2 = MapStoreTestUtil.createEventList();
        Assert.assertEquals(eventList1, eventList2);
    }

    @Test
    public void testMapStoreInternPool() {
        final MapStoreInternPool internPool = new MapStoreInternPool();

        MapStore mapStore1 = MapStoreTestUtil.createMapStore();
        MapStore mapStore2 = MapStoreTestUtil.createMapStore();

        MapStore mapStore3 = internPool.intern(mapStore1);
        Assert.assertEquals(1, internPool.size());
        Assert.assertTrue(mapStore3 == mapStore1);

        MapStore mapStore4 = internPool.intern(mapStore2);
        Assert.assertEquals(1, internPool.size());
        Assert.assertTrue(mapStore4 == mapStore1);
        Assert.assertTrue(mapStore4 == mapStore3);
        Assert.assertTrue(mapStore4 != mapStore2);

        mapStore1 = null;
        mapStore2 = null;
        mapStore3 = null;
        mapStore4 = null;

        System.gc();
        ThreadUtil.sleep(100);
        System.out.println("size=" + internPool.size());
    }

    @Test
    public void testMultiRef() {
        EventList eventList = MapStoreTestUtil.createEventList();
        MapStore mapStore = MapStoreTestUtil.createMapStore(eventList);

        final MapStoreInternPool mapStoreInternPool = new MapStoreInternPool();
        final EventListInternPool eventListInternPool = new EventListInternPool();

        mapStore = mapStoreInternPool.intern(mapStore);
        eventList = eventListInternPool.intern(eventList);

        mapStore = null;
        eventList = null;

        System.gc();
        ThreadUtil.sleep(100);
        System.out.println("mapStoreInternPool=" + mapStoreInternPool.size());
        System.out.println("eventListInternPool=" + eventListInternPool.size());
    }
}
