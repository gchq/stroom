package stroom.dashboard.impl.db;

import stroom.dashboard.impl.visualisation.VisualisationAssetDao;
import stroom.visualisation.shared.VisualisationAssets;

import com.google.inject.Guice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * Tests the Visualisation DAO.
 */
@ExtendWith(MockitoExtension.class)
public class TestVisualisationDao {

    @SuppressWarnings("unused")
    @Inject
    private VisualisationAssetDao assetDao;

    @BeforeEach
    void setup() {
        Guice.createInjector(new TestModule()).injectMembers(this);
    }

    @Test
    public void testDao() throws IOException {
        final String ownerDocUuid = "123456";
        final String userUuid = "abcdef";

        VisualisationAssets assets = assetDao.fetchDraftAssets(userUuid, ownerDocUuid);
        assertThat(assets.getAssets().size()).isEqualTo(0);
        assertThat(assets.isDirty()).isEqualTo(false);

        assetDao.updateNewFolder(userUuid, ownerDocUuid, "/assetOne");
        assets = assetDao.fetchDraftAssets(userUuid, ownerDocUuid);
        assertThat(assets.getAssets().size()).isEqualTo(1);
        assertThat(assets.isDirty()).isEqualTo(true);

        assetDao.updateNewFile(userUuid, ownerDocUuid, "/assetTwo/NewFile.txt");
        assets = assetDao.fetchDraftAssets(userUuid, ownerDocUuid);
        assertThat(assets.getAssets().size()).isEqualTo(2);
        assertThat(assets.isDirty()).isEqualTo(true);

        assetDao.saveDraftToLive(userUuid, ownerDocUuid);
        assets = assetDao.fetchDraftAssets(userUuid, ownerDocUuid);
        assertThat(assets.getAssets().size()).isEqualTo(2);
        assertThat(assets.isDirty()).isEqualTo(false);

        assetDao.updateDelete(userUuid, ownerDocUuid, "/assetOne", true);
        assets = assetDao.fetchDraftAssets(userUuid, ownerDocUuid);
        assertThat(assets.isDirty()).isEqualTo(true);
        assertThat(assets.getAssets().size()).isEqualTo(1);

        assetDao.updateRename(userUuid,
                ownerDocUuid,
                "/assetTwo",
                "/root",
                true);
        assets = assetDao.fetchDraftAssets(userUuid, ownerDocUuid);
        assertThat(assets.isDirty()).isEqualTo(true);
        assertThat(assets.getAssets().size()).isEqualTo(1);
        assertThat(assets.getAssets().getFirst().getPath()).isEqualTo("/root/NewFile.txt");

        assetDao.updateContent(userUuid, ownerDocUuid, "/root/NewFile.txt",
                "ABCDEFG".getBytes(StandardCharsets.UTF_8));
        assetDao.saveDraftToLive(userUuid, ownerDocUuid);
        assets = assetDao.fetchDraftAssets(userUuid, ownerDocUuid);
        assertThat(assets.isDirty()).isEqualTo(false);
        assertThat(assets.getAssets().size()).isEqualTo(1);
        assertThat(assets.getAssets().getFirst().getPath()).isEqualTo("/root/NewFile.txt");
        final Path tempFile = Files.createTempFile("TestVisualisationDao", ".tmp");
        try {
            Instant timestamp = assetDao.writeLiveToServletCache("foo",
            "bar",
                    ownerDocUuid,
                    "/root/NewFile.txt",
                    Instant.EPOCH,
                    tempFile);
            final String fileContents = Files.readString(tempFile);
            assertThat(fileContents).isEqualTo("ABCDEFG");
            assertThat(timestamp).isNotEqualTo(Instant.EPOCH);

            timestamp = assetDao.writeLiveToServletCache("foo",
                    "bar",
                    ownerDocUuid,
                    "/does/not/exist.txt",
                    Instant.EPOCH,
                    tempFile);
            assertThat(timestamp).isNull();
            assertThat(Files.readString(tempFile)).isEqualTo("ABCDEFG");
            assertThat(timestamp).isNotEqualTo(Instant.EPOCH);
        } finally {
            Files.delete(tempFile);
        }
    }

