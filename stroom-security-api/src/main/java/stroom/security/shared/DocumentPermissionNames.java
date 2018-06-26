/*
 * Copyright 2016 Crown Copyright
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

package stroom.security.shared;

import java.util.HashMap;
import java.util.Map;

/**
 * Provide string constants for the permissions.
 */
public final class DocumentPermissionNames {
    /**
     * Users with the use permission can use documents.
     */
    public static final String USE = "Use";

    /**
     * Users with the create permission can find and create/saveAs documents.
     */
    public static final String CREATE = "Create";

    /**
     * Users with the read permission can find, create and read/load/view
     * documents.
     */
    public static final String READ = "Read";

    /**
     * Users with the update permission can find, create, read and update/save
     * documents.
     */
    public static final String UPDATE = "Update";

    /**
     * Users with delete permission can find, create, read, update and delete
     * documents.
     */
    public static final String DELETE = "Delete";

    /**
     * Owners have permission to find, create, read, update and delete documents
     * plus they can change permissions.
     */
    public static final String OWNER = "Owner";

    public static final String[] DOCUMENT_PERMISSIONS = new String[]{USE, READ, UPDATE, DELETE, OWNER};

    private static final Map<String, String> LOWER_PERMISSIONS = new HashMap<String, String>();
    private static final Map<String, String> HIGHER_PERMISSIONS = new HashMap<String, String>();

    static {
        LOWER_PERMISSIONS.put(OWNER, DELETE);
        LOWER_PERMISSIONS.put(DELETE, UPDATE);
        LOWER_PERMISSIONS.put(UPDATE, READ);
        LOWER_PERMISSIONS.put(READ, USE);
    }

    static {
        HIGHER_PERMISSIONS.put(DELETE, OWNER);
        HIGHER_PERMISSIONS.put(UPDATE, DELETE);
        HIGHER_PERMISSIONS.put(READ, UPDATE);
        HIGHER_PERMISSIONS.put(USE, READ);
    }

    private DocumentPermissionNames() {
        // Constants
    }

    public static final String getDocumentCreatePermission(final String docType) {
        return CREATE + " - " + docType;
    }

    public static final String getLowerPermission(final String permission) {
        return LOWER_PERMISSIONS.get(permission);
    }

    public static final String getHigherPermission(final String permission) {
        return HIGHER_PERMISSIONS.get(permission);
    }

    public static boolean isValidPermission(final String permission) {
        for (final String perm : DOCUMENT_PERMISSIONS) {
            if (perm.equals(permission)) {
                return true;
            }
        }

        return false;
    }
}