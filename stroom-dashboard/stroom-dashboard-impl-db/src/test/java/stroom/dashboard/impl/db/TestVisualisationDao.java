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
}
