package stroom.proxy.repo;

import stroom.data.zip.CharsetConstants;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.repo.store.Entries;
import stroom.proxy.repo.store.FileSet;
import stroom.proxy.repo.store.SequentialFileStore;
import stroom.util.io.CloseableUtil;
import stroom.util.io.FileUtil;
import stroom.util.logging.Metrics;

import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(GuiceExtension.class)
@IncludeModule(ProxyRepoTestModule.class)
class TestSequentialFileStore {

    @Test
    void test() throws IOException {
        final Path repoDir = FileUtil.createTempDirectory("stroom").resolve("repo1");
        final SequentialFileStore fileStore = new SequentialFileStore(() -> repoDir);

        addFile(fileStore);
        addFile(fileStore);

        // Re open.
        final SequentialFileStore reopenFileStore = new SequentialFileStore(() -> repoDir);

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

        FileUtil.delete(repoDir);
    }

    @Test
    void testPerformance() {
        final Path repoDir = FileUtil.createTempDirectory("stroom").resolve("repo1");
        final SequentialFileStore fileStore = new SequentialFileStore(() -> repoDir);

        for (int i = 0; i < 100000; i++) {
            addFile(fileStore);
        }

        assertThat(fileStore.getMaxStoreId()).isEqualTo(100000);

        for (int i = 0; i < 5; i++) {
            addFile(fileStore);
        }

        assertThat(fileStore.getMaxStoreId()).isEqualTo(100005);
    }

    @Test
    void testDelete() {
        final int count = 1_000;
        final Path repoDir = FileUtil.createTempDirectory("stroom").resolve("repo1");
        FileUtil.deleteContents(repoDir);
        final SequentialFileStore fileStore = new SequentialFileStore(() -> repoDir);

        Metrics.measure("Add files", () -> {
            for (int i = 0; i < count; i++) {
                addFile(fileStore);
            }
        });

        assertThat(fileStore.getMaxStoreId()).isEqualTo(count);

        Metrics.measure("Delete files", () -> {

            final AtomicLong sequence = new AtomicLong();

            final int futureCOunt = 1;
            final CompletableFuture[] futures = new CompletableFuture[futureCOunt];
            for (int i = 0; i < futureCOunt; i++) {
                futures[i] = CompletableFuture.runAsync(() -> {
                    try {
                        long id = sequence.getAndIncrement();
                        while (id < count) {
                            fileStore.deleteSource(id);
                            id = sequence.getAndIncrement();
                        }
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
            CompletableFuture.allOf(futures).join();
        });

        Metrics.report();
    }

    private void addFile(final SequentialFileStore fileStore) {
        try {
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
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
