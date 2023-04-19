package stroom.test;

import stroom.content.ContentPack;
import stroom.content.ContentPackCollection;
import stroom.content.ContentPacks;
import stroom.importexport.impl.ImportExportSerializer;
import stroom.importexport.impl.ImportExportService;
import stroom.importexport.shared.ImportSettings;
import stroom.test.common.util.test.ContentPackZipDownloader;
import stroom.test.common.util.test.FileSystemTestUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;

/**
 * This class should be used when integration tests require stroom content that is available as
 * released content packs from the stroom-content git repo.
 * <p>
 * The setupSampleData gradle task should be used when you need stroom content for manual testing
 * inside stroom. See {@link SetupSampleData} for details.
 */
public class ContentImportService {

    private final ImportExportService importExportService;
    private final ImportExportSerializer importExportSerializer;

    @Inject
    ContentImportService(final ImportExportService importExportService,
                         final ImportExportSerializer importExportSerializer) {
        this.importExportService = importExportService;
        this.importExportSerializer = importExportSerializer;
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

    public void importSamplePacks() {
        importContentPacks(Arrays.asList(
                ContentPacks.CORE_XML_SCHEMAS_PACK,
                ContentPacks.EVENT_LOGGING_XML_SCHEMA_PACK,
                ContentPacks.TEMPLATE_PIPELINES_PACK,
                ContentPacks.STANDARD_PIPELINES_PACK
        ));
    }

    private void importContentPacks(final List<ContentPack> packs) {
        packs.forEach(this::importContentPack);
    }

    public void importContentPack(final ContentPack pack) {
        final Path repoPath = ContentPackZipDownloader.downloadContentPack(
                pack, FileSystemTestUtil.getContentPackDownloadsDir());

        final Path subPath = repoPath.resolve(pack.getPath());

        importExportSerializer.read(subPath, new ArrayList<>(), ImportSettings.auto());
    }

    public void importFromDefinitionYaml(final Path definitionYaml) {
        try {
            final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            final ContentPackCollection contentPacks = mapper.readValue(
                    definitionYaml.toFile(),
                    ContentPackCollection.class);
            contentPacks.getContentPacks().forEach(this::importContentPack);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }
}
