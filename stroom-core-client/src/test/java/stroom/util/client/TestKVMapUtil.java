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

package stroom.util.client;

import org.junit.Assert;
import org.junit.Test;
import stroom.util.test.StroomUnitTest;

import java.util.Map;

public class TestKVMapUtil extends StroomUnitTest {
    @Test
    public void testSimpleParse() {
        final Map<String, String> map = KVMapUtil.parse("param1=value1");
        Assert.assertEquals("param1", map.keySet().iterator().next());
        Assert.assertEquals("value1", map.get("param1"));
    }

    @Test
    public void testComplexParse() {
        testKV("k1=v1", "k1", "v1");
        testKV("k1=v1 key2=value\"\"2 key3=value\"\"3", "k1", "v1", "key2", "value\"2", "key3", "value\"3");
        testKV("k1=v1 key2=\"quoted string\" key3=value\"\"3", "k1", "v1", "key2", "quoted string", "key3", "value\"3");
        testKV("k1=v1 key2=\"quoted \"\" string\" key3=value\"\"3", "k1", "v1", "key2", "quoted \" string", "key3", "value\"3");
        testKV("k1=v1 key2=\"quoted = string\" key3=value\"\"3", "k1", "v1", "key2", "quoted = string", "key3", "value\"3");
        testKV("k1=v1 key2=escaped \\= string key3=value\"\"3", "k1", "v1", "key2", "escaped = string", "key3", "value\"3");
    }

    @Test
    public void testReplacement() {
        Map<String, String> map = KVMapUtil.parse("key1=value1");
        String result = KVMapUtil.replaceParameters("this is ${key1}", map);
        Assert.assertEquals("this is value1", result);

        map = KVMapUtil.parse("key1=value1 key2=value2");
        result = KVMapUtil.replaceParameters("this is $${key1} ${key2}", map);
        Assert.assertEquals("this is ${key1} value2", result);

        result = KVMapUtil.replaceParameters("this is $$${key1} ${key2}", map);
        Assert.assertEquals("this is $value1 value2", result);

        result = KVMapUtil.replaceParameters("this is $$$${key1} ${key2}", map);
        Assert.assertEquals("this is $${key1} value2", result);

        result = KVMapUtil.replaceParameters("this is $$$$${key1} ${key2}", map);
        Assert.assertEquals("this is $$value1 value2", result);

        result = KVMapUtil.replaceParameters("$this is $$$$${key1} ${key2}", map);
        Assert.assertEquals("$this is $$value1 value2", result);

        map = KVMapUtil.parse("user=user1 user2");
        result = KVMapUtil.replaceParameters("${user}", map);
        Assert.assertEquals("user1 user2", result);
    }

    private void testKV(String text, String... expectedParams) {
        final Map<String, String> map = KVMapUtil.parse(text);

        Assert.assertTrue(expectedParams.length > 0);
        Assert.assertTrue(expectedParams.length % 2 == 0);
        Assert.assertEquals(expectedParams.length / 2, map.size());

        for (int i = 0; i < expectedParams.length; i += 2) {
            String key = expectedParams[i];
            String value = expectedParams[i + 1];
            Assert.assertEquals(value, map.get(key));
        }
    }
}
