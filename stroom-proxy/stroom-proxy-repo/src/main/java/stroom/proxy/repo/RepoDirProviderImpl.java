package stroom.proxy.repo;

import stroom.util.io.PathCreator;

import com.google.common.base.Strings;

import java.nio.file.Path;
import java.nio.file.Paths;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RepoDirProviderImpl implements RepoDirProvider {

    private final Path repoDir;

    @Inject
    RepoDirProviderImpl(final RepoConfig repoConfig,
                        final PathCreator pathCreator) {
        if (Strings.isNullOrEmpty(repoConfig.getRepoDir())) {
            throw new RuntimeException("No repo directory have been provided in 'repoDir'");
        }

        final String path = pathCreator.replaceSystemProperties(repoConfig.getRepoDir());
        this.repoDir = Paths.get(path);
    }

    @Override
    public Path get() {
        return repoDir;
    }
}
