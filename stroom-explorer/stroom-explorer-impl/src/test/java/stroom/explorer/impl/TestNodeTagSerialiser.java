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
        final NodeTagSerialiser nodeTagSerialiser = new NodeTagSerialiser();
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Set<String>>(){})
                .withOutputType(String.class)
                .withSingleArgTestFunction(nodeTagSerialiser::serialise)
                .withSimpleEqualityAssertion()
                .addCase(null, null)
                .addCase(Collections.emptySet(), null)
                .addCase(Set.of("foo"), "foo")
                .addCase(Set.of("FOO"), "foo")
                .addCase(Set.of("foo", "bar"), "bar foo")
                .addCase(Set.of("bar", "FOO"), "bar foo")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> deserialise() {
        final NodeTagSerialiser nodeTagSerialiser = new NodeTagSerialiser();
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withWrappedOutputType(new TypeLiteral<Set<String>>(){})
                .withSingleArgTestFunction(nodeTagSerialiser::deserialise)
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
