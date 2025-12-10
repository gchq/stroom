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

package stroom.security.shared;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

import java.util.List;

/**
 * Provide enums for the document permissions.
 */
public enum DocumentPermission implements HasDisplayValue, HasPrimitiveValue {

    // ************************* IMPORTANT!!!! *************************
    // Any change to existing primitiveValues must be done in conjunction with a
    // database migration as these values are held in the DB.
    //
    // Also, the primitiveValue is used to determine whether one permission is higher
    // than another, so the enum values with the highest permission must have the
    // highest primitiveValue.
    //
    // Also, do NOT use a primitiveValue of -1 as that gets used in DocumentPermissionDaoImpl
    // as an alternative for null values.
    //
    // *****************************************************************

    /**
     * Users with owner permission can find, view/read/load, edit/update/save, delete
     * documents and change permissions.
     */
    OWNER("Owner", 50, PermissionType.DESTRUCTIVE,
            "Same as delete plus ability to change document permissions"),

    /**
     * Users with delete permission can find, view/read/load, edit/update/save and delete
     * documents.
     */
    DELETE("Delete", 40, PermissionType.DESTRUCTIVE,
            "Same as edit plus permission to delete documents"),

    /**
     * Users with the edit permission can find, view/read/load and edit/update/save
     * documents.
     */
    EDIT("Edit", 30, PermissionType.DESTRUCTIVE,
            "Same as view plus permission to edit documents"),

    /**
     * Users with the view permission can find and view/read/load documents.
     */
    VIEW("View", 20, PermissionType.NON_DESTRUCTIVE,
            "Permission to view documents"),

    /**
     * Users with the use permission can use documents.
     */
    USE("Use", 10, PermissionType.NON_DESTRUCTIVE,
            "Only allow use of a document, " +
            "e.g. allow use of an index or feeds as part of a search process but do not allow viewing " +
            "of the document itself");

    public static final List<DocumentPermission> LIST = List.of(USE, VIEW, EDIT, DELETE, OWNER);
    public static final PrimitiveValueConverter<DocumentPermission> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(DocumentPermission.class, DocumentPermission.values());

    private final String displayValue;
    private final byte primitiveValue;
    private final PermissionType permissionType;
    private final String description;

    DocumentPermission(final String displayValue,
                       final int primitiveValue,
                       final PermissionType permissionType,
                       final String description) {
        this.displayValue = displayValue;
        this.primitiveValue = (byte) primitiveValue;
        this.permissionType = permissionType;
        this.description = description;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }

    public PermissionType getPermissionType() {
        return permissionType;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEqualOrHigher(final DocumentPermission permission) {
        return primitiveValue >= permission.getPrimitiveValue();
    }

    public boolean isHigher(final DocumentPermission permission) {
        return primitiveValue > permission.getPrimitiveValue();
    }

    /**
     * @return The highest permission. Copes with nulls.
     */
    public static DocumentPermission highest(final DocumentPermission perm1,
                                             final DocumentPermission perm2) {
        if (perm1 == null) {
            return perm2;
        } else if (perm2 == null) {
            return perm1;
        } else if (perm1.primitiveValue > perm2.primitiveValue) {
            return perm1;
        } else {
            return perm2;
        }
    }


    // --------------------------------------------------------------------------------


    public enum PermissionType {
        DESTRUCTIVE,
        NON_DESTRUCTIVE
    }
}
