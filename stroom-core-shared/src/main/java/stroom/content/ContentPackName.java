package stroom.content;

public enum ContentPackName {
    CORE_XML_SCHEMAS("core-xml-schemas"),
    EVENT_LOGGING_XML_SCHEMA("event-logging-xml-schema"),
    TEMPLATE_PIPELINES("template-pipelines"),
    STANDARD_PIPELINES("standard-pipelines");

    private final String packName;

    ContentPackName(final String packName) {
        this.packName = packName;
    }

    public String getPackName() {
        return packName;
    }
}
