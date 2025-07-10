package stroom.importexport.api;

public enum ExportMode {
    /**
     * Export the requested items to the specified path.
     */
    EXPORT,
    /**
     * Perform a dry-run of the export to obtain the summary of the items
     * to be exported. Nothing will be written to the path and the path
     * will be ignored.
     */
    DRY_RUN,
    ;
}
