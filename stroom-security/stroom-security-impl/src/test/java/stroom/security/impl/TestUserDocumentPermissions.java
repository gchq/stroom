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

package stroom.security.impl;

import stroom.docref.DocRef;
import stroom.security.shared.DocumentPermission;
import stroom.test.common.TestUtil;

import io.vavr.Tuple;
import it.unimi.dsi.fastutil.objects.Object2ByteMap;
import it.unimi.dsi.fastutil.objects.Object2ByteOpenHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("checkstyle:AvoidStaticImport")
class TestUserDocumentPermissions {

    private static final DocRef DOC_UUID_1 = new DocRef("test", "123");
    private static final DocRef DOC_UUID_2 = new DocRef("test", "456");
    private static final DocRef DOC_UUID_3 = new DocRef("test", "789");

    private UserDocumentPermissions userDocPerms;

    @BeforeEach
    void setUp() {
        userDocPerms = UserDocumentPermissions.builder()
                .setPermission(DOC_UUID_1.getUuid(), DocumentPermission.OWNER)
                .setPermission(DOC_UUID_2.getUuid(), DocumentPermission.DELETE)
                .build();

        assertThat(userDocPerms.getPermissions())
                .containsEntry(DOC_UUID_1.getUuid(), DocumentPermission.OWNER)
                .containsEntry(DOC_UUID_2.getUuid(), DocumentPermission.DELETE);
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
                .addCase(Tuple.of(DOC_UUID_1, DocumentPermission.USE), true) // Inferred
                .addCase(Tuple.of(DOC_UUID_1, DocumentPermission.VIEW), true) // Inferred
                .addCase(Tuple.of(DOC_UUID_1, DocumentPermission.EDIT), true) // Direct
                .addCase(Tuple.of(DOC_UUID_1, DocumentPermission.DELETE), true) // Inferred
                .addCase(Tuple.of(DOC_UUID_1, DocumentPermission.OWNER), true) // Direct

                .addCase(Tuple.of(DOC_UUID_2, DocumentPermission.USE), true) // Inferred
                .addCase(Tuple.of(DOC_UUID_2, DocumentPermission.VIEW), true) // Direct
                .addCase(Tuple.of(DOC_UUID_2, DocumentPermission.EDIT), true) // Inferred
                .addCase(Tuple.of(DOC_UUID_2, DocumentPermission.DELETE), true) // Direct
                .addCase(Tuple.of(DOC_UUID_2, DocumentPermission.OWNER), false)

                .addCase(Tuple.of(DOC_UUID_3, DocumentPermission.USE), false)
                .addCase(Tuple.of(DOC_UUID_3, DocumentPermission.VIEW), false)
                .addCase(Tuple.of(DOC_UUID_3, DocumentPermission.EDIT), false)
                .addCase(Tuple.of(DOC_UUID_3, DocumentPermission.DELETE), false)
                .addCase(Tuple.of(DOC_UUID_3, DocumentPermission.OWNER), false)

                .build();
    }

    @Test
    void testPermissionPrimitiveValuesDoNotEqualPermissionNotFoundValue() {
        assertThat(DocumentPermission.values())
                .allSatisfy(documentPermission ->
                        assertThat(documentPermission.getPrimitiveValue())
                                .isNotEqualTo(UserDocumentPermissions.PERMISSION_NOT_FOUND_VALUE));
    }

    @Test
    void testPermissionPrimitiveValuesGrreaterThanPermissionNotFoundValue() {
        assertThat(DocumentPermission.values())
                .allSatisfy(documentPermission ->
                        assertThat(documentPermission.getPrimitiveValue())
                                .isGreaterThan(UserDocumentPermissions.PERMISSION_NOT_FOUND_VALUE));
    }

    @Test
    void testObject2ByteMapConstructorCopiesInput() {
        final Object2ByteMap<String> permissions = new Object2ByteOpenHashMap<>();
        permissions.put(DOC_UUID_1.getUuid(), DocumentPermission.OWNER.getPrimitiveValue());

        final UserDocumentPermissions userDocumentPermissions = UserDocumentPermissions.create(permissions);

        permissions.clear();

        assertThat(userDocumentPermissions.hasDocumentPermission(DOC_UUID_1, DocumentPermission.OWNER))
                .isTrue();
    }

