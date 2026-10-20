/*
 * Copyright 2026 Crown Copyright
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


package stroom.gwt.workbench.client.app.rest;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestJsonValues {

    @Test
    void testParse() {
        final Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("s", "a\"b\né/");
        expected.put("n", JsonValues.normalise(new BigDecimal("-150")));
        expected.put("t", true);
        expected.put("f", false);
        expected.put("z", null);
        expected.put("a", Arrays.asList(BigDecimal.ONE, Arrays.asList(), new LinkedHashMap<>()));

        assertThat(JsonValues.parse(
                " {\"s\": \"a\\\"b\\n\\u00e9\\/\", \"n\": -1.5e2, \"t\": true, \"f\": false, \"z\": null,"
                        + " \"a\": [1, [], {}]} "))
                .isEqualTo(expected);
    }

    @Test
    void testParse_scalars() {
        assertThat(JsonValues.parse("\"x\"")).isEqualTo("x");
        assertThat(JsonValues.parse("12")).isEqualTo(new BigDecimal("12"));
        assertThat(JsonValues.parse("null")).isNull();
    }

    @Test
    void testParse_invalid() {
        assertThatThrownBy(() -> JsonValues.parse("{\"a\": }")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JsonValues.parse("[1, 2")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JsonValues.parse("{} x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JsonValues.parse("tru")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JsonValues.parse("\"\\u12\"")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JsonValues.parse("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testJsonEquals() {
        assertThat(JsonValues.jsonEquals("{\"a\":1,\"b\":[2]}", "{ \"b\": [2.0], \"a\": 1 }")).isTrue();
        assertThat(JsonValues.jsonEquals("{\"a\":1}", "{\"a\":2}")).isFalse();
        assertThat(JsonValues.jsonEquals(null, null)).isTrue();
        assertThat(JsonValues.jsonEquals("{}", null)).isFalse();
        assertThat(JsonValues.jsonEquals("{}", "{")).isFalse();
    }

    @Test
    void testParse_strictNumbers() {
        // Numbers JSON allows
        for (final String valid : new String[]{"0", "-0", "1", "-1", "10", "0.5", "-0.5", "1e5", "1E+5", "1e-5",
                "1.25e10", "123456789012345678901234567890"}) {
            assertThat(JsonValues.parse(valid)).as(valid).isInstanceOf(BigDecimal.class);
        }
        // ... and ones it doesn't
        for (final String invalid : new String[]{"+1", "01", "-01", "00", "1.", ".5", "-", "1e", "1e+", "1E-",
                "-.5", "1.e5", "0x10", "NaN", "Infinity", "-Infinity", "1-2", "1..2", "1e5e5"}) {
            assertThatThrownBy(() -> JsonValues.parse(invalid))
                    .as(invalid)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void testParse_strictStrings() {
        assertThatThrownBy(() -> JsonValues.parse("\"a\\x\"")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JsonValues.parse("\"a\nb\"")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JsonValues.parse("\"a\tb\"")).isInstanceOf(IllegalArgumentException.class);
        // Integer.parseInt would accept a sign in a unicode escape
        assertThatThrownBy(() -> JsonValues.parse("\"\\u+123\"")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JsonValues.parse("\"\\u-123\"")).isInstanceOf(IllegalArgumentException.class);
        assertThat(JsonValues.parse("\"\\u00E9\\/\\\\\"")).isEqualTo("é/\\");
    }

    @Test
    void testJsonEquals_numbersCompareExactly() {
        // Equal values written differently
        assertThat(JsonValues.jsonEquals("1", "1.0")).isTrue();
        assertThat(JsonValues.jsonEquals("1", "10e-1")).isTrue();
        assertThat(JsonValues.jsonEquals("100", "1e2")).isTrue();
        assertThat(JsonValues.jsonEquals("0", "-0.0")).isTrue();
        assertThat(JsonValues.jsonEquals("0", "0e10")).isTrue();
        // Long ids that are the same double (2^53 + 1 rounds to 2^53)
        assertThat(9007199254740993.0).isEqualTo(9007199254740992.0);
        assertThat(JsonValues.jsonEquals("{\"id\": 9007199254740993}", "{\"id\": 9007199254740992}")).isFalse();
        assertThat(JsonValues.jsonEquals("{\"id\": 9007199254740993}", "{\"id\": 9007199254740993}")).isTrue();
        assertThat(JsonValues.jsonEquals("0.1", "0.10000000000000001")).isFalse();
    }

    @Test
    void testContainsObjectMembers() {
        assertThat(JsonValues.jsonContains("{\"a\": 1, \"b\": {\"c\": 2, \"d\": 3}}",
                "{\"b\": {\"c\": 2}}")).isTrue();
        assertThat(JsonValues.jsonContains("{\"a\": 1}", "{\"a\": 1, \"b\": 2}")).isFalse();
        assertThat(JsonValues.jsonContains("{\"a\": 1}", "{\"a\": \"1\"}")).isFalse();
        // A null member must be there (and null)
        assertThat(JsonValues.jsonContains("{\"a\": null}", "{\"a\": null}")).isTrue();
        assertThat(JsonValues.jsonContains("{}", "{\"a\": null}")).isFalse();
        assertThat(JsonValues.jsonContains("{\"a\": 1}", "{}")).isTrue();
    }

    @Test
    void testContainsArraysItemByItem() {
        // As Jest's toMatchObject: the same length, each item containing the expected item
        assertThat(JsonValues.jsonContains("[{\"id\": 1, \"x\": 2}, {\"id\": 3}]",
                "[{\"id\": 1}, {\"id\": 3}]")).isTrue();
        assertThat(JsonValues.jsonContains("[1, 2]", "[1]")).isFalse();
        assertThat(JsonValues.jsonContains("[1, 2]", "[2, 1]")).isFalse();
        assertThat(JsonValues.jsonContains("{\"a\": [1]}", "{\"a\": {}}")).isFalse();
    }

    @Test
    void testContainsComparesNumbersByValue() {
        assertThat(JsonValues.jsonContains("{\"a\": 1.0}", "{\"a\": 1}")).isTrue();
        assertThat(JsonValues.jsonContains("2", "2")).isTrue();
        assertThat(JsonValues.jsonContains("2", "3")).isFalse();
    }

    @Test
    void testContainsInvalidOrMissingActual() {
        assertThat(JsonValues.jsonContains(null, "{}")).isFalse();
        assertThat(JsonValues.jsonContains("{", "{}")).isFalse();
        assertThatThrownBy(() -> JsonValues.jsonContains("{}", "{"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
