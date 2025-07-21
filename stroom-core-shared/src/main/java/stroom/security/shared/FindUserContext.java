package stroom.security.shared;

public enum FindUserContext {
    // Used when changing the run as behaviour for processing or task execution.
    RUN_AS,
    // Used when changing annotation assignment.
    ANNOTATION_ASSIGNMENT,
    // Used when listing users for changing permissions.
    PERMISSIONS
}
