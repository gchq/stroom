package stroom.security.shared;

import stroom.docref.HasDisplayValue;

import java.util.List;

public enum DocumentPermissionChange implements HasDisplayValue {
    SET_PERMSSION(
            "Set permission",
            "Set a specific user permission."),
//    REMOVE_PERMISSION(
//            "Remove permission",
//            "Remove permisison for the specified user."),

    ADD_DOCUMENT_CREATE_PERMSSION(
            "Add permission to create",
            "Add permission to create documents in the selected folders."),
    REMOVE_DOCUMENT_CREATE_PERMSSION(
            "Remove permission to create",
            "Remove permission to create documents in the selected folders."),
    ADD_ALL_DOCUMENT_CREATE_PERMSSIONS(
            "Add permission to create any document",
            "Add permission to create documents in the selected folders."),
    REMOVE_ALL_DOCUMENT_CREATE_PERMSSIONS(
            "Remove permission to create any document",
            "Remove permission to create documents in the selected folders."),

    ADD_ALL_PERMSSIONS_FROM(
            "Add all permissions",
            "Add all permissions from the specified document to the selection."),
    SET_ALL_PERMSSIONS_FROM(
            "Set all permissions",
            "Set all permissions in the selection to be exactly the same as the specified document."),

    REMOVE_ALL_PERMISSIONS(
            "Remove all permissions [DANGEROUS]",
            "Removes all permisisons.");

    public static final List<DocumentPermissionChange> LIST = List.of(
            SET_PERMSSION,
//            REMOVE_PERMISSION,

            ADD_DOCUMENT_CREATE_PERMSSION,
            REMOVE_DOCUMENT_CREATE_PERMSSION,
            ADD_ALL_DOCUMENT_CREATE_PERMSSIONS,
            REMOVE_ALL_DOCUMENT_CREATE_PERMSSIONS,

            ADD_ALL_PERMSSIONS_FROM,
            SET_ALL_PERMSSIONS_FROM,

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
