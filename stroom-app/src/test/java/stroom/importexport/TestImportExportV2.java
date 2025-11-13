package stroom.importexport;

import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;
import stroom.feed.api.FeedStore;
import stroom.importexport.api.ImportExportSerializer;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportSettings.ImportMode;
import stroom.importexport.shared.ImportState;
import stroom.pipeline.PipelineStore;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.test.common.StroomCoreServerTestFileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class TestImportExportV2 extends AbstractCoreIntegrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestImportExportV2.class);

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
                "samples/config-feeds-internal-4-items-v2");

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
                "samples/config-feeds-internal-4-items-v2");

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
                "samples/config-feeds-internal-4-items-v2");

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
        explorerNodeService.renameNode(internalFolderDocRef.copy().name("FooBar").build());

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
                "samples/config-feeds-internal-4-items-v2");

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
        explorerNodeService.renameNode(internalFolderDocRef.copy().name("FooBar").build());

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

    /**
     * Does the import. Moves the items to a new folder. Does the import again
     * and the items should have moved back to the location from the original import.
     */
    @Test
    public void testImportWithMoveAndMoveBack() {
        final Path inDirV2 = StroomCoreServerTestFileUtil.getTestResourcesDir().resolve(
                "samples/config-feeds-internal-4-items-v2");

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

        final List<ExplorerNode> systemChildren = explorerNodeService.getChildren(systemNode.getDocRef());
        assertThat(systemChildren.size())
                .as("Should be one folder imported under System")
                .isEqualTo(1);
        final ExplorerNode internalFolder = systemChildren.getFirst();

        final List<ExplorerNode> folderChildren = explorerNodeService.getChildren(internalFolder.getDocRef());
        assertThat(folderChildren.size())
                .as("Should have two items under 'Internal Folder'")
                .isEqualTo(2);

        // Create somewhere to move the items to
        final DocRef newFolderDocRef = DocRef.builder()
                .name("New folder")
                .type(ExplorerConstants.FOLDER_TYPE)
                .uuid(UUID.randomUUID().toString())
                .build();
        final ExplorerNode newFolderNode = ExplorerNode.builder()
                .docRef(newFolderDocRef)
                .build();
        explorerNodeService.createNode(newFolderNode.getDocRef(),
                systemNode.getDocRef(),
                PermissionInheritance.DESTINATION);
        explorerService.rebuildTree();

        // Move the items across
        explorerService.move(folderChildren, newFolderNode, PermissionInheritance.DESTINATION);

        // Check the items have moved
        final List<ExplorerNode> folderChildrenAfterMove = explorerNodeService.getChildren(internalFolder.getDocRef());
        assertThat(folderChildrenAfterMove.size())
                .as("Internal folder should not have any children after move")
                .isEqualTo(0);
        final List<ExplorerNode> newFolderChildrenAfterMove =
                explorerNodeService.getChildren(newFolderNode.getDocRef());
        assertThat(newFolderChildrenAfterMove.size())
                .as("New folder should have two children after move")
                        .isEqualTo(2);

        // Do the import again, renaming items to the import value
        final ImportSettings secondImportSettings = ImportSettings.builder()
                .importMode(ImportMode.IGNORE_CONFIRMATION)
                .useImportFolders(true)
                .useImportNames(true)
                .rootDocRef(systemNode.getDocRef())
                .build();
        LOGGER.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> Doing second import");
        importExportSerializer.read(inDirV2,
                null,
                secondImportSettings);

        final List<ExplorerNode> folderChildrenAfterSecondImport =
                explorerNodeService.getChildren(internalFolder.getDocRef());
        assertThat(folderChildrenAfterSecondImport.size())
                .as("Should be two children of original folder")
                .isEqualTo(2);
        final List<ExplorerNode> newFolderChildrenAfterSecondImport =
                explorerNodeService.getChildren(newFolderNode.getDocRef());
        assertThat(newFolderChildrenAfterSecondImport.size())
                .as("New folder should not contain any children after second import")
                .isEqualTo(0);
    }

    /**
     * Does the import. Moves the items to a new folder. Does the import again
     * and the items should stay moved.
     */
    @Test
    public void testImportWithMoveAndStayMoved() {
        final Path inDirV2 = StroomCoreServerTestFileUtil.getTestResourcesDir().resolve(
                "samples/config-feeds-internal-4-items-v2");

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

        final List<ExplorerNode> systemChildren = explorerNodeService.getChildren(systemNode.getDocRef());
        assertThat(systemChildren.size())
                .as("Should be one folder imported under System")
                .isEqualTo(1);
        final ExplorerNode internalFolder = systemChildren.getFirst();

        final List<ExplorerNode> folderChildren = explorerNodeService.getChildren(internalFolder.getDocRef());
        assertThat(folderChildren.size())
                .as("Should have two items under 'Internal Folder'")
                .isEqualTo(2);

        // Create somewhere to move the items to
        final DocRef newFolderDocRef = DocRef.builder()
                .name("New folder")
                .type(ExplorerConstants.FOLDER_TYPE)
                .uuid(UUID.randomUUID().toString())
                .build();
        final ExplorerNode newFolderNode = ExplorerNode.builder()
                .docRef(newFolderDocRef)
                .build();
        explorerNodeService.createNode(newFolderNode.getDocRef(),
                systemNode.getDocRef(),
                PermissionInheritance.DESTINATION);
        explorerService.rebuildTree();

        // Move the items across
        explorerService.move(folderChildren, newFolderNode, PermissionInheritance.DESTINATION);

        // Check the items have moved
        final List<ExplorerNode> folderChildrenAfterMove =
                explorerNodeService.getChildren(internalFolder.getDocRef());
        assertThat(folderChildrenAfterMove.size())
                .as("Internal folder should not have any children after move")
                .isEqualTo(0);
        final List<ExplorerNode> newFolderChildrenAfterMove =
                explorerNodeService.getChildren(newFolderNode.getDocRef());
        assertThat(newFolderChildrenAfterMove.size())
                .as("New folder should have two children after move")
                .isEqualTo(2);

        // Do the import again, renaming items to the import value
        final ImportSettings secondImportSettings = ImportSettings.builder()
                .importMode(ImportMode.IGNORE_CONFIRMATION)
                .useImportFolders(false)
                .useImportNames(true)
                .rootDocRef(systemNode.getDocRef())
                .build();
        LOGGER.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> Doing second import");
        importExportSerializer.read(inDirV2,
                null,
                secondImportSettings);

        final List<ExplorerNode> folderChildrenAfterSecondImport =
                explorerNodeService.getChildren(internalFolder.getDocRef());
        assertThat(folderChildrenAfterSecondImport.size())
                .as("Should be no children in original folder")
                .isEqualTo(0);
        final List<ExplorerNode> newFolderChildrenAfterSecondImport =
                explorerNodeService.getChildren(newFolderNode.getDocRef());
        assertThat(newFolderChildrenAfterSecondImport.size())
                .as("New folder should contain two children after second import")
                .isEqualTo(2);
    }

    /**
     * Test moving and renaming after V1 import so folder UUIDs don't match
     */
    @Test
    public void testImportV1FolderWithRename() {
        final Path inDirV1 = StroomCoreServerTestFileUtil.getTestResourcesDir().resolve(
                "samples/config-feeds-internal-v1");
        final Path inDirV2 = StroomCoreServerTestFileUtil.getTestResourcesDir().resolve(
                "samples/config-feeds-internal-4-items-v2");

        final ExplorerNode systemNode = explorerNodeService.getRoot();
        // The Descendants returned includes the System Node so count is 1, not 0
        assertThat(explorerNodeService.getDescendants(systemNode.getDocRef()).size())
                .as("System should not have any children")
                .isEqualTo(1);

        final ImportSettings importSettingsV1 = ImportSettings.builder()
                .importMode(ImportMode.IGNORE_CONFIRMATION)
                .useImportFolders(true)
                .useImportNames(true)
                .rootDocRef(systemNode.getDocRef())
                .build();

        // Do the V1 import
        LOGGER.info("================= Read V1 {}", inDirV1);
        importExportSerializer.read(inDirV1,
                null,
                importSettingsV1);

        // This is the UUID of the folder on disk in V2
        final String uuidInV2OnDisk = "9e1cbd84-47f0-45ea-8c49-a751fc212147";

        final List<ExplorerNode> systemChildren = explorerNodeService.getChildren(systemNode.getDocRef());
        for (final ExplorerNode node : systemChildren) {
            LOGGER.info("{}", node.getDocRef());
        }
        assertThat(systemChildren.size())
                .as("Should be one folder imported under System")
                .isEqualTo(1);
        final ExplorerNode internalFolderV1 = systemChildren.getFirst();
        final String uuidInV1InStroom = internalFolderV1.getDocRef().getUuid();
        assertThat(uuidInV1InStroom)
                .as("V1 import should change UUID")
                .isNotEqualTo(uuidInV2OnDisk);

        // Check that the correct nodes are in the Folder - should only be one
        final List<ExplorerNode> folderChildrenV1 = explorerNodeService.getChildren(internalFolderV1.getDocRef());
        assertThat(folderChildrenV1.size())
                .as("Should be one object under Internal folder")
                .isEqualTo(1);

        // Rename the folder - now neither name nor UUID match
        final DocRef internalFolderDocRef = internalFolderV1.getDocRef();
        explorerNodeService.renameNode(internalFolderDocRef.copy().name("FooBar").build());

        // Do a V2 import over the top
        LOGGER.info("================= Read V2 {}", inDirV2);
        final ImportSettings importSettingsV2 = ImportSettings.builder()
                .importMode(ImportMode.IGNORE_CONFIRMATION)
                .useImportFolders(true)
                .useImportNames(true)
                .rootDocRef(systemNode.getDocRef())
                .build();
        importExportSerializer.read(inDirV2,
                null,
                importSettingsV2);

        // The existing renamed folder won't be recognised as the name and UUID are different
        // so we should now have two folders under System
        final List<ExplorerNode> systemChildrenV2 = explorerNodeService.getChildren(systemNode.getDocRef());
        assertThat(systemChildrenV2.size())
                .as("Should be two folders imported under System")
                .isEqualTo(2);

        boolean foundInternal = false;
        boolean foundFooBar = false;
        for (final ExplorerNode systemChild : systemChildrenV2) {
            if (systemChild.getName().equals("Internal")) {
                foundInternal = true;
                assertThat(systemChild.getUuid())
                        .as("UUID of 'internal' should match UUID in V2")
                        .isEqualTo(uuidInV2OnDisk);
                final List<ExplorerNode> folderChildrenV2 = explorerNodeService.getChildren(systemChild.getDocRef());
                assertThat(folderChildrenV2.size())
                        .as("Should be two objects under Internal folder")
                        .isEqualTo(2);
            } else if (systemChild.getName().equals("FooBar")) {
                foundFooBar = true;
                assertThat(systemChild.getUuid())
                        .as("UUID of 'FooBar' should not match UUID of V2 import")
                        .isNotEqualTo(uuidInV2OnDisk);
                final List<ExplorerNode> folderChildrenV2 = explorerNodeService.getChildren(systemChild.getDocRef());
                assertThat(folderChildrenV2.size())
                        .as("Should be no objects under FooBar folder")
                        .isEqualTo(0);
            } else {
                assertThat(systemChild.getName())
                        .as("Unrecognised folder '" + systemChild.getName() + "'")
                        .isEqualTo("");
            }
        }

        assertThat(foundInternal)
                .as("Should be a folder named 'Internal'")
                .isEqualTo(true);
        assertThat(foundFooBar)
                .as("Should be a folder named 'Internal'")
                .isEqualTo(true);

    }

    /**
     * Test moving and renaming after V1 import so folder UUIDs don't match
     */
    @Test
    public void testImportV1FolderWithRenameNotImportFolders() {
        final Path inDirV1 = StroomCoreServerTestFileUtil.getTestResourcesDir().resolve(
                "samples/config-feeds-internal-v1");
        final Path inDirV2 = StroomCoreServerTestFileUtil.getTestResourcesDir().resolve(
                "samples/config-feeds-internal-4-items-v2");

        final ExplorerNode systemNode = explorerNodeService.getRoot();
        // The Descendants returned includes the System Node so count is 1, not 0
        assertThat(explorerNodeService.getDescendants(systemNode.getDocRef()).size())
                .as("System should not have any children")
                .isEqualTo(1);

        final ImportSettings importSettingsV1 = ImportSettings.builder()
                .importMode(ImportMode.IGNORE_CONFIRMATION)
                .useImportFolders(true)
                .useImportNames(true)
                .rootDocRef(systemNode.getDocRef())
                .build();

        // Do the V1 import
        LOGGER.info("================= Read V1 {}", inDirV1);
        importExportSerializer.read(inDirV1,
                null,
                importSettingsV1);

        // This is the UUID of the folder on disk in V2
        final String uuidInV2OnDisk = "9e1cbd84-47f0-45ea-8c49-a751fc212147";

        final List<ExplorerNode> systemChildren = explorerNodeService.getChildren(systemNode.getDocRef());
        for (final ExplorerNode node : systemChildren) {
            LOGGER.info("{}", node.getDocRef());
        }
        assertThat(systemChildren.size())
                .as("Should be one folder imported under System")
                .isEqualTo(1);
        final ExplorerNode internalFolderV1 = systemChildren.getFirst();
        final String uuidInV1InStroom = internalFolderV1.getDocRef().getUuid();
        assertThat(uuidInV1InStroom)
                .as("V1 import should change UUID")
                .isNotEqualTo(uuidInV2OnDisk);

        // Check that the correct nodes are in the Folder - should only be one
        final List<ExplorerNode> folderChildrenV1 = explorerNodeService.getChildren(internalFolderV1.getDocRef());
        assertThat(folderChildrenV1.size())
                .as("Should be one object under Internal folder")
                .isEqualTo(1);

        // Rename the folder - now neither name nor UUID match
        final DocRef internalFolderDocRef = internalFolderV1.getDocRef();
        explorerNodeService.renameNode(internalFolderDocRef.copy().name("FooBar").build());

        // Do a V2 import over the top
        LOGGER.info("================= Read V2 {}", inDirV2);
        final ImportSettings importSettingsV2 = ImportSettings.builder()
                .importMode(ImportMode.IGNORE_CONFIRMATION)
                .useImportFolders(false)
                .useImportNames(true)
                .rootDocRef(systemNode.getDocRef())
                .build();
        importExportSerializer.read(inDirV2,
                null,
                importSettingsV2);

        // The existing renamed folder won't be recognised as the name and UUID are different
        // so we should now have two folders under System
        final List<ExplorerNode> systemChildrenV2 = explorerNodeService.getChildren(systemNode.getDocRef());
        assertThat(systemChildrenV2.size())
                .as("Should be two folders imported under System")
                .isEqualTo(2);

        boolean foundInternal = false;
        boolean foundFooBar = false;
        for (final ExplorerNode systemChild : systemChildrenV2) {
            if (systemChild.getName().equals("Internal")) {
                foundInternal = true;
                assertThat(systemChild.getUuid())
                        .as("UUID of 'internal' should match UUID in V2")
                        .isEqualTo(uuidInV2OnDisk);
                final List<ExplorerNode> folderChildrenV2 = explorerNodeService.getChildren(systemChild.getDocRef());
                assertThat(folderChildrenV2.size())
                        .as("Should be one object under Internal folder")
                        .isEqualTo(1);
            } else if (systemChild.getName().equals("FooBar")) {
                foundFooBar = true;
                assertThat(systemChild.getUuid())
                        .as("UUID of 'FooBar' should not match UUID of V2 import")
                        .isNotEqualTo(uuidInV2OnDisk);
                final List<ExplorerNode> folderChildrenV2 = explorerNodeService.getChildren(systemChild.getDocRef());
                assertThat(folderChildrenV2.size())
                        .as("Should be one object under FooBar folder")
                        .isEqualTo(1);
            } else {
                assertThat(systemChild.getName())
                        .as("Unrecognised folder '" + systemChild.getName() + "'")
                        .isEqualTo("");
            }
        }

        assertThat(foundInternal)
                .as("Should be a folder named 'Internal'")
                .isEqualTo(true);
        assertThat(foundFooBar)
                .as("Should be a folder named 'Internal'")
                .isEqualTo(true);

    }

    /**
     * Test moving and renaming after V1 import so folder UUIDs don't match
     */
    @Test
    public void testImportV1FolderWithRenameNotImportNames() {
        final Path inDirV1 = StroomCoreServerTestFileUtil.getTestResourcesDir().resolve(
                "samples/config-feeds-internal-v1");
        final Path inDirV2 = StroomCoreServerTestFileUtil.getTestResourcesDir().resolve(
                "samples/config-feeds-internal-4-items-v2");

        final ExplorerNode systemNode = explorerNodeService.getRoot();
        // The Descendants returned includes the System Node so count is 1, not 0
        assertThat(explorerNodeService.getDescendants(systemNode.getDocRef()).size())
                .as("System should not have any children")
                .isEqualTo(1);

        final ImportSettings importSettingsV1 = ImportSettings.builder()
                .importMode(ImportMode.IGNORE_CONFIRMATION)
                .useImportFolders(true)
                .useImportNames(true)
                .rootDocRef(systemNode.getDocRef())
                .build();

        // Do the V1 import
        LOGGER.info("================= Read V1 {}", inDirV1);
        importExportSerializer.read(inDirV1,
                null,
                importSettingsV1);

        // This is the UUID of the folder on disk in V2
        final String uuidInV2OnDisk = "9e1cbd84-47f0-45ea-8c49-a751fc212147";

        final List<ExplorerNode> systemChildren = explorerNodeService.getChildren(systemNode.getDocRef());
        for (final ExplorerNode node : systemChildren) {
            LOGGER.info("{}", node.getDocRef());
        }
        assertThat(systemChildren.size())
                .as("Should be one folder imported under System")
                .isEqualTo(1);
        final ExplorerNode internalFolderV1 = systemChildren.getFirst();
        final String uuidInV1InStroom = internalFolderV1.getDocRef().getUuid();
        assertThat(uuidInV1InStroom)
                .as("V1 import should change UUID")
                .isNotEqualTo(uuidInV2OnDisk);

        // Check that the correct nodes are in the Folder - should only be one
        final List<ExplorerNode> folderChildrenV1 = explorerNodeService.getChildren(internalFolderV1.getDocRef());
        assertThat(folderChildrenV1.size())
                .as("Should be one object under Internal folder")
                .isEqualTo(1);

        // Rename the folder - now neither name nor UUID match
        final DocRef internalFolderDocRef = internalFolderV1.getDocRef();
        explorerNodeService.renameNode(internalFolderDocRef.copy().name("FooBar").build());

        // Do a V2 import over the top
        LOGGER.info("================= Read V2 {}", inDirV2);
        final ImportSettings importSettingsV2 = ImportSettings.builder()
                .importMode(ImportMode.IGNORE_CONFIRMATION)
                .useImportFolders(true)
                .useImportNames(false)
                .rootDocRef(systemNode.getDocRef())
                .build();
        importExportSerializer.read(inDirV2,
                null,
                importSettingsV2);

        // The existing renamed folder won't be recognised as the name and UUID are different
        // so we should now have two folders under System
        final List<ExplorerNode> systemChildrenV2 = explorerNodeService.getChildren(systemNode.getDocRef());
        assertThat(systemChildrenV2.size())
                .as("Should be two folders imported under System")
                .isEqualTo(2);

        boolean foundInternal = false;
        boolean foundFooBar = false;
        for (final ExplorerNode systemChild : systemChildrenV2) {
            if (systemChild.getName().equals("Internal")) {
                foundInternal = true;
                assertThat(systemChild.getUuid())
                        .as("UUID of 'internal' should match UUID in V2")
                        .isEqualTo(uuidInV2OnDisk);
                final List<ExplorerNode> folderChildrenV2 = explorerNodeService.getChildren(systemChild.getDocRef());
                assertThat(folderChildrenV2.size())
                        .as("Should be two objects under Internal folder")
                        .isEqualTo(2);
            } else if (systemChild.getName().equals("FooBar")) {
                foundFooBar = true;
                assertThat(systemChild.getUuid())
                        .as("UUID of 'FooBar' should not match UUID of V2 import")
                        .isNotEqualTo(uuidInV2OnDisk);
                final List<ExplorerNode> folderChildrenV2 = explorerNodeService.getChildren(systemChild.getDocRef());
                assertThat(folderChildrenV2.size())
                        .as("Should be no objects under FooBar folder")
                        .isEqualTo(0);
            } else {
                assertThat(systemChild.getName())
                        .as("Unrecognised folder '" + systemChild.getName() + "'")
                        .isEqualTo("");
            }
        }

        assertThat(foundInternal)
                .as("Should be a folder named 'Internal'")
                .isEqualTo(true);
        assertThat(foundFooBar)
                .as("Should be a folder named 'Internal'")
                .isEqualTo(true);

    }

    /**
     * Tests importing under mode CREATE_CONFIRMATION.
     * Should give the same results for V1 and V2.
     */
    @Test
    void testImportModeCreateConfirmationImportFoldersImportNames() {
        final Path inDirV2 = StroomCoreServerTestFileUtil.getTestResourcesDir().resolve(
                "samples/config-feeds-internal-2-items-v2");

        final ExplorerNode systemNode = explorerNodeService.getRoot();
        final List<ImportState> importStateListV1 = importFrom(inDirV2,
                ImportMode.CREATE_CONFIRMATION,
                true,
                true,
                systemNode.getDocRef());

        // Wipe down the database
        commonTestControl.clear();

        final List<ImportState> importStateListV2 = importFrom(inDirV2,
                ImportMode.CREATE_CONFIRMATION,
                true,
                true,
                systemNode.getDocRef());

        assertThat(equalImportStateLists(importStateListV1, importStateListV2))
                .as("Import States must be identical for V1 and V2")
                .isEqualTo(true);
    }

    /**
     * Tests importing under mode ACTION_CONFIRMATION.
     * Should give the same results for V1 and V2.
     */
    @Test
    void testImportModeActionConfirmationImportFoldersImportNames() {
        final Path inDirV2 = StroomCoreServerTestFileUtil.getTestResourcesDir().resolve(
                "samples/config-feeds-internal-2-items-v2");

        final ExplorerNode systemNode = explorerNodeService.getRoot();
        final List<ImportState> importStateListV1 = importFrom(inDirV2,
                ImportMode.ACTION_CONFIRMATION,
                true,
                true,
                systemNode.getDocRef());

        // Wipe down the database
        commonTestControl.clear();

        final List<ImportState> importStateListV2 = importFrom(inDirV2,
                ImportMode.ACTION_CONFIRMATION,
                true,
                true,
                systemNode.getDocRef());

        assertThat(equalImportStateLists(importStateListV1, importStateListV2))
                .as("Import States must be identical for V1 and V2")
                .isEqualTo(true);
    }

    /**
     * Tests importing under mode ACTION_CONFIRMATION.
     * Should give the same results for V1 and V2.
     */
    @Test
    void testImportModeActionConfirmationActionWithinFolder() {
        final Path inDirV2 = StroomCoreServerTestFileUtil.getTestResourcesDir().resolve(
                "samples/config-feeds-internal-2-items-v2");

        final ExplorerNode systemNode = explorerNodeService.getRoot();
        final ImportSettings importSettings = ImportSettings.builder()
                .importMode(ImportMode.ACTION_CONFIRMATION)
                .useImportNames(true)
                .useImportFolders(true)
                .rootDocRef(systemNode.getDocRef())
                .build();

        // Create Action to import the Internal/DECORATION XSLT item
        final List<ImportState> importStateList = new ArrayList<>();
        final DocRef decorationDocRef = new DocRef("XSLT",
                "c2b9b9b0-45fe-473a-84b6-936ab1b629b2",
                "DECORATION");
        final ImportState decorationImportState = new ImportState(decorationDocRef,
                "Internal/DECORATION.XSLT");
        decorationImportState.setAction(true);
        importStateList.add(decorationImportState);

        importExportSerializer.read(inDirV2,
                importStateList,
                importSettings);

        final List<ExplorerNode> systemChildren = explorerNodeService.getChildren(systemNode.getDocRef());
        assertThat(systemChildren.size())
                .as("Should be one folder imported under System")
                .isEqualTo(1);
        final ExplorerNode internalFolder = systemChildren.getFirst();

        // Check that the correct nodes are in the Folder - should only be one
        final List<ExplorerNode> folderChildren = explorerNodeService.getChildren(internalFolder.getDocRef());
        assertThat(folderChildren.size())
                .as("Should be one object under Internal folder")
                .isEqualTo(1);
    }

    /**
     * Utility method to import data from a given directory
     * with given settings.
     */
    private List<ImportState> importFrom(final Path dir,
                                         final ImportMode importMode,
                                         final boolean useImportFolders,
                                         final boolean useImportNames,
                                         final DocRef rootDocRef) {
        final ImportSettings importSettings = ImportSettings.builder()
                .importMode(importMode)
                .useImportNames(useImportNames)
                .useImportFolders(useImportFolders)
                .rootDocRef(rootDocRef)
                .build();

        final List<ImportState> importStateList = new ArrayList<>();
        importExportSerializer.read(dir,
                importStateList,
                importSettings);

        return importStateList;
    }

    /**
     * ImportState.equals() only compares the DocRef. For testing
     * we need to compare everything. This function does that.
     */
    private boolean equals(final ImportState is1, final ImportState is2) {
        return Objects.equals(is1.getDocRef(), is2.getDocRef())
               && Objects.equals(is1.getSourcePath(), is2.getSourcePath())
                && Objects.equals(is1.getDestPath(), is2.getDestPath())
                && Objects.equals(is1.getOwnerDocRef(), is2.getOwnerDocRef())
                && Objects.equals(is1.isAction(), is2.isAction())
                && Objects.equals(is1.getMessageList(), is2.getMessageList())
                && Objects.equals(is1.getSeverity(), is2.getSeverity())
                && Objects.equals(is1.getUpdatedFieldList(), is2.getUpdatedFieldList())
                && Objects.equals(is1.getState(), is2.getState());
    }

    /**
     * ImportState.toString() only displays the DocRef. We need to see
     * everything, so this function does that.
     */
    private String toString(final ImportState is) {
        return "DocRef: " + is.getDocRef()
                + "\n    Source Path: " + is.getSourcePath()
                + "\n    Dest Path: " + is.getDestPath()
                + "\n    Owner DocRef: " + is.getOwnerDocRef()
                + "\n    Is Action: " + is.isAction()
                + "\n    Severity: " + is.getSeverity()
                + "\n    Updated field list: " + is.getUpdatedFieldList()
                + "\n    State: " + is.getState()
                + "\n    Message List: " + is.getMessageList();
    }

    private boolean equalImportStateLists(final List<ImportState> iss1, final List<ImportState> iss2) {
        final List<ImportState> missingFrom2 = new ArrayList<>();
        final List<ImportState> missingFrom1 = new ArrayList<>();
        boolean equals = true;

        LOGGER.info("========= COMPARING IMPORT STATE LISTS");
        for (final ImportState is1 : iss1) {
            boolean matched = false;
            for (final ImportState is2 : iss2) {
                LOGGER.info("Comparing {} and {}", is1, is2);
                if (equals(is1, is2)) {
                    LOGGER.info("    Matched!");
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                missingFrom2.add(is1);
                equals = false;
            }
        }

        for (final ImportState is2 : iss2) {
            boolean matched = false;
            for (final ImportState is1 : iss1) {
                if (equals(is1, is2)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                missingFrom1.add(is2);
                equals = false;
            }
        }

        if (!missingFrom1.isEmpty()) {
            LOGGER.info("Missing from 1:");
            for (final ImportState is1 : missingFrom1) {
                LOGGER.info("---- {}", toString(is1));
            }
        }

        if (!missingFrom2.isEmpty()) {
            LOGGER.info("Missing from 2: ");
            for (final ImportState is2 : missingFrom2) {
                LOGGER.info("---- {}", toString(is2));
            }
        }

        return equals;
    }

}
