package stroom.content;

import stroom.util.shared.Version;

public class ContentPacks {

    public static final ContentPack CORE_XML_SCHEMAS_PACK = ContentPack.of(
    ContentPackName.CORE_XML_SCHEMAS,
            Version.of(2, 2));

    public static final ContentPack EVENT_LOGGING_XML_SCHEMA_PACK = ContentPack.of(
            ContentPackName.EVENT_LOGGING_XML_SCHEMA,
            Version.of(3, 4, 2));

    public static final ContentPack STANDARD_PIPELINES_PACK = ContentPack.of(
            ContentPackName.STANDARD_PIPELINES,
            Version.of(0, 2));

    public static final ContentPack TEMPLATE_PIPELINES_PACK = ContentPack.of(
            ContentPackName.TEMPLATE_PIPELINES,
            Version.of(0, 3));

    private ContentPacks() {
    }
}
