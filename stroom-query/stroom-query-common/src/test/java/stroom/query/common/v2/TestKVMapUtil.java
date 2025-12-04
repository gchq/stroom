/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.query.common.v2;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestKVMapUtil {

    @Test
    void testSimpleParse() {
        final Map<String, String> map = KVMapUtil.parse("param1=value1");
        assertThat(map.keySet().iterator().next()).isEqualTo("param1");
        assertThat(map.get("param1")).isEqualTo("value1");
    }

    @Test
    void testComplexParse() {
        testKV("k1=v1", "k1", "v1");
        testKV("k1=v1 key2=value\"\"2 key3=value\"\"3",
                "k1",
                "v1",
                "key2",
                "value\"2",
                "key3",
                "value\"3");
        testKV("k1=v1 key2=\"quoted string\" key3=value\"\"3",
                "k1",
                "v1",
                "key2",
                "quoted string",
                "key3",
                "value\"3");
        testKV("k1=v1 key2=\"quoted \"\" string\" key3=value\"\"3",
                "k1",
                "v1",
                "key2",
                "quoted \" string",
                "key3",
                "value\"3");
        testKV("k1=v1 key2=\"quoted = string\" key3=value\"\"3",
                "k1",
                "v1",
                "key2",
                "quoted = string",
                "key3",
                "value\"3");
        testKV("k1=v1 key2=escaped \\= string key3=value\"\"3",
                "k1",
                "v1",
                "key2",
                "escaped = string",
                "key3",
                "value\"3");
    }

    @Test
    void testReplacement() {
        Map<String, String> map = KVMapUtil.parse("key1=value1");
        String result = KVMapUtil.replaceParameters("this is ${key1}", map);
        assertThat(result).isEqualTo("this is value1");

        map = KVMapUtil.parse("key1=value1 key2=value2");
        result = KVMapUtil.replaceParameters("this is $${key1} ${key2}", map);
        assertThat(result).isEqualTo("this is ${key1} value2");

        result = KVMapUtil.replaceParameters("this is $$${key1} ${key2}", map);
        assertThat(result).isEqualTo("this is $value1 value2");

        result = KVMapUtil.replaceParameters("this is $$$${key1} ${key2}", map);
        assertThat(result).isEqualTo("this is $${key1} value2");

        result = KVMapUtil.replaceParameters("this is $$$$${key1} ${key2}", map);
        assertThat(result).isEqualTo("this is $$value1 value2");

        result = KVMapUtil.replaceParameters("$this is $$$$${key1} ${key2}", map);
        assertThat(result).isEqualTo("$this is $$value1 value2");

        map = KVMapUtil.parse("user=user1 user2");
        result = KVMapUtil.replaceParameters("${user}", map);
        assertThat(result).isEqualTo("user1 user2");
    }

    private void testKV(final String text, final String... expectedParams) {
        final Map<String, String> map = KVMapUtil.parse(text);

        assertThat(expectedParams.length > 0).isTrue();
        assertThat(expectedParams.length % 2 == 0).isTrue();
        assertThat(map).hasSize(expectedParams.length / 2);

        for (int i = 0; i < expectedParams.length; i += 2) {
            final String key = expectedParams[i];
            final String value = expectedParams[i + 1];
            assertThat(map.get(key)).isEqualTo(value);
        }
    }
}
