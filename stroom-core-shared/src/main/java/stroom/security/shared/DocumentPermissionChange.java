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

import java.util.List;

public enum DocumentPermissionChange implements HasDisplayValue {
    SET_PERMISSION(
            "Set permission",
            "Set a specific user permission."),
//    REMOVE_PERMISSION(
//            "Remove permission",
//            "Remove permission for the specified user."),

    ADD_DOCUMENT_CREATE_PERMISSION(
            "Add permission to create",
            "Add permission to create documents in the selected folders."),
    REMOVE_DOCUMENT_CREATE_PERMISSION(
            "Remove permission to create",
            "Remove permission to create documents in the selected folders."),
    ADD_ALL_DOCUMENT_CREATE_PERMISSIONS(
            "Add permission to create any document",
            "Add permission to create documents in the selected folders."),
    REMOVE_ALL_DOCUMENT_CREATE_PERMISSIONS(
            "Remove permission to create any document",
            "Remove permission to create documents in the selected folders."),

    ADD_ALL_PERMISSIONS_FROM(
            "Add all permissions",
            "Add all permissions from the specified document to the selection."),
    SET_ALL_PERMISSIONS_FROM(
            "Set all permissions",
            "Set all permissions in the selection to be exactly the same as the specified document."),

    REMOVE_ALL_PERMISSIONS(
            "Remove all permissions for all users [DANGEROUS]",
            "Removes all permissions for all users.");

    public static final List<DocumentPermissionChange> LIST = List.of(
            SET_PERMISSION,

            ADD_DOCUMENT_CREATE_PERMISSION,
            REMOVE_DOCUMENT_CREATE_PERMISSION,
            ADD_ALL_DOCUMENT_CREATE_PERMISSIONS,
            REMOVE_ALL_DOCUMENT_CREATE_PERMISSIONS,

            ADD_ALL_PERMISSIONS_FROM,
            SET_ALL_PERMISSIONS_FROM,

            REMOVE_ALL_PERMISSIONS
    );

    private final String displayValue;
    private final String description;

    DocumentPermissionChange(final String displayValue,
                             final String description) {
        this.displayValue = displayValue;
        this.description = description;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    public String getDescription() {
        return description;
    }
}
