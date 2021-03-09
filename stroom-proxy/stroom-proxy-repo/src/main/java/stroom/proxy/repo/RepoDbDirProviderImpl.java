package stroom.proxy.repo;

import stroom.util.io.PathCreator;

import com.google.common.base.Strings;

import java.nio.file.Path;
import java.nio.file.Paths;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RepoDbDirProviderImpl implements RepoDbDirProvider {

    private final Path dbDir;

    @Inject
    RepoDbDirProviderImpl(final RepoConfig repoConfig,
                          final PathCreator pathCreator) {
        if (Strings.isNullOrEmpty(repoConfig.getDbDir())) {
            throw new RuntimeException("No repo DB directory have been provided");
        }

        final String path = pathCreator.replaceSystemProperties(repoConfig.getDbDir());
        this.dbDir = Paths.get(path);
    }

    @Override
    public Path get() {
        return dbDir;
    }
}
