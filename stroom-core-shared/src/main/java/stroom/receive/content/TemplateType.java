package stroom.receive.content;

public enum TemplateType {
    /**
     * Create a processor filter specific to the feed on an existing pipeline that
     * is appropriate to the attributeMap values.
     */
    PROCESSOR_FILTER,
    /**
     * Create a new pipeline (and associated filter specific to the feed) that inherits
     * from an existing template pipeline that is appropriate to the attributeMap values.
     */
    INHERIT_PIPELINE,
    ;
}
