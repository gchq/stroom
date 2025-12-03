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

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Hold all the document permissions that a user holds.
 */
public class UserDocumentPermissions {

    //docUuid => DocumentPermissionEnum.primitiveValue
    private final Map<String, Byte> permissions;

    public UserDocumentPermissions() {
        permissions = new ConcurrentHashMap<>();
    }

    public UserDocumentPermissions(final Map<String, Byte> permissions) {
        this.permissions = permissions;
    }

    /**
     * @return True if the passed permission is directly held or inherited
     * (e.g. permission == 'Use' and this document holds 'Update' which
     * inherits Use so return true).
     */
    public boolean hasDocumentPermission(final DocRef docRef, final DocumentPermission permission) {
        final Byte perm = permissions.get(docRef.getUuid());
        if (perm != null) {
            return perm >= permission.getPrimitiveValue();
        }
        return false;
    }

    public void setPermission(final DocRef docRef, final DocumentPermission permission) {
        setPermission(docRef.getUuid(), permission);
    }

    public void setPermission(final String docUuid, final DocumentPermission permission) {
        permissions.put(docUuid, permission.getPrimitiveValue());
    }

    public void clearPermission(final DocRef docRef) {
        clearPermission(docRef.getUuid());
    }

    public void clearPermission(final String docUuid) {
        permissions.remove(docUuid);
    }

    /**
     * Mostly for use in tests
     *
     * @return A map of docUUID => {@link DocumentPermission}.
     */
    public Map<String, DocumentPermission> getPermissions() {
        return permissions.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Entry::getKey,
                        entry ->
                                DocumentPermission.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(entry.getValue())));
    }
}
