package stroom.proxy.repo;

import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.common.base.Strings;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RepoDirProviderImpl implements RepoDirProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RepoDirProviderImpl.class);

    private final Path repoDir;

    @Inject
    RepoDirProviderImpl(final ProxyRepoConfig repoConfig,
                        final PathCreator pathCreator) {
        if (Strings.isNullOrEmpty(repoConfig.getRepoDir())) {
            throw new RuntimeException("No repo directory have been provided in 'repoDir'");
        }

        this.repoDir = pathCreator.toAppPath(repoConfig.getRepoDir());

        try {
            Files.createDirectories(repoDir);
        } catch (final IOException e) {
            LOGGER.error(LogUtil.message(
                    "Failed to create proxy repo directory '{}'. This is configured using " +
                            "property {}. {}",
                    FileUtil.getCanonicalPath(repoDir),
                    repoConfig.getFullPathStr(ProxyRepoConfig.PROP_NAME_REPO_DIR),
                    e.getMessage()));
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Path get() {
        return repoDir;
    }
}
