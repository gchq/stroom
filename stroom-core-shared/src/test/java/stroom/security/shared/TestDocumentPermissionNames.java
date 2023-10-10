package stroom.security.shared;

import stroom.security.shared.DocumentPermissionNames.InferredPermissionType;
import stroom.test.common.TestUtil;
import stroom.util.shared.GwtUtil;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static stroom.security.shared.DocumentPermissionNames.DELETE;
import static stroom.security.shared.DocumentPermissionNames.OWNER;
import static stroom.security.shared.DocumentPermissionNames.READ;
import static stroom.security.shared.DocumentPermissionNames.UPDATE;
import static stroom.security.shared.DocumentPermissionNames.USE;

class TestDocumentPermissionNames {

    @TestFactory
    Stream<DynamicTest> testExcludePermissions() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<Set<String>, String[]>>() {})
                .withWrappedOutputType(new TypeLiteral<Set<String>>() {})
                .withTestFunction(testCase -> DocumentPermissionNames.excludePermissions(
                        testCase.getInput()._1(),
                        testCase.getInput()._2()))
                .withSimpleEqualityAssertion()
                .addCase(
                        Tuple.of(Set.of("a", "b", "c"), null),
                        Set.of("a", "b", "c"))
                .addCase(
                        Tuple.of(Set.of("a", "b", "c"), new String[0]),
                        Set.of("a", "b", "c"))
                .addCase(
                        Tuple.of(Set.of("a", "b", "c"), new String[] {"b"}),
                        Set.of("a", "c"))
                .addCase(
                        Tuple.of(Set.of("a", "b", "c"), new String[] {"d"}),
                        Set.of("a", "b", "c"))
                .addCase(
                        Tuple.of(Set.of("a", "b", "c"), new String[] {"a", "b", "c"}),
                        Collections.emptySet())
                .build();
    }

    @TestFactory
    Stream<DynamicTest> test() {

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<List<String>>() {
                })
                .withWrappedOutputType(new TypeLiteral<Map<String, InferredPermissionType>>() {
                })
                .withSingleArgTestFunction(directPerms ->
                        DocumentPermissionNames.getInferredPermissions(directPerms)
                        .asMap())
                .withSimpleEqualityAssertion()
                .addCase(null, Collections.emptyMap())
                .addCase(Collections.emptyList(), Collections.emptyMap())
                .addCase(GwtUtil.toList(USE), GwtUtil.toMap(USE, InferredPermissionType.DIRECT))
                .addCase(GwtUtil.toList(READ), GwtUtil.toMap(
                        USE, InferredPermissionType.INFERRED,
                        READ, InferredPermissionType.DIRECT))
                .addCase(GwtUtil.toList(UPDATE), GwtUtil.toMap(
                        USE, InferredPermissionType.INFERRED,
                        READ, InferredPermissionType.INFERRED,
                        UPDATE, InferredPermissionType.DIRECT))
                .addCase(GwtUtil.toList(DELETE), GwtUtil.toMap(
                        USE, InferredPermissionType.INFERRED,
                        READ, InferredPermissionType.INFERRED,
                        UPDATE, InferredPermissionType.INFERRED,
                        DELETE, InferredPermissionType.DIRECT))
                .addCase(GwtUtil.toList(OWNER), GwtUtil.toMap(
                        USE, InferredPermissionType.INFERRED,
                        READ, InferredPermissionType.INFERRED,
                        UPDATE, InferredPermissionType.INFERRED,
                        DELETE, InferredPermissionType.INFERRED,
                        OWNER, InferredPermissionType.DIRECT))
                .addCase(GwtUtil.toList(USE, OWNER), GwtUtil.toMap(
                        USE, InferredPermissionType.DIRECT,
                        READ, InferredPermissionType.INFERRED,
                        UPDATE, InferredPermissionType.INFERRED,
                        DELETE, InferredPermissionType.INFERRED,
                        OWNER, InferredPermissionType.DIRECT))
                .addCase(GwtUtil.toList(OWNER, USE), GwtUtil.toMap(
                        USE, InferredPermissionType.DIRECT,
                        READ, InferredPermissionType.INFERRED,
                        UPDATE, InferredPermissionType.INFERRED,
                        DELETE, InferredPermissionType.INFERRED,
                        OWNER, InferredPermissionType.DIRECT))
                .build();
    }

}
