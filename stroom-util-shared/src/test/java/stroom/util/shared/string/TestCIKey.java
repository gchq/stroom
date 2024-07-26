package stroom.util.shared.string;

import stroom.test.common.TestUtil;

import io.vavr.Tuple;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class TestCIKey {

    @TestFactory
    Stream<DynamicTest> test() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, String.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final CIKey str1 = CIKey.of(testCase.getInput()._1());
                    final CIKey str2 = CIKey.of(testCase.getInput()._2());
                    // Make sure the wrappers hold the original value
                    Assertions.assertThat(str1.get())
                            .isEqualTo(testCase.getInput()._1());
                    Assertions.assertThat(str2.get())
                            .isEqualTo(testCase.getInput()._2());

                    final boolean areEqual = Objects.equals(str1, str2);
                    final boolean haveEqualHashCode = Objects.equals(str1.hashCode(), str2.hashCode());

                    // If objects are equal, so should the hashes
                    Assertions.assertThat(haveEqualHashCode)
                            .isEqualTo(areEqual);

                    return areEqual;
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, null), true)
                .addCase(Tuple.of(null, "foo"), false)
                .addCase(Tuple.of("foo", null), false)
                .addCase(Tuple.of("foo", "bar"), false)
                .addCase(Tuple.of("foo", "foox"), false)
                .addCase(Tuple.of("foo", "foo"), true)
                .addCase(Tuple.of("foo", "FOO"), true)
                .addCase(Tuple.of("foo", "Foo"), true)
                .addCase(Tuple.of("foo123", "Foo123"), true)
                .addCase(Tuple.of("123", "123"), true)
                .addCase(Tuple.of("", ""), true)
                .build();
    }

    @Test
    void testWithMap() {

        final Map<CIKey, String> map = new HashMap<>();

        final Consumer<String> putter = str ->
                map.put(CIKey.of(str), str);

        putter.accept("foo"); // first key put to 'foo'
        putter.accept("fOo");
        putter.accept("FOO"); // Last value put to 'foo'
        putter.accept("bar");

        Assertions.assertThat(map)
                .hasSize(2);
        Assertions.assertThat(map)
                .containsKeys(
                        CIKey.of("foo"),
                        CIKey.of("bar"));

        Assertions.assertThat(map.keySet().stream().map(CIKey::get).collect(Collectors.toSet()))
                .contains("foo", "bar");

        Assertions.assertThat(map.values())
                .contains("FOO", "bar");

        Assertions.assertThat(map.get(CIKey.of("foo")))
                .isEqualTo("FOO");
        Assertions.assertThat(map.get(CIKey.of("FOO")))
                .isEqualTo("FOO");
    }
}
