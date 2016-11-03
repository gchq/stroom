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

import stroom.util.test.StroomUnitTest;
import org.junit.Assert;
import org.junit.Test;

public class TestMapStoreKey extends StroomUnitTest {
    @Test
    public void testEquality() {
        final MapStoreKey mapStoreKey1 = new MapStoreKey("TestMapName", "TestKeyName");
        final MapStoreKey mapStoreKey2 = new MapStoreKey("TestMapName", "TestKeyName");
        Assert.assertEquals(mapStoreKey1, mapStoreKey2);

        final MapStoreKey mapStoreKey3 = new MapStoreKey("TestMapName", "TestKeyName2");
        Assert.assertFalse(mapStoreKey1.equals(mapStoreKey3));

        final MapStoreKey mapStoreKey4 = new MapStoreKey("TestMapName2", "TestKeyName");
        Assert.assertFalse(mapStoreKey1.equals(mapStoreKey4));
    }
}
