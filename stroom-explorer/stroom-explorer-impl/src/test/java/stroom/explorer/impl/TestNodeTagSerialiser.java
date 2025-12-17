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

package stroom.explorer.impl;

import stroom.test.common.TestUtil;

import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;

class TestNodeTagSerialiser {

    @TestFactory
    Stream<DynamicTest> serialise() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Set<String>>(){})
                .withOutputType(String.class)
                .withSingleArgTestFunction(NodeTagSerialiser::serialise)
                .withSimpleEqualityAssertion()
                .addCase(null, null)
                .addCase(Collections.emptySet(), null)
                .addCase(Set.of("foo"), "foo")
                .addCase(Set.of("FOO"), "foo")
                .addCase(Set.of("foo", "bar"), "bar foo")
                .addCase(Set.of("bar", "FOO"), "bar foo")
                .addCase(Set.of("b", "c", "a"), "a b c")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> deserialise() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withWrappedOutputType(new TypeLiteral<Set<String>>(){})
                .withSingleArgTestFunction(NodeTagSerialiser::deserialise)
                .withSimpleEqualityAssertion()
                .addCase(null, Collections.emptySet())
                .addCase("", Collections.emptySet())
                .addCase(" ", Collections.emptySet())
                .addCase("   ", Collections.emptySet())
                .addCase("foo", Set.of("foo"))
                .addCase("FOO bar", Set.of("foo", "bar"))
                .addCase("BAR foo", Set.of("foo", "bar"))
                .addCase("  foo    bar   ", Set.of("foo", "bar"))
                .build();
    }
}
