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
 */

package stroom.util.zip;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.util.io.StreamUtil;
import stroom.util.test.StroomJUnit4ClassRunner;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestHeaderMap {
    @Test
    public void testSimple() {
        HeaderMap headerMap = new HeaderMap();
        headerMap.put("person", "person1");

        Assert.assertEquals("person1", headerMap.get("person"));
        Assert.assertEquals("person1", headerMap.get("PERSON"));

        Assert.assertEquals(new HashSet<>(Arrays.asList("person")), headerMap.keySet());

        headerMap.put("PERSON", "person2");

        Assert.assertEquals("person2", headerMap.get("person"));
        Assert.assertEquals("person2", headerMap.get("PERSON"));

        Assert.assertEquals(new HashSet<>(Arrays.asList("PERSON")), headerMap.keySet());

        HeaderMap headerMap2 = new HeaderMap();
        headerMap2.put("persOn", "person3");
        headerMap2.put("persOn1", "person4");

        headerMap.putAll(headerMap2);

        Assert.assertEquals(new HashSet<>(Arrays.asList("persOn", "persOn1")), headerMap.keySet());

    }

    @Test
    public void testRemove() {
        HeaderMap headerMap = new HeaderMap();
        headerMap.put("a", "a1");
        headerMap.put("B", "b1");

        headerMap.removeAll(Arrays.asList("A", "b"));

        Assert.assertEquals(0, headerMap.size());
    }

    @Test
    public void testReadWrite() throws IOException {
        HeaderMap headerMap = new HeaderMap();
        headerMap.read("b:2\na:1\nz\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        Assert.assertEquals("1", headerMap.get("a"));
        Assert.assertEquals("2", headerMap.get("b"));
        Assert.assertNull(headerMap.get("z"));

        Assert.assertEquals("a:1\nb:2\nz\n", new String(headerMap.toByteArray(), StreamUtil.DEFAULT_CHARSET));
    }

    @Test
    public void testtoString() throws IOException {
        HeaderMap headerMap = new HeaderMap();
        headerMap.read("b:2\na:1\nz\n".getBytes(StreamUtil.DEFAULT_CHARSET));

        // HeaderMap's are used in log output and so check that they do output
        // the map values.
        Assert.assertTrue(headerMap.toString(), headerMap.toString().contains("b=2"));
        Assert.assertTrue(headerMap.toString(), headerMap.toString().contains("a=1"));
    }

    @Test
    public void testTrim() {
        HeaderMap headerMap = new HeaderMap();
        headerMap.put(" person ", "person1");
        headerMap.put("PERSON", "person2");
        headerMap.put("FOOBAR", "1");
        headerMap.put("F OOBAR", "2");
        headerMap.put(" foobar ", " 3 ");

        Assert.assertEquals("person2", headerMap.get("PERSON "));
        Assert.assertEquals("3", headerMap.get("FOOBAR"));

    }

}
