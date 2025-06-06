package stroom.content;

import java.util.List;

public class ContentPacks {

    private static final String CONTENT_RELEASES_URL = "https://github.com/gchq/stroom-content/releases/download/";
    private static final String VISUALISATION_RELEASES_URL =
            "https://github.com/gchq/stroom-visualisations-dev/releases/download/";

    private static final GitRepo STROOM_CONTENT_GIT_REPO =
            new GitRepo("stroom-content",
                    "https://github.com/gchq/stroom-content.git",
                    "planb",
                    "73bf8b6123c76faa61f4edba4e4bbee42fcf07f5");
    private static final GitRepo STROOM_VISUALISATION_DEV_GIT_REPO =
            new GitRepo("stroom-visualisations-dev",
                    "https://github.com/gchq/stroom-visualisations-dev.git",
                    "7.2",
                    "d95c1588bc13a697a818bffea6ed213239a1f993");

    public static final ContentPack CORE_XML_SCHEMAS_PACK = createStandardContentPack("core-xml-schemas");

    public static final ContentPack EVENT_LOGGING_XML_SCHEMA_PACK =
            createStandardContentPack("event-logging-xml-schema");

    public static final ContentPack STANDARD_PIPELINES_PACK = createStandardContentPack("standard-pipelines");

    public static final ContentPack TEMPLATE_PIPELINES_PACK = createStandardContentPack("template-pipelines");

    public static final ContentPack INTERNAL_DASHBOARDS = createStandardContentPack("internal-dashboards");

    public static final ContentPack INTERNAL_STATISTICS_SQL = createStandardContentPack("internal-statistics-sql");

    public static final ContentPack INTERNAL_STATISTICS_STROOM_STATS = createStandardContentPack(
            "internal-statistics-stroom-stats");

    //STANDARD_PIPELINES_PACK

    public static final ContentPack STROOM_101 = createStandardContentPack("stroom-101");

    public static final ContentPack STROOM_LOGS = createStandardContentPack("stroom-logs");

    public static final ContentPack STATE = createStandardContentPack("state");

    public static final ContentPack PLANB = createStandardContentPack("planb");

    //TEMPLATE_PIPELINES_PACK

    public static final ContentPack VISUALISATIONS = createVisualisationContentPack(
            "visualisations-production");

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
                    STATE,
                    PLANB,
                    TEMPLATE_PIPELINES_PACK,
                    VISUALISATIONS));

    private static ContentPack createStandardContentPack(final String name) {
        return new ContentPack(name, "source/" + name + "/stroomContent", STROOM_CONTENT_GIT_REPO);
    }

    private static ContentPack createVisualisationContentPack(final String name) {
        return new ContentPack(name, "war/stroom-content", STROOM_VISUALISATION_DEV_GIT_REPO);
    }

    private ContentPacks() {
    }
}
