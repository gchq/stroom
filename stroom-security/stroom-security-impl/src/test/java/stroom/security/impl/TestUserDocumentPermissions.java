package stroom.security.impl;

import stroom.docref.DocRef;
import stroom.security.shared.DocumentPermission;
import stroom.test.common.TestUtil;

import io.vavr.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.security.shared.DocumentPermission.DELETE;
import static stroom.security.shared.DocumentPermission.EDIT;
import static stroom.security.shared.DocumentPermission.OWNER;
import static stroom.security.shared.DocumentPermission.USE;
import static stroom.security.shared.DocumentPermission.VIEW;

class TestUserDocumentPermissions {

    private static final DocRef DOC_UUID_1 = new DocRef("test", "123");
    private static final DocRef DOC_UUID_2 = new DocRef("test", "456");
    private static final DocRef DOC_UUID_3 = new DocRef("test", "789");

    private UserDocumentPermissions userDocPerms;

    @BeforeEach
    void setUp() {
        userDocPerms = new UserDocumentPermissions();
        userDocPerms.setPermission(DOC_UUID_1, OWNER);
        userDocPerms.setPermission(DOC_UUID_2, DELETE);
    }

    @TestFactory
    Stream<DynamicTest> testHasDocumentPermission() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(DocRef.class, DocumentPermission.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> userDocPerms.hasDocumentPermission(
                        testCase.getInput()._1(),
                        testCase.getInput()._2()))
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(DOC_UUID_1, USE), true) // Inferred
                .addCase(Tuple.of(DOC_UUID_1, VIEW), true) // Inferred
                .addCase(Tuple.of(DOC_UUID_1, EDIT), true) // Direct
                .addCase(Tuple.of(DOC_UUID_1, DELETE), true) // Inferred
                .addCase(Tuple.of(DOC_UUID_1, OWNER), true) // Direct

                .addCase(Tuple.of(DOC_UUID_2, USE), true) // Inferred
                .addCase(Tuple.of(DOC_UUID_2, VIEW), true) // Direct
                .addCase(Tuple.of(DOC_UUID_2, EDIT), true) // Inferred
                .addCase(Tuple.of(DOC_UUID_2, DELETE), true) // Direct
                .addCase(Tuple.of(DOC_UUID_2, OWNER), false)

                .addCase(Tuple.of(DOC_UUID_3, USE), false)
                .addCase(Tuple.of(DOC_UUID_3, VIEW), false)
                .addCase(Tuple.of(DOC_UUID_3, EDIT), false)
                .addCase(Tuple.of(DOC_UUID_3, DELETE), false)
                .addCase(Tuple.of(DOC_UUID_3, OWNER), false)

                .build();
    }


    @Test
    void addPermission() {
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_3, EDIT))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_3, VIEW))
                .isFalse();

        userDocPerms.setPermission(DOC_UUID_3, EDIT);

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_3, EDIT))
                .isTrue();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_3, VIEW))
                .isTrue();

        // Add same again
        userDocPerms.setPermission(DOC_UUID_3, EDIT);

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_3, EDIT))
                .isTrue();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_3, VIEW))
                .isTrue();
    }

    @Test
    void removePermission() {
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, OWNER))
                .isTrue();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DELETE))
                .isTrue();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, EDIT))
                .isTrue();

        userDocPerms.setPermission(DOC_UUID_1, EDIT);

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, OWNER))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DELETE))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, EDIT))
                .isTrue();

        // Remove same again
        userDocPerms.setPermission(DOC_UUID_1, EDIT);

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, OWNER))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DELETE))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, EDIT))
                .isTrue();
    }

    @Test
    void clearDocumentPermissions() {
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, OWNER))
                .isTrue();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DELETE))
                .isTrue();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, EDIT))
                .isTrue();

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_2, VIEW))
                .isTrue();

        userDocPerms.clearPermission(DOC_UUID_1);

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, OWNER))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DELETE))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, EDIT))
                .isFalse();

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_2, VIEW))
                .isTrue();

        // Same again
        userDocPerms.clearPermission(DOC_UUID_1);

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, OWNER))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DELETE))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, EDIT))
                .isFalse();

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_2, VIEW))
                .isTrue();

        userDocPerms.clearPermission(DOC_UUID_2);

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_2, VIEW))
                .isFalse();
    }
}
