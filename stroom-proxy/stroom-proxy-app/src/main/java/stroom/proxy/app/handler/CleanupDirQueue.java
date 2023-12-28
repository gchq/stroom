package stroom.proxy.app.handler;

import stroom.proxy.repo.RepoDirProvider;
import stroom.util.io.FileUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class CleanupDirQueue implements DirDest {

    private final Path dir;

    @Inject
    CleanupDirQueue(final RepoDirProvider repoDirProvider) {
        dir = repoDirProvider.get().resolve("99_deleting");
        ensureDirExists(dir);
        FileUtil.deleteContents(dir);
    }

    @Override
    public void add(final Path sourceDir) throws IOException {
        // We will move before delete to help ensure we don't end up partially deleting dir contents in place.
        final Path deleteDir = dir.resolve(sourceDir.getFileName());
        Files.move(sourceDir, deleteDir, StandardCopyOption.ATOMIC_MOVE);
        FileUtil.deleteDir(deleteDir);
    }

    private void ensureDirExists(final Path path) {
        if (Files.isDirectory(path)) {
            return;
        }

        try {
            Files.createDirectories(path);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}