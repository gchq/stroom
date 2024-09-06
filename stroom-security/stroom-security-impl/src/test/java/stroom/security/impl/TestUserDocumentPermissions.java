package stroom.security.impl;

import stroom.test.common.TestUtil;

import io.vavr.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.security.shared.DocumentPermissionNames.DELETE;
import static stroom.security.shared.DocumentPermissionNames.OWNER;
import static stroom.security.shared.DocumentPermissionNames.READ;
import static stroom.security.shared.DocumentPermissionNames.UPDATE;
import static stroom.security.shared.DocumentPermissionNames.USE;

class TestUserDocumentPermissions {

    private static final String DOC_UUID_1 = "123";
    private static final String DOC_UUID_2 = "456";
    private static final String DOC_UUID_3 = "789";

    private UserDocumentPermissions userDocPerms;

    @BeforeEach
    void setUp() {
        userDocPerms = new UserDocumentPermissions();
        userDocPerms.addPermission(DOC_UUID_1, UPDATE);
        userDocPerms.addPermission(DOC_UUID_1, OWNER);

        userDocPerms.addPermission(DOC_UUID_2, READ);
        userDocPerms.addPermission(DOC_UUID_2, DELETE);
    }

    @TestFactory
    Stream<DynamicTest> testHasDocumentPermission() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, String.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> userDocPerms.hasDocumentPermission(
                        testCase.getInput()._1(),
                        testCase.getInput()._2()))
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(DOC_UUID_1, USE), true) // Inferred
                .addCase(Tuple.of(DOC_UUID_1, READ), true) // Inferred
                .addCase(Tuple.of(DOC_UUID_1, UPDATE), true) // Direct
                .addCase(Tuple.of(DOC_UUID_1, DELETE), true) // Inferred
                .addCase(Tuple.of(DOC_UUID_1, OWNER), true) // Direct

                .addCase(Tuple.of(DOC_UUID_2, USE), true) // Inferred
                .addCase(Tuple.of(DOC_UUID_2, READ), true) // Direct
                .addCase(Tuple.of(DOC_UUID_2, UPDATE), true) // Inferred
                .addCase(Tuple.of(DOC_UUID_2, DELETE), true) // Direct
                .addCase(Tuple.of(DOC_UUID_2, OWNER), false)

                .addCase(Tuple.of(DOC_UUID_3, USE), false)
                .addCase(Tuple.of(DOC_UUID_3, READ), false)
                .addCase(Tuple.of(DOC_UUID_3, UPDATE), false)
                .addCase(Tuple.of(DOC_UUID_3, DELETE), false)
                .addCase(Tuple.of(DOC_UUID_3, OWNER), false)

                .build();
    }


    @Test
    void addPermission() {
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_3, UPDATE))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_3, READ))
                .isFalse();

        userDocPerms.addPermission(DOC_UUID_3, UPDATE);

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_3, UPDATE))
                .isTrue();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_3, READ))
                .isTrue();

        // Add same again
        userDocPerms.addPermission(DOC_UUID_3, UPDATE);

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_3, UPDATE))
                .isTrue();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_3, READ))
                .isTrue();
    }

    @Test
    void removePermission() {
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, OWNER))
                .isTrue();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DELETE))
                .isTrue();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, UPDATE))
                .isTrue();

        userDocPerms.removePermission(DOC_UUID_1, OWNER);

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, OWNER))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DELETE))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, UPDATE))
                .isTrue();

        // Remove same again
        userDocPerms.removePermission(DOC_UUID_1, OWNER);

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, OWNER))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DELETE))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, UPDATE))
                .isTrue();
    }

    @Test
    void clearDocumentPermissions() {
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, OWNER))
                .isTrue();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DELETE))
                .isTrue();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, UPDATE))
                .isTrue();

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_2, READ))
                .isTrue();

        userDocPerms.clearDocumentPermissions(DOC_UUID_1);

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, OWNER))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DELETE))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, UPDATE))
                .isFalse();

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_2, READ))
                .isTrue();

        // Same again
        userDocPerms.clearDocumentPermissions(DOC_UUID_1);

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, OWNER))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DELETE))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, UPDATE))
                .isFalse();

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_2, READ))
                .isTrue();

        userDocPerms.clearDocumentPermissions(DOC_UUID_2);

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_2, READ))
                .isFalse();
    }
}
