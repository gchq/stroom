package stroom.proxy.repo;

import stroom.util.io.PathCreator;

import com.google.common.base.Strings;

import java.nio.file.Path;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RepoDbDirProviderImpl implements RepoDbDirProvider {

    private final Path dbDir;

    @Inject
    RepoDbDirProviderImpl(final ProxyDbConfig proxyDbConfig,
                          final PathCreator pathCreator) {
        if (Strings.isNullOrEmpty(proxyDbConfig.getDbDir())) {
            throw new RuntimeException("No repo DB directory have been provided");
        }

        this.dbDir = pathCreator.toAppPath(proxyDbConfig.getDbDir());
    }

    @Override
    public Path get() {
        return dbDir;
    }
}
