package stroom.test;

import stroom.importexport.impl.ImportExportService;
import stroom.test.common.StroomCoreServerTestFileUtil;
import stroom.util.io.FileUtil;
import stroom.util.shared.Version;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * This class should be used when integration tests require stroom content that is available as
 * released content packs from the stroom-content git repo.
 *
 * The setupSampleData gradle task should be used when you need stroom content for manual testing
 * inside stroom. See {@link SetupSampleData} for details.
 */
public class ContentImportService {

    public static final String CONTENT_PACK_IMPORT_DIR = "transientContentPacks";

    public enum ContentPackName {
        CORE_XML_SCHEMAS("core-xml-schemas"),
        EVENT_LOGGING_XML_SCHEMA("event-logging-xml-schema"),
        TEMPLATE_PIPELINES("template-pipelines"),
        STANDARD_PIPELINES("standard-pipelines");

        private final String packName;

        ContentPackName(final String packName) {
            this.packName = packName;
        }

        String getPackName() {
            return packName;
        }
    }

    public static final ContentPack CORE_XML_SCHEMAS_PACK = ContentPack.of(
            ContentPackName.CORE_XML_SCHEMAS,
            Version.of(1, 1));

    public static final ContentPack EVENT_LOGGING_XML_SCHEMA_PACK = ContentPack.of(
            ContentPackName.EVENT_LOGGING_XML_SCHEMA,
            Version.of(3, 1, 1));

    public static final ContentPack TEMPLATE_PIPELINES_PACK = ContentPack.of(
            ContentPackName.TEMPLATE_PIPELINES,
            Version.of(0, 2));

    public static final ContentPack STANDARD_PIPELINES_PACK = ContentPack.of(
            ContentPackName.STANDARD_PIPELINES,
            Version.of(0, 1));

    private static final Version VISUALISATIONS_VERSION = Version.of(3, 0, 4);

    private ImportExportService importExportService;

    @Inject
    ContentImportService(final ImportExportService importExportService) {
        this.importExportService = importExportService;
    }

    /**
     * Imports standard packs, i.e. all the schemas and standard/template pipelines
     */
    public void importStandardPacks() {
        importContentPacks(Arrays.asList(
                CORE_XML_SCHEMAS_PACK,
                EVENT_LOGGING_XML_SCHEMA_PACK,
                TEMPLATE_PIPELINES_PACK,
                STANDARD_PIPELINES_PACK
        ));
    }

    public void importVisualisations() {

        final Path contentPackDirPath = getContentPackDirPath();

        final Path packPath = VisualisationsDownloader.downloadVisualisations(
                VISUALISATIONS_VERSION, contentPackDirPath);
        importExportService.performImportWithoutConfirmation(packPath);
    }

    public void importContentPacks(final List<ContentPack> packs) {

        packs.forEach(pack -> {
            Path packPath = ContentPackDownloader.downloadContentPack(
                    pack.getNameAsStr(), pack.getVersion(), getContentPackDirPath());
            importExportService.performImportWithoutConfirmation(packPath);
        });
    }

    private Path getContentPackDirPath() {
        final Path contentPackDir = StroomCoreServerTestFileUtil.getTestResourcesDir()
                .resolve(CONTENT_PACK_IMPORT_DIR);
        try {
            Files.createDirectories(contentPackDir);
        } catch (final IOException e) {
            throw new UncheckedIOException(String.format("Error creating directory %s for content packs",
                    FileUtil.getCanonicalPath(contentPackDir)), e);
        }
        return contentPackDir;
    }

    public static class ContentPack {
        private final ContentPackName name;
        private final Version version;

        public ContentPack(final ContentPackName name, final Version version) {
            this.name = name;
            this.version = version;
        }

        public static ContentPack of(final ContentPackName name, final Version version) {
            return new ContentPack(name, version);
        }

        public String getNameAsStr() {
            return name.getPackName();
        }

        ContentPackName getName() {
            return name;
        }

        public Version getVersion() {
            return version;
        }
    }
}
