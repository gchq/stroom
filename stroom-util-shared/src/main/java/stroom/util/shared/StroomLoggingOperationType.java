package stroom.util.shared;

public enum StroomLoggingOperationType {
    //Special type - don't log at all
    UNLOGGED,

    //Special type - system should determine event type based on resource method name and HTTP method
    ALLOCATE_AUTOMATICALLY,

    //Standard event types follow
    CREATE,
    UPDATE,
    DELETE,
    VIEW,
    COPY,
    SEARCH,
    PROCESS,
    IMPORT,
    EXPORT,
    UNKNOWN
}
