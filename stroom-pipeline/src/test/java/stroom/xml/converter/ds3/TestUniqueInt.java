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

package stroom.xml.converter.ds3;

import stroom.util.test.StroomUnitTest;
import org.junit.Assert;
import org.junit.Test;

public class TestUniqueInt extends StroomUnitTest {
    @Test
    public void test() {
        final UniqueInt uniqueInt = new UniqueInt();

        Assert.assertEquals(-1, uniqueInt.getMax());
        Assert.assertNull(uniqueInt.getArr());
        Assert.assertEquals("", uniqueInt.toString());

        uniqueInt.add(3);
        uniqueInt.add(1);
        uniqueInt.add(5);
        uniqueInt.add(10);
        uniqueInt.add(0);

        Assert.assertEquals(10, uniqueInt.getMax());
        Assert.assertEquals(5, uniqueInt.getArr().length);
        Assert.assertEquals("0,1,3,5,10", uniqueInt.toString());

        uniqueInt.add(4);
        uniqueInt.add(10);

        Assert.assertEquals(10, uniqueInt.getMax());
        Assert.assertEquals(6, uniqueInt.getArr().length);
        Assert.assertEquals("0,1,3,4,5,10", uniqueInt.toString());

        uniqueInt.add(22);
        uniqueInt.add(10);

        Assert.assertEquals(22, uniqueInt.getMax());
        Assert.assertEquals(7, uniqueInt.getArr().length);
        Assert.assertEquals("0,1,3,4,5,10,22", uniqueInt.toString());
    }
}
