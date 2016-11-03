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

import stroom.util.shared.PropertyMap;
import stroom.util.test.StroomJUnit4ClassRunner;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestPropertyMap {
    @Test
    public void testSimple() {
        PropertyMap map = new PropertyMap();
        map.put("key", "value");
        map.put("k=y", "va ue");

        String line = map.toArgLine();

        PropertyMap test = new PropertyMap();
        test.loadArgLine(line);

        Assert.assertEquals(map, test);

    }

    @Test
    public void testLoadInvalid() {
        PropertyMap map = new PropertyMap();
        map.loadArgLine("");

        Assert.assertEquals(0, map.size());
    }

}