    @Test
    void testDelete() throws IOException {
        final String ownerDocUuid = "doc123456";
        final String userUuid = "user789";

        VisualisationAssets assets = assetDao.fetchDraftAssets(userUuid, ownerDocUuid);
        assertThat(assets.getAssets().size()).isEqualTo(0);
        assertThat(assets.isDirty()).isEqualTo(false);

        assetDao.updateNewFolder(userUuid, ownerDocUuid, "/folderToBeDeleted");
        assets = assetDao.fetchDraftAssets(userUuid, ownerDocUuid);
        assertThat(assets.getAssets().size()).isEqualTo(1);
        assertThat(assets.getAssets().getFirst().getPath()).isEqualTo("/folderToBeDeleted/");

        assetDao.updateDelete(userUuid, ownerDocUuid, "/folderToBeDeleted", true);
        assets = assetDao.fetchDraftAssets(userUuid, ownerDocUuid);
        if (!assets.getAssets().isEmpty()) {
            System.err.println("Extra value is '" + assets.getAssets().getFirst().getPath() + "'");
        }
        assertThat(assets.getAssets().size()).isEqualTo(0);
    }

    @Test
    void testCopy() throws IOException {
        final String fromDocUuid = "fromDocId";
        final String fromUserUuid = "abcdef";
        final String toDocUuid = "toDocId";

        VisualisationAssets assets = assetDao.fetchDraftAssets(fromUserUuid, fromDocUuid);
        assertThat(assets.getAssets().size()).isEqualTo(0);
        assertThat(assets.isDirty()).isEqualTo(false);

        assetDao.updateNewFolder(fromUserUuid, fromDocUuid, "/assetOne");
        assets = assetDao.fetchDraftAssets(fromUserUuid, fromDocUuid);
        assertThat(assets.getAssets().size()).isEqualTo(1);
        assertThat(assets.isDirty()).isEqualTo(true);

        assetDao.updateNewFile(fromUserUuid, fromDocUuid, "/assetTwo/NewFile.txt");
        assets = assetDao.fetchDraftAssets(fromUserUuid, fromDocUuid);
        assertThat(assets.getAssets().size()).isEqualTo(2);
        assertThat(assets.isDirty()).isEqualTo(true);

        assetDao.saveDraftToLive(fromUserUuid, fromDocUuid);
        assetDao.copyLiveAssets(fromDocUuid, toDocUuid);

        final VisualisationAssets copiedAssets = assetDao.fetchDraftAssets(fromUserUuid, toDocUuid);
        assertThat(copiedAssets.getAssets().size()).isEqualTo(2);
        assertThat(copiedAssets.isDirty()).isEqualTo(false);

        // Try copying again - should blow up
        try {
            assetDao.copyLiveAssets(fromDocUuid, toDocUuid);
            assertThat(false).isEqualTo(true);
        } catch (final IOException e) {
            // OK it worked as expected
        }
    }

    @Test
    void testDeDupAssets() throws IOException {
        final String ownerDocUuid = "depdupDoc";
        final String userUuid = "dedupUser";

        VisualisationAssets assets = assetDao.fetchDraftAssets(userUuid, ownerDocUuid);
        assertThat(assets.getAssets().size()).isEqualTo(0);
        assertThat(assets.isDirty()).isEqualTo(false);

        // Add in duplicated assets
        assetDao.updateNewFolder(userUuid, ownerDocUuid, "folder");
        assetDao.updateNewFolder(userUuid, ownerDocUuid, "/folder/subfolder");
        assetDao.updateNewFile(userUuid, ownerDocUuid, "/folder/subfolder/file.ext");

        // Draft table contains duplicates
        assets = assetDao.fetchDraftAssets(userUuid, ownerDocUuid);
        assertThat(assets.isDirty()).isEqualTo(true);
        assertThat(assets.getAssets().size()).isEqualTo(3);

        // Save to live
        assetDao.saveDraftToLive(userUuid, ownerDocUuid);
        assets = assetDao.fetchDraftAssets(userUuid, ownerDocUuid);
        assertThat(assets.isDirty()).isEqualTo(false);
        assertThat(assets.getAssets().size()).isEqualTo(1);
        assertThat(assets.getAssets().getFirst().getPath()).isEqualTo("/folder/subfolder/file.ext");
    }

    @Test
    void testParentPath() {
        final String fullPath = "/1/2/3/4/5/6.ext";
        final String path5 = VisualisationAssetDaoImpl.getParentPath(fullPath);
        assertThat(path5).isEqualTo("/1/2/3/4/5/");
        final String path4 = VisualisationAssetDaoImpl.getParentPath(path5);
        assertThat(path4).isEqualTo("/1/2/3/4/");
        final String path3 = VisualisationAssetDaoImpl.getParentPath(path4);
        assertThat(path3).isEqualTo("/1/2/3/");
        final String path2 = VisualisationAssetDaoImpl.getParentPath(path3);
        assertThat(path2).isEqualTo("/1/2/");
        final String path1 = VisualisationAssetDaoImpl.getParentPath(path2);
        assertThat(path1).isEqualTo("/1/");
        final String path0 = VisualisationAssetDaoImpl.getParentPath(path1);
        assertThat(path0).isEqualTo("");
    }

