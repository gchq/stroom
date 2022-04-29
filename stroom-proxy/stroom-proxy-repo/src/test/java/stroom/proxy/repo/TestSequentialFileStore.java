package stroom.proxy.repo;

import stroom.data.zip.CharsetConstants;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.repo.store.Entries;
import stroom.proxy.repo.store.FileSet;
import stroom.proxy.repo.store.SequentialFileStore;
import stroom.util.io.CloseableUtil;
import stroom.util.io.FileUtil;

import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(GuiceExtension.class)
@IncludeModule(ProxyRepoTestModule.class)
class TestSequentialFileStore {

    @Inject
    private RepoSources proxyRepoSources;

    @BeforeEach
    void beforeEach() {
        proxyRepoSources.clear();
    }

    @Test
    void test() throws IOException {
        final Path repoDir = FileUtil.createTempDirectory("stroom").resolve("repo1");
        final SequentialFileStore fileStore = new SequentialFileStore(
                () -> repoDir,
                proxyRepoSources);

        addFile(fileStore);
        addFile(fileStore);

        // Re open.
        final SequentialFileStore reopenFileStore = new SequentialFileStore(
                () -> repoDir,
                proxyRepoSources);

        final FileSet fileSet1 = reopenFileStore.getStoreFileSet(1L);
        final FileSet fileSet2 = reopenFileStore.getStoreFileSet(2L);
        assertThat(Files.exists(fileSet1.getZip())).isTrue();
        assertThat(Files.exists(fileSet2.getZip())).isTrue();

        reopenFileStore.deleteSource(1L);
        assertThat(Files.exists(fileSet1.getZip())).isFalse();
        reopenFileStore.deleteSource(2L);
        assertThat(Files.exists(fileSet2.getZip())).isFalse();

        final FileSet fileSet3 = reopenFileStore.getStoreFileSet(3L);
        assertThat(Files.exists(fileSet3.getZip())).isFalse();
        addFile(fileStore);
        assertThat(Files.exists(fileSet3.getZip())).isTrue();
    }

    @Test
    void testPerformance() throws IOException {
        final Path repoDir = FileUtil.createTempDirectory("stroom").resolve("repo1");
        final SequentialFileStore fileStore = new SequentialFileStore(
                () -> repoDir,
                proxyRepoSources);

        for (int i = 0; i < 100000; i++) {
            addFile(fileStore);
        }

        assertThat(fileStore.getMaxStoreId()).isEqualTo(100000);

        for (int i = 0; i < 5; i++) {
            addFile(fileStore);
        }

        assertThat(fileStore.getMaxStoreId()).isEqualTo(100005);
    }

    private void addFile(final SequentialFileStore fileStore) throws IOException {
        final AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.addFeedAndType(attributeMap, "test", null);
        try (final Entries entries = fileStore.getEntries(attributeMap)) {
            OutputStream outputStream = null;
            try {
                outputStream = entries.addEntry("file");
                outputStream.write("SOME_DATA".getBytes(CharsetConstants.DEFAULT_CHARSET));
            } finally {
                CloseableUtil.close(outputStream);
            }
        }
    }
}
