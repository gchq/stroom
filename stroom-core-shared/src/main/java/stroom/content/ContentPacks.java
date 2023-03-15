package stroom.content;

import java.util.List;

public class ContentPacks {

    private static final String CONTENT_RELEASES_URL = "https://github.com/gchq/stroom-content/releases/download/";
    private static final String VISUALISATION_RELEASES_URL =
            "https://github.com/gchq/stroom-visualisations-dev/releases/download/";

    public static final ContentPack CORE_XML_SCHEMAS_PACK = createStandardContentPack(CONTENT_RELEASES_URL,
            "core-xml-schemas",
            "2.2");

    public static final ContentPack EVENT_LOGGING_XML_SCHEMA_PACK = createStandardContentPack(CONTENT_RELEASES_URL,
            "event-logging-xml-schema",
            "3.4.2");

    public static final ContentPack STANDARD_PIPELINES_PACK = createStandardContentPack(CONTENT_RELEASES_URL,
            "standard-pipelines",
            "0.2");

    public static final ContentPack TEMPLATE_PIPELINES_PACK = createStandardContentPack(CONTENT_RELEASES_URL,
            "template-pipelines",
            "0.3");

    //CORE_XML_SCHEMAS_PACK
    //EVENT_LOGGING_XML_SCHEMA_PACK

    public static final ContentPack INTERNAL_DASHBOARDS = createStandardContentPack(CONTENT_RELEASES_URL,
            "internal-dashboards",
            "1.1");

    public static final ContentPack INTERNAL_STATISTICS_SQL = createStandardContentPack(CONTENT_RELEASES_URL,
            "internal-statistics-sql",
            "2.1");

    public static final ContentPack INTERNAL_STATISTICS_STROOM_STATS = createStandardContentPack(CONTENT_RELEASES_URL,
            "internal-statistics-stroom-stats",
            "2.1");

    //STANDARD_PIPELINES_PACK

    public static final ContentPack STROOM_101 = createStandardContentPack(CONTENT_RELEASES_URL,
            "stroom-101",
            "1.0");

    public static final ContentPack STROOM_LOGS = createStandardContentPack(CONTENT_RELEASES_URL,
            "stroom-logs",
            "3.0-beta.1");

    //TEMPLATE_PIPELINES_PACK

    public static final ContentPack VISUALISATIONS = createSVisualisationContentPack(VISUALISATION_RELEASES_URL,
            "visualisations-production",
            "3.12-alpha.1");

    public static final ContentPackCollection SAMPLE_DATA_CONTENT_PACKS = new ContentPackCollection(
            List.of(
                    CORE_XML_SCHEMAS_PACK,
                    EVENT_LOGGING_XML_SCHEMA_PACK,
                    INTERNAL_DASHBOARDS,
                    INTERNAL_STATISTICS_SQL,
                    INTERNAL_STATISTICS_STROOM_STATS,
                    STANDARD_PIPELINES_PACK,
                    STROOM_101,
                    STROOM_LOGS,
                    TEMPLATE_PIPELINES_PACK,
                    VISUALISATIONS));

    private static ContentPack createStandardContentPack(final String baseUrl,
                                                         final String name,
                                                         final String version) {
        final String url = baseUrl + name + "-v" + version + "/" + name + "-v" + version + ".zip";
        return new ContentPack(url, name, version);
    }

    private static ContentPack createSVisualisationContentPack(final String baseUrl,
                                                               final String name,
                                                               final String version) {
        final String url = baseUrl + "v" + version + "/" + name + "-v" + version + ".zip";
        return new ContentPack(url, name, version);
    }

    private ContentPacks() {
    }
}
