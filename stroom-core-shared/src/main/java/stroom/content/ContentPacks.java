package stroom.content;

public class ContentPacks {

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

    public static final ContentPack PLANB = createStandardContentPack("planb");

    public static final ContentPack VISUALISATIONS = createVisualisationContentPack(
            "visualisations-production");

    private static ContentPack createStandardContentPack(final String name) {
        return new ContentPack(name, "source/" + name + "/stroomContent", STROOM_CONTENT_GIT_REPO);
    }

    private static ContentPack createVisualisationContentPack(final String name) {
        return new ContentPack(name, "war/stroom-content", STROOM_VISUALISATION_DEV_GIT_REPO);
    }

    private ContentPacks() {
    }
}
