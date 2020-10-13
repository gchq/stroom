package stroom.test;

import stroom.content.ContentPack;
import stroom.content.ContentPacks;
import stroom.importexport.impl.ImportExportService;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.shared.Version;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * This class should be used when integration tests require stroom content that is available as
 * released content packs from the stroom-content git repo.
 * <p>
 * The setupSampleData gradle task should be used when you need stroom content for manual testing
 * inside stroom. See {@link SetupSampleData} for details.
 */
public class ContentImportService {

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
                ContentPacks.CORE_XML_SCHEMAS_PACK,
                ContentPacks.EVENT_LOGGING_XML_SCHEMA_PACK,
                ContentPacks.TEMPLATE_PIPELINES_PACK,
                ContentPacks.STANDARD_PIPELINES_PACK
        ));
    }

    public void importVisualisations() {

        final Path contentPackDirPath = FileSystemTestUtil.getContentPackDownloadsDir();

        final Path packPath = VisualisationsDownloader.downloadVisualisations(
                VISUALISATIONS_VERSION, contentPackDirPath);
        importExportService.performImportWithoutConfirmation(packPath);
    }

    public void importContentPacks(final List<ContentPack> packs) {
        packs.forEach(pack -> {
            final Path packPath = ContentPackDownloader.downloadContentPack(
                    pack,
                    FileSystemTestUtil.getContentPackDownloadsDir());

            importExportService.performImportWithoutConfirmation(packPath);
        });
    }


}
