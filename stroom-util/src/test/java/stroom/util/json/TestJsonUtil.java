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
                .withWrappedOutputType(new TypeLiteral<Map<String, String>>(){})
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
                .withWrappedInputType(new TypeLiteral<Set<String>>(){})
                .withWrappedOutputType(new TypeLiteral<Map<String, String>>(){})
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

}
