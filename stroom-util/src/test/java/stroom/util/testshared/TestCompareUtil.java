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

package stroom.util.testshared;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import stroom.util.shared.CompareUtil;
import stroom.util.test.StroomJUnit4ClassRunner;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestCompareUtil {
    @Test
    public void testStringCompare() {
        Assert.assertEquals(0, CompareUtil.compareString(null, null));
        Assert.assertEquals(0, CompareUtil.compareString("A", "A"));
        Assert.assertEquals(0, CompareUtil.compareString("A", "a"));
        Assert.assertEquals(-1, CompareUtil.compareString("A", "B"));
        Assert.assertEquals(1, CompareUtil.compareString("B", "a"));
        Assert.assertEquals(1, CompareUtil.compareString("B", null));
        Assert.assertEquals(-1, CompareUtil.compareString(null, "B"));
    }

    @Test
    public void testLongCompare() {
        Assert.assertEquals(0, CompareUtil.compareLong(null, null));
        Assert.assertEquals(0, CompareUtil.compareLong(1L, 1L));
        Assert.assertEquals(-1, CompareUtil.compareLong(1L, 2L));
        Assert.assertEquals(1, CompareUtil.compareLong(2L, 1L));
        Assert.assertEquals(1, CompareUtil.compareLong(2L, null));
        Assert.assertEquals(-1, CompareUtil.compareLong(null, 2L));
    }

}
