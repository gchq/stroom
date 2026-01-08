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

package stroom.util.client;

import stroom.query.api.Param;
import stroom.query.api.ParamUtil;
import stroom.query.api.ParamValues;
import stroom.query.api.ParamValuesImpl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestParamUtil {

    @Test
    void testSimpleParse() {
        final List<Param> list = ParamUtil.parse("param1=value1");
        final Param param = list.iterator().next();
        assertThat(param.getKey()).isEqualTo("param1");
        assertThat(param.getValue()).isEqualTo("value1");
    }

    @Test
    void testComplexParse() {
        testKV("k1=v1", "k1", "v1");
        testKV("k1=v1 key2=value\"\"2 key3=value\"\"3", "k1", "v1", "key2", "value\"2", "key3", "value\"3");
        testKV("k1=v1 key2=\"quoted string\" key3=value\"\"3", "k1", "v1", "key2", "quoted string", "key3", "value\"3");
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
        ParamValues paramValues = new ParamValuesImpl(getMap("key1=value1"));
        String result = ParamUtil.replaceParameters("this is ${key1}", paramValues);
        assertThat(result).isEqualTo("this is value1");

        paramValues = new ParamValuesImpl(getMap("key1=value1 key2=value2"));
        result = ParamUtil.replaceParameters("this is $${key1} ${key2}", paramValues);
        assertThat(result).isEqualTo("this is $value1 value2");

        result = ParamUtil.replaceParameters("this is $$${key1} ${key2}", paramValues);
        assertThat(result).isEqualTo("this is $$value1 value2");

        result = ParamUtil.replaceParameters("this is $$$${key1} ${key2}", paramValues);
        assertThat(result).isEqualTo("this is $$$value1 value2");

        result = ParamUtil.replaceParameters("this is \"${key1}\" ${key2}", paramValues);
        assertThat(result).isEqualTo("this is \"${key1}\" value2");

        paramValues = new ParamValuesImpl(getMap("user=\"user1 user2\""));
        result = ParamUtil.replaceParameters("${user}", paramValues);
        assertThat(result).isEqualTo("'user1 user2'");
    }

    @Test
    void testGetKeys() {
        List<String> result = ParamUtil.getKeys("this is ${key1}");
        assertThat(result).containsAll(List.of("key1"));

        result = ParamUtil.getKeys("this is $${key1} ${key2}");
        assertThat(result).containsAll(List.of("key2"));

        result = ParamUtil.getKeys("this is $$${key1} ${key2}");
        assertThat(result).containsAll(List.of("key1", "key2"));

        result = ParamUtil.getKeys("this is $$$${key1} ${key2}");
        assertThat(result).containsAll(List.of("key2"));

        result = ParamUtil.getKeys("this is $$$$${key1} ${key2}");
        assertThat(result).containsAll(List.of("key1", "key2"));

        result = ParamUtil.getKeys("$this is $$$$${key1} ${key2}");
        assertThat(result).containsAll(List.of("key1", "key2"));

        result = ParamUtil.getKeys("${user}");
        assertThat(result).containsAll(List.of("user"));
    }

    private Map<String, String> getMap(final String input) {
        final List<Param> list = ParamUtil.parse(input);
        return ParamUtil.createParamMap(list);
    }

    private void testKV(final String text, final String... expectedParams) {
        final Map<String, String> map = getMap(text);

        assertThat(expectedParams.length > 0).isTrue();
        assertThat(expectedParams.length % 2 == 0).isTrue();
        assertThat(map.size()).isEqualTo(expectedParams.length / 2);

        for (int i = 0; i < expectedParams.length; i += 2) {
            final String key = expectedParams[i];
            final String value = expectedParams[i + 1];
            assertThat(map.get(key)).isEqualTo(value);
        }
    }
}
