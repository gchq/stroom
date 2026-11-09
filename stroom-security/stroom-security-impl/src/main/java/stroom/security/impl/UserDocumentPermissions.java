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
import stroom.util.shared.NullSafe;

import it.unimi.dsi.fastutil.objects.Object2ByteMap;
import it.unimi.dsi.fastutil.objects.Object2ByteMaps;
import it.unimi.dsi.fastutil.objects.Object2ByteOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

/// Holds all the document permissions held by a single user/group.
/// Immutable.
public class UserDocumentPermissions {

    // This value MUST be lower than all primitive values in the DocumentPermission enum
    // else hasDocumentPermission() won't work. There is a test to assert this.
    static final byte PERMISSION_NOT_FOUND_VALUE = Byte.MIN_VALUE;
    public static final UserDocumentPermissions EMPTY = new UserDocumentPermissions(Object2ByteMaps.emptyMap());

    /// Map of document UUID to document permission primitive value.
    /// This defines all the document permissions held by this user/group.
    private final Object2ByteMap<String> permissions;

    /// Create a [UserDocumentPermissions] from the supplied map of document permissions keyed on document UUID.
    /// Will create a defensive copy of the map.
    public static UserDocumentPermissions create(@Nullable final Object2ByteMap<String> permissions) {
        if (NullSafe.isEmptyMap(permissions)) {
            return EMPTY;
        } else {
            // Defensive copy of the map, so we are fully immutable
            return new UserDocumentPermissions(new Object2ByteOpenHashMap<>(permissions));
        }
    }

    /// **ONLY** for use in testing as it uses boxed values.
    /// Creates a set of user document permissions from a boxed map.
    ///
    /// @param permissions A map of document UUID to document permission primitive value.
    static UserDocumentPermissions create(final Map<String, Byte> permissions) {
        Objects.requireNonNull(permissions, "Null permissions map");
        permissions.forEach((key, byteValue) -> {
            if (byteValue == null) {
                throw new IllegalArgumentException("Null DocumentPermission for key " + key);
            }
        });
        return UserDocumentPermissions.create(new Object2ByteOpenHashMap<>(permissions));
    }

    /// Creates a set of user document permissions from a primitive map.
    ///
    /// @param permissions A map of document UUID to document permission primitive value.
    private UserDocumentPermissions(final Object2ByteMap<String> permissions) {
        Objects.requireNonNull(permissions, "Null permissions map");
        if (permissions.isEmpty()) {
            this.permissions = Object2ByteMaps.emptyMap();
        } else {
            validateEntries(permissions);
            this.permissions = Object2ByteMaps.unmodifiable(permissions);
        }
    }

    private static void validateEntries(final Object2ByteMap<String> permissions) {
        // Just in case there is any rubbish in the DB
        permissions.forEach((key, byteValue) -> {
            if (key == null) {
                throw new IllegalArgumentException("Null key in permissions entry");
            }
            final DocumentPermission perm = DocumentPermission.PRIMITIVE_VALUE_CONVERTER
                    .fromPrimitiveValue(byteValue);
            if (perm == null) {
                throw new IllegalArgumentException(
                        "Null or invalid DocumentPermission primitive " + byteValue
                        + " for key " + key);
            }
        });
    }

    /// @return True if the passed permission is directly held or inherited
    /// (e.g. permission == 'Use' and this document holds 'Update' which
    /// inherits Use so return true).
    public boolean hasDocumentPermission(final DocRef docRef,
                                         final DocumentPermission requestedPermission) {
        final byte heldPermissionByte = permissions.getOrDefault(docRef.getUuid(), PERMISSION_NOT_FOUND_VALUE);
        // Tests assert that PERMISSION_NOT_FOUND_VALUE is lower than all primitive values in the enum
        return heldPermissionByte >= requestedPermission.getPrimitiveValue();
    }

    /// Mostly for use in tests
    ///
    /// @return A map of docUUID => [DocumentPermission].
    public Map<String, DocumentPermission> getPermissions() {
        return permissions.object2ByteEntrySet()
                .stream()
                .collect(Collectors.toMap(
                        Entry::getKey,
                        entry ->
                                DocumentPermission.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(
                                        entry.getByteValue())));
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private Object2ByteMap<String> permissions = null;

        Builder setPermission(final String docUuid, final DocumentPermission permission) {
            Objects.requireNonNull(permission);
            return setPermission(docUuid, permission.getPrimitiveValue());
        }

        public Builder setPermission(final String docUuid, final byte permissionPrimitiveValue) {
            Objects.requireNonNull(docUuid);
            if (permissions == null) {
                this.permissions = new Object2ByteOpenHashMap<>();
            }
            this.permissions.put(docUuid, permissionPrimitiveValue);
            return this;
        }

        public UserDocumentPermissions build() {
            // Use private ctor as we don't want to copy the map as we trust it.
            return permissions != null
                    ? new UserDocumentPermissions(permissions)
                    : UserDocumentPermissions.EMPTY;
        }
    }
}
