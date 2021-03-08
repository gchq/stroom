package stroom.proxy.repo;

import stroom.util.io.FileUtil;

import java.io.IOException;
import java.nio.file.Files;
import javax.inject.Inject;

public class MockRepoConfig implements RepoConfig {

    private final String dbDir;
    private final String repoDir;

    @Inject
    public MockRepoConfig() throws IOException {
        dbDir = FileUtil.getCanonicalPath(Files.createTempDirectory("stroom-proxy-db"));
        repoDir = FileUtil.getCanonicalPath(Files.createTempDirectory("stroom-proxy-repo"));
    }

    @Override
    public String getRepoDir() {
        return repoDir;
    }

    @Override
    public String getDbDir() {
        return dbDir;
    }
}