    @Test
    void testMapConstructorCopiesInput() {
        final Map<String, Byte> permissions = new HashMap<>();
        permissions.put(DOC_UUID_1.getUuid(), DocumentPermission.OWNER.getPrimitiveValue());

        final UserDocumentPermissions userDocumentPermissions = UserDocumentPermissions.create(permissions);

        permissions.clear();

        assertThat(userDocumentPermissions.hasDocumentPermission(DOC_UUID_1, DocumentPermission.OWNER))
                .isTrue();
    }

    @Test
    void testNullPermissionsMap() {
        assertThatThrownBy(() -> UserDocumentPermissions.create((Map<String, Byte>) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Null permissions map");
    }

    @Test
    void testNullKey() {
        final Map<String, Byte> permissions = new HashMap<>();
        permissions.put(null, DocumentPermission.OWNER.getPrimitiveValue());

        assertThatThrownBy(() -> UserDocumentPermissions.create(permissions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Null key in permissions entry");
    }

    @Test
    void testNullValue() {
        final Map<String, Byte> permissions = new HashMap<>();
        permissions.put(DOC_UUID_1.getUuid(), null);

        assertThatThrownBy(() -> UserDocumentPermissions.create(permissions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Null DocumentPermission for key 123");
    }

    @Test
    void testInvalidPermissionPrimitiveValue() {
        final Map<String, Byte> permissions = Map.of(DOC_UUID_1.getUuid(), (byte) 99);

        assertThatThrownBy(() -> UserDocumentPermissions.create(permissions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Null or invalid DocumentPermission primitive 99 for key 123");
    }

    @Test
    void testObject2ByteMapWithInvalidPermissionPrimitiveValue() {
        final Object2ByteMap<String> permissions = new Object2ByteOpenHashMap<>();
        permissions.put(DOC_UUID_1.getUuid(), (byte) 99);

        assertThatThrownBy(() -> UserDocumentPermissions.create(permissions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Null or invalid DocumentPermission primitive 99 for key 123");
    }

    @Test
    void addPermission() {
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_3, DocumentPermission.EDIT))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_3, DocumentPermission.VIEW))
                .isFalse();

        setPermission(DOC_UUID_3, DocumentPermission.EDIT);

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_3, DocumentPermission.EDIT))
                .isTrue();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_3, DocumentPermission.VIEW))
                .isTrue();

        // Add same again
        setPermission(DOC_UUID_3, DocumentPermission.EDIT);

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_3, DocumentPermission.EDIT))
                .isTrue();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_3, DocumentPermission.VIEW))
                .isTrue();
    }

    @Test
    void removePermission() {
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.OWNER))
                .isTrue();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.DELETE))
                .isTrue();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.EDIT))
                .isTrue();

        setPermission(DOC_UUID_1, DocumentPermission.EDIT);

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.OWNER))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.DELETE))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.EDIT))
                .isTrue();

        // Remove same again
        setPermission(DOC_UUID_1, DocumentPermission.EDIT);

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.OWNER))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.DELETE))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.EDIT))
                .isTrue();
    }

    @Test
    void clearDocumentPermissions() {
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.OWNER))
                .isTrue();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.DELETE))
                .isTrue();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.EDIT))
                .isTrue();

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_2, DocumentPermission.VIEW))
                .isTrue();

        removePermission(DOC_UUID_1);

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.OWNER))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.DELETE))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.EDIT))
                .isFalse();

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_2, DocumentPermission.VIEW))
                .isTrue();

        // Same again
        removePermission(DOC_UUID_1);

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.OWNER))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.DELETE))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.EDIT))
                .isFalse();

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_2, DocumentPermission.VIEW))
                .isTrue();

        removePermission(DOC_UUID_2);

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_2, DocumentPermission.VIEW))
                .isFalse();
    }

    private void removePermission(final DocRef docRef) {
        final Map<String, DocumentPermission> copy = new HashMap<>(userDocPerms.getPermissions());
        copy.remove(docRef.getUuid());
        buildUserDocumentPermissions(copy);
    }

    private void setPermission(final DocRef docRef, final DocumentPermission documentPermission) {
        final Map<String, DocumentPermission> copy = new HashMap<>(userDocPerms.getPermissions());

        // Set the permission
        copy.put(docRef.getUuid(), documentPermission);
        buildUserDocumentPermissions(copy);
    }

    private void buildUserDocumentPermissions(final Map<String, DocumentPermission> permsMap) {
        final Map<String, Byte> modifiedMap = permsMap
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Entry::getKey,
                        stringDocumentPermissionEntry ->
                                stringDocumentPermissionEntry.getValue().getPrimitiveValue()));
        userDocPerms = UserDocumentPermissions.create(modifiedMap);
    }
}
