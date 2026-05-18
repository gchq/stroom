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

package stroom.util.json;

import stroom.test.common.TestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.TypeLiteral;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

class TestJsonUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestJsonUtil.class);

    protected static final String JSON = """
            {
                "alg": "algorithm",
                "childObj": {
                  "child1": "a",
                  "child2": "b"
                },
                "childArr": [ "1", "2" ],
                "kid": "12345678-1234-1234-1234-123456789012",
                "signer": "xxx",
                "iss": "url",
                "client": "client-id",
                "exp": "expiration"
             }""";

    @Test
    void getEntries() {
        Assertions.assertThat(JsonUtil.getEntries(JSON, "alg", "iss"))
                .isEqualTo(Map.of("alg", "algorithm",
                        "iss", "url"));
    }

    @TestFactory
    Stream<DynamicTest> testGetEntries_differentJson() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withWrappedOutputType(new TypeLiteral<Map<String, String>>() {
                })
                .withTestFunction(testCase ->
                        JsonUtil.getEntries(testCase.getInput(), "key"))
                .withSimpleEqualityAssertion()
                // root array
                .addCase("""
                        [
                          { "key": "a" },
                          { "key": "b" }
                        ]
                        """, Collections.emptyMap())
                // empty obj
                .addCase("""
                        {
                        }
                        """, Collections.emptyMap())
                // single key
                .addCase("""
                        {
                          "key": "123"
                        }
                        """, Map.of("key", "123"))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> getEntries_rootObject() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Set<String>>() {
                })
                .withWrappedOutputType(new TypeLiteral<Map<String, String>>() {
                })
                .withTestFunction(testCase ->
                        JsonUtil.getEntries(JSON, testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, Collections.emptyMap())
                .addCase(Collections.emptySet(), Collections.emptyMap())
                .addCase(Set.of("notFound"), Collections.emptyMap())
                .addCase(Set.of("childObj"), Collections.emptyMap())
                .addCase(Set.of("childArr"), Collections.emptyMap())
                .addCase(Set.of("signer"), Map.of("signer", "xxx"))
                .addCase(Set.of("signer", "iss"), Map.of(
                        "signer", "xxx",
                        "iss", "url"))
                .addCase(Set.of("signer", "notFound"), Map.of(
                        "signer", "xxx"))
                // child1 is in sub object, so ignored
                .addCase(Set.of("signer", "child1"), Map.of(
                        "signer", "xxx"))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> getValue_rootObject() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(String.class)
                .withTestFunction(testCase ->
                        JsonUtil.getValue(JSON, testCase.getInput()).orElse(null))
                .withSimpleEqualityAssertion()
                .addCase(null, null)
                .addCase("", null)
                .addCase("  ", null)
                .addCase("notFound", null)
                .addCase("childObj", null)
                .addCase("childArr", null)
                .addCase("signer", "xxx")
                .build();
    }

    @Test
    void testConsistentOrder() {
        final String json = JsonUtil.getConsistentOrderMapper(true)
                .writeValueAsString(new MyPojo());

        // JSON props and map entries should be in a nice predictable a-z order
        Assertions.assertThat(json)
                .isEqualTo("""
                        {
                          "aaa" : "AAA",
                          "bbb" : "BBB",
                          "ccc" : "CCC",
                          "ddd" : "DDD",
                          "mmmMap" : {
                            "aaa" : "AAA",
                            "bbb" : "BBB",
                            "ccc" : "CCC",
                            "ddd" : "DDD",
                            "xxx" : "XXX",
                            "yyy" : "YYY",
                            "zzz" : "ZZZ"
                          },
                          "xxx" : "XXX",
                          "yyy" : "YYY",
                          "zzz" : "ZZZ"
                        }""");
    }

    @Test
    void testEnum() {
        final EnumWrapper enumWrapper = new EnumWrapper(MyEnum.TWO);
        final String jsonV3 = JsonUtil.getMapper(true)
                .writeValueAsString(enumWrapper);
        final String jsonV2 = writeValueAsStringWithV2Jackson(enumWrapper);

        LOGGER.debug("jsonV3:\n{}", jsonV3);
        LOGGER.debug("jsonV2:\n{}", jsonV2);

        Assertions.assertThat(jsonV3)
                .isEqualTo(jsonV2);
    }

    private static <T> String writeValueAsStringWithV2Jackson(final T value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT)
                    .writeValueAsString(value);
        } catch (final JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }


    // --------------------------------------------------------------------------------


    private static class EnumWrapper {

        @JsonProperty
        private final MyEnum myEnum;

        @JsonCreator
        private EnumWrapper(@JsonProperty("myEnum") final MyEnum myEnum) {
            this.myEnum = myEnum;
        }

        public MyEnum getMyEnum() {
            return myEnum;
        }
    }


    private enum MyEnum {
        ONE(1),
        TWO(2),
        THREE(3),
        ;

        private final int value;

        MyEnum(final int value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    // --------------------------------------------------------------------------------


    @JsonPropertyOrder(alphabetic = true)
    @SuppressWarnings("unused")
    private static class MyPojo {

        // Declared in random order
        @JsonProperty
        private final String aaa;
        @JsonProperty
        private final String ddd;
        @JsonProperty
        private final String yyy;
        @JsonProperty
        private final String xxx;
        @JsonProperty
        private final String ccc;
        @JsonProperty
        private final String bbb;
        @JsonProperty
        private final String zzz;
        @JsonProperty
        private final Map<String, String> mmmMap;

        // Declared in
        @JsonCreator
        private MyPojo(@JsonProperty("ddd") final String ddd,
                       @JsonProperty("aaa") final String aaa,
                       @JsonProperty("zzz") final String zzz,
                       @JsonProperty("ccc") final String ccc,
                       @JsonProperty("xxx") final String xxx) {

            // Assignments kept alphabetical to guarantee all 26 are accounted for
            this.aaa = aaa;
            this.bbb = "BBB";
            this.ccc = ccc;
            this.ddd = ddd;
            // ...
            this.xxx = xxx;
            this.yyy = "YYY";
            this.zzz = zzz;
            this.mmmMap = Map.of(
                    "zzz", "ZZZ",
                    "yyy", "YYY",
                    "xxx", "XXX",
                    "ddd", "DDD",
                    "ccc", "CCC",
                    "bbb", "BBB",
                    "aaa", "AAA");
        }

        public MyPojo() {
            this("DDD", "AAA", "ZZZ", "CCC", "XXX");
        }
    }
}