    @Test
    void testIndexAssetExists() throws IOException {
        final String ownerDocUuid = "iaeDoc";
        final String userUuid = "iaeUser";

        final VisualisationAssets assets = assetDao.fetchDraftAssets(userUuid, ownerDocUuid);
        assertThat(assets.getAssets().size()).isEqualTo(0);
        assertThat(assets.isDirty()).isEqualTo(false);
        boolean indexAssetExists = assetDao.indexAssetExists(ownerDocUuid);
        assertThat(indexAssetExists).isEqualTo(false);

        // Add the index asset
        assetDao.updateNewFile(userUuid, ownerDocUuid, "/index.html");

        // Not saved yet so still doesn't exist
        indexAssetExists = assetDao.indexAssetExists(ownerDocUuid);
        assertThat(indexAssetExists).isEqualTo(false);

        // Save it and check the asset now exists
        assetDao.saveDraftToLive(userUuid, ownerDocUuid);
        indexAssetExists = assetDao.indexAssetExists(ownerDocUuid);
        assertThat(indexAssetExists).isEqualTo(true);

    }

    @Test
    void testGetDraftContent() throws IOException {
        final String ownerDocUuid = "draftContentDoc";
        final String userUuid = "draftContentUser";

        // 1. File doesn't exist
        final String missingContent = assetDao.getDraftContent(userUuid, ownerDocUuid, "/missing.txt");
        assertThat(missingContent).isNull();

        // 2. Normal file size
        assetDao.updateNewFile(userUuid, ownerDocUuid, "/smalld.txt");
        final String testStr = "small content";
        assetDao.updateContent(userUuid, ownerDocUuid, "/smalld.txt", testStr.getBytes(StandardCharsets.UTF_8));
        final String fetchedContent = assetDao.getDraftContent(userUuid, ownerDocUuid, "/smalld.txt");
        assertThat(fetchedContent).isEqualTo(testStr);

        // 3. File too big
        assetDao.updateNewFile(userUuid, ownerDocUuid, "/bigd.txt");
        final byte[] bigData = new byte[(1024 * 512) + 1]; // Just over the 512KiB limit
        // 'A'
        Arrays.fill(bigData, (byte) 65);
        assetDao.updateContent(userUuid, ownerDocUuid, "/bigd.txt", bigData);
        
        try {
            assetDao.getDraftContent(userUuid, ownerDocUuid, "/bigd.txt");
            assertThat(false).as("Expected DataTooBigException").isTrue();
        } catch (final RuntimeException e) {
            // Success
        }
    }

    @Test
    void testGetDraftContentFromLive() throws IOException {
        final String ownerDocUuid = "draftContentDoc";
        final String userUuid = "draftContentUser";

        // 1. File doesn't exist
        final String missingContent = assetDao.getDraftContent(userUuid, ownerDocUuid, "/missing.txt");
        assertThat(missingContent).isNull();

        // 2. Normal file size
        assetDao.updateNewFile(userUuid, ownerDocUuid, "/smalll.txt");
        final String testStr = "small content";
        assetDao.updateContent(userUuid, ownerDocUuid, "/smalll.txt", testStr.getBytes(StandardCharsets.UTF_8));
        assetDao.saveDraftToLive(userUuid, ownerDocUuid);
        final String fetchedContent = assetDao.getDraftContent(userUuid, ownerDocUuid, "/smalll.txt");
        assertThat(fetchedContent).isEqualTo(testStr);

        // 3. File too big
        assetDao.updateNewFile(userUuid, ownerDocUuid, "/bigl.txt");
        final byte[] bigData = new byte[(1024 * 512) + 1]; // Just over the 512KiB limit
        // 'A'
        Arrays.fill(bigData, (byte) 65);
        assetDao.updateContent(userUuid, ownerDocUuid, "/bigl.txt", bigData);
        assetDao.saveDraftToLive(userUuid, ownerDocUuid);

        try {
            assetDao.getDraftContent(userUuid, ownerDocUuid, "/bigl.txt");
            assertThat(false).as("Expected DataTooBigException").isTrue();
        } catch (final RuntimeException e) {
            // Success
        }
    }
}
