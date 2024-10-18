package stroom.index.impl;

import stroom.docref.DocRef;
import stroom.index.impl.IndexFieldCacheImpl.Key;
import stroom.test.common.TestUtil;

import io.vavr.Tuple;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Objects;
import java.util.stream.Stream;

class TestIndexFieldCacheImpl {

    @TestFactory
    Stream<DynamicTest> test() {

        final DocRef docRef1 = DocRef.builder()
                .type("type")
                .name("docref1")
                .randomUuid()
                .build();
        final DocRef docRef2 = DocRef.builder()
                .type("type")
                .name("docref2")
                .randomUuid()
                .build();

        return TestUtil.buildDynamicTestStream()
                .withInputTypes(Key.class, Key.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {

                    final boolean areObjectsEqual = Objects.equals(
                            testCase.getInput()._1(),
                            testCase.getInput()._2());
                    final boolean areHashesEqual = Objects.equals(
                            testCase.getInput()._1().hashCode(),
                            testCase.getInput()._2().hashCode());

                    Assertions.assertThat(areHashesEqual)
                            .isEqualTo(areObjectsEqual);

                    return areObjectsEqual;
                })
                .withSimpleEqualityAssertion()
                .addCase(
                        Tuple.of(new Key(docRef1, null), new Key(docRef1, null)),
                        true)
                .addCase(
                        Tuple.of(new Key(docRef1, ""), new Key(docRef1, "")),
                        true)
                .addCase(
                        Tuple.of(new Key(docRef1, "bar"), new Key(docRef1, "bar")),
                        true)
                .addCase(
                        Tuple.of(new Key(docRef1, "bar"), new Key(docRef1, "BAR")),
                        true)
                .addCase(
                        Tuple.of(new Key(docRef1, "foo"), new Key(docRef1, "BAR")),
                        false)
                .addCase(
                        Tuple.of(new Key(docRef1, "bar"), new Key(docRef2, "bar")),
                        false)
                .addCase(
                        Tuple.of(new Key(docRef1, ""), new Key(docRef2, "bar")),
                        false)
                .build();
    }
}
