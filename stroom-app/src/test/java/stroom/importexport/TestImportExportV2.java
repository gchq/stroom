package stroom.importexport;

import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerNode;
import stroom.feed.api.FeedStore;
import stroom.importexport.api.ImportExportSerializer;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportSettings.ImportMode;
import stroom.pipeline.PipelineStore;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.test.common.StroomCoreServerTestFileUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TestImportExportV2 extends AbstractCoreIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestImportExportV2.class);

    @SuppressWarnings("unused")
    @Inject
    private CommonTestControl commonTestControl;

    @Inject
    @SuppressWarnings("unused")
    private ImportExportSerializer importExportSerializer;

    @SuppressWarnings("unused")
    @Inject
    private FeedStore feedStore;

    @SuppressWarnings("unused")
    @Inject
    private PipelineStore pipelineStore;

    @SuppressWarnings("unused")
    @Inject
    private ExplorerNodeService explorerNodeService;

    @SuppressWarnings("unused")
    @Inject
    private ExplorerService explorerService;

    /**
     * Tests importing a subtree of the files. This wouldn't work with the V1 import/export
     * code but should work fine with V2.
     */
    @Test
    void testImportSubtree() {
        final Path inDir = StroomCoreServerTestFileUtil.getTestResourcesDir().resolve("samples/config-v2");
        final Path subDir1 = inDir.resolve("Feeds_and_Translations.Folder.7e38afb4-b3ff-456c-a225-9887ffb6935d");
        final Path subDir2 = subDir1.resolve("Internal.Folder.9e1cbd84-47f0-45ea-8c49-a751fc212147");

        final ExplorerNode systemNode = explorerNodeService.getRoot();
        // The Descendants returned includes the System Node so count is 1, not 0
        assertThat(explorerNodeService.getDescendants(systemNode.getDocRef()).size())
                .as("System should not have any children")
                .isEqualTo(1);

        final ImportSettings importSettings = ImportSettings.builder()
                .importMode(ImportMode.IGNORE_CONFIRMATION)
                .useImportFolders(true)
                .useImportNames(true)
                .rootDocRef(systemNode.getDocRef())
                .build();

        importExportSerializer.read(subDir2,
                null,
                importSettings);

        final List<ExplorerNode> children = explorerNodeService.getChildren(systemNode.getDocRef());
        assertThat(children.size())
                .as("Should be two nodes imported")
                .isEqualTo(2);
        final List<ExplorerNode> decorationNodes = explorerNodeService.getNodesByName(systemNode, "DECORATION");
        assertThat(decorationNodes.size())
                .as("Should be one DECORATION node")
                .isEqualTo(1);
        final List<ExplorerNode> outputNodes = explorerNodeService.getNodesByName(systemNode, "OUTPUT");
        assertThat(outputNodes.size())
                .as("Should be one OUTPUT node")
                .isEqualTo(1);
    }

    @Test
    public void testImportV1Folders() {
        final Path inDirV1 = StroomCoreServerTestFileUtil.getTestResourcesDir().resolve(
                "samples/config-feeds-internal-v1");
        final Path inDirV2 = StroomCoreServerTestFileUtil.getTestResourcesDir().resolve(
                "samples/config-feeds-internal-v2");

        final ExplorerNode systemNode = explorerNodeService.getRoot();
        // The Descendants returned includes the System Node so count is 1, not 0
        assertThat(explorerNodeService.getDescendants(systemNode.getDocRef()).size())
                .as("System should not have any children")
                .isEqualTo(1);

        final ImportSettings importSettings = ImportSettings.builder()
                .importMode(ImportMode.IGNORE_CONFIRMATION)
                .useImportFolders(true)
                .useImportNames(true)
                .rootDocRef(systemNode.getDocRef())
                .build();

        // Do the V1 import
        LOGGER.info("================= Read V1 {}", inDirV1);
        importExportSerializer.read(inDirV1,
                null,
                importSettings);

        // This is the UUID of the folder on disk in V2
        final String uuidInV2OnDisk = "9e1cbd84-47f0-45ea-8c49-a751fc212147";

        final List<ExplorerNode> childrenV1 = explorerNodeService.getChildren(systemNode.getDocRef());
        for (final ExplorerNode node : childrenV1) {
            LOGGER.info("{}", node.getDocRef());
        }
        assertThat(childrenV1.size())
                .as("Should be one folder imported under System")
                .isEqualTo(1);
        final ExplorerNode internalFolderV1 = childrenV1.getFirst();
        final String uuidInV1InStroom = internalFolderV1.getDocRef().getUuid();
        assertThat(uuidInV1InStroom)
                .as("V1 import should change UUID")
                .isNotEqualTo(uuidInV2OnDisk);

        // Check that the correct nodes are in the Folder - should only be one
        final List<ExplorerNode> folderChildrenV1 = explorerNodeService.getChildren(internalFolderV1.getDocRef());
        assertThat(folderChildrenV1.size())
                .as("Should be one object under Internal folder")
                .isEqualTo(1);

        // Do a V2 import over the top
        LOGGER.info("================= Read V2 {}", inDirV2);
        importExportSerializer.read(inDirV2,
                null,
                importSettings);
        final List<ExplorerNode> childrenV2 = explorerNodeService.getChildren(systemNode.getDocRef());
        assertThat(childrenV2.size())
                .as("Should be one folder imported under System")
                .isEqualTo(1);
        final ExplorerNode internalFolderV2 = childrenV1.getFirst();
        final String uuidInV2InStroom = internalFolderV2.getDocRef().getUuid();
        assertThat(uuidInV2InStroom)
                .as("V2 import should not change UUID in Stroom from V1 import")
                .isNotEqualTo(uuidInV2OnDisk);
        assertThat(uuidInV2InStroom)
                .as("V2 UUID should match V1 UUID")
                .isEqualTo(uuidInV1InStroom);

        // Check that the correct nodes are in the Folder - should be one more
        final List<ExplorerNode> folderChildrenV2 = explorerNodeService.getChildren(internalFolderV2.getDocRef());
        assertThat(folderChildrenV2.size())
                .as("Should be two objects under Internal folder")
                .isEqualTo(2);
    }

    @Test
    public void testImportV2Folders() {
        final Path inDirV2 = StroomCoreServerTestFileUtil.getTestResourcesDir().resolve(
                "samples/config-feeds-internal-v2");

        final ExplorerNode systemNode = explorerNodeService.getRoot();
        // The Descendants returned includes the System Node so count is 1, not 0
        assertThat(explorerNodeService.getDescendants(systemNode.getDocRef()).size())
                .as("System should not have any children")
                .isEqualTo(1);

        final ImportSettings importSettings = ImportSettings.builder()
                .importMode(ImportMode.IGNORE_CONFIRMATION)
                .useImportFolders(true)
                .useImportNames(true)
                .rootDocRef(systemNode.getDocRef())
                .build();

        // Do the V2 import
        LOGGER.info("================= Read V2 {}", inDirV2);
        importExportSerializer.read(inDirV2,
                null,
                importSettings);

        // This is the UUID of the folder on disk in V2
        final String uuidInV2OnDisk = "9e1cbd84-47f0-45ea-8c49-a751fc212147";

        final List<ExplorerNode> childrenV2 = explorerNodeService.getChildren(systemNode.getDocRef());
        assertThat(childrenV2.size())
                .as("Should be one folder imported under System")
                .isEqualTo(1);
        final ExplorerNode internalFolderV2 = childrenV2.getFirst();
        final String uuidInV2InStroom = internalFolderV2.getDocRef().getUuid();
        assertThat(uuidInV2InStroom)
                .as("V2 import UUID should match disk UUID")
                .isEqualTo(uuidInV2OnDisk);

        // Check that the correct nodes are in the Folder - should be one more
        final List<ExplorerNode> folderChildrenV2 = explorerNodeService.getChildren(internalFolderV2.getDocRef());
        assertThat(folderChildrenV2.size())
                .as("Should be two objects under Internal folder")
                .isEqualTo(2);
    }

    @Test
    public void testImportWithRename() {
        final Path inDirV2 = StroomCoreServerTestFileUtil.getTestResourcesDir().resolve(
                "samples/config-feeds-internal-v2");

        final ExplorerNode systemNode = explorerNodeService.getRoot();
        // The Descendants returned includes the System Node so count is 1, not 0
        assertThat(explorerNodeService.getDescendants(systemNode.getDocRef()).size())
                .as("System should not have any children")
                .isEqualTo(1);

        final ImportSettings importSettings = ImportSettings.builder()
                .importMode(ImportMode.IGNORE_CONFIRMATION)
                .useImportFolders(true)
                .useImportNames(true)
                .rootDocRef(systemNode.getDocRef())
                .build();

        // Do the V2 import
        LOGGER.info("================= Read V2 {}", inDirV2);
        importExportSerializer.read(inDirV2,
                null,
                importSettings);

        final List<ExplorerNode> children1 = explorerNodeService.getChildren(systemNode.getDocRef());
        assertThat(children1.size())
                .as("Should be one folder imported under System")
                .isEqualTo(1);
        final ExplorerNode internalFolder1 = children1.getFirst();

        final DocRef internalFolderDocRef = internalFolder1.getDocRef();
        internalFolderDocRef.setName("FooBar");
        explorerNodeService.renameNode(internalFolderDocRef);

        // Do the import again, renaming items to the import value
        importExportSerializer.read(inDirV2,
                null,
                importSettings);

        final List<ExplorerNode> childrenV2 = explorerNodeService.getChildren(systemNode.getDocRef());
        assertThat(childrenV2.size())
                .as("Should be one folder imported under System")
                .isEqualTo(1);
        final ExplorerNode internalFolderV2 = childrenV2.getFirst();
        assertThat(internalFolderV2.getDocRef().getName())
                .as("Name of folder must not be FooBar")
                .isNotEqualTo("FooBar");
        assertThat(internalFolderV2.getDocRef().getName())
                .as("Name of folder must be FooBar")
                .isEqualTo("Internal");
    }

    @Test
    public void testImportWithoutRename() {
        final Path inDirV2 = StroomCoreServerTestFileUtil.getTestResourcesDir().resolve(
                "samples/config-feeds-internal-v2");

        final ExplorerNode systemNode = explorerNodeService.getRoot();
        // The Descendants returned includes the System Node so count is 1, not 0
        assertThat(explorerNodeService.getDescendants(systemNode.getDocRef()).size())
                .as("System should not have any children")
                .isEqualTo(1);

        final ImportSettings importSettings = ImportSettings.builder()
                .importMode(ImportMode.IGNORE_CONFIRMATION)
                .useImportFolders(true)
                .useImportNames(true)
                .rootDocRef(systemNode.getDocRef())
                .build();

        // Do the V2 import
        LOGGER.info("================= Read V2 {}", inDirV2);
        importExportSerializer.read(inDirV2,
                null,
                importSettings);

        final List<ExplorerNode> children1 = explorerNodeService.getChildren(systemNode.getDocRef());
        assertThat(children1.size())
                .as("Should be one folder imported under System")
                .isEqualTo(1);
        final ExplorerNode internalFolder1 = children1.getFirst();

        final DocRef internalFolderDocRef = internalFolder1.getDocRef();
        internalFolderDocRef.setName("FooBar");
        explorerNodeService.renameNode(internalFolderDocRef);

        // Do the import again, not renaming items to the import value
        final ImportSettings importSettings2 = ImportSettings.builder()
                .importMode(ImportMode.IGNORE_CONFIRMATION)
                .useImportFolders(true)
                .useImportNames(false)
                .rootDocRef(systemNode.getDocRef())
                .build();

        importExportSerializer.read(inDirV2,
                null,
                importSettings2);

        final List<ExplorerNode> childrenV2 = explorerNodeService.getChildren(systemNode.getDocRef());
        assertThat(childrenV2.size())
                .as("Should be one folder imported under System")
                .isEqualTo(1);
        final ExplorerNode internalFolderV2 = childrenV2.getFirst();
        assertThat(internalFolderV2.getDocRef().getName())
                .as("Name of folder must be FooBar")
                .isEqualTo("FooBar");
        assertThat(internalFolderV2.getDocRef().getName())
                .as("Name of folder must not be FooBar")
                .isNotEqualTo("Internal");
    }

}
