package stroom.proxy.repo;

import stroom.util.io.FileUtil;
import stroom.util.time.StroomDuration;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class MockProxyRepoConfig extends ProxyRepoConfig {

    private boolean storingEnabled = false;
    private final String repoDir;
    private String format = "${pathId}/${id}";
    private StroomDuration lockDeleteAge = StroomDuration.of(Duration.ofHours(1));
    private StroomDuration dirCleanDelay = StroomDuration.of(Duration.ofSeconds(10));

    @Inject
    public MockProxyRepoConfig() throws IOException {
        repoDir = FileUtil.getCanonicalPath(Files.createTempDirectory("stroom-proxy-repo"));
    }

    @Override
    public boolean isStoringEnabled() {
        return storingEnabled;
    }

    public void setStoringEnabled(final boolean storingEnabled) {
        this.storingEnabled = storingEnabled;
    }

    @Override
    public String getRepoDir() {
        return repoDir;
    }

    @Override
    public String getFormat() {
        return format;
    }

    public void setFormat(final String format) {
        this.format = format;
    }

    @Override
    public StroomDuration getLockDeleteAge() {
        return lockDeleteAge;
    }

    public void setLockDeleteAge(final StroomDuration lockDeleteAge) {
        this.lockDeleteAge = lockDeleteAge;
    }

    @Override
    public StroomDuration getDirCleanDelay() {
        return dirCleanDelay;
    }

    public void setDirCleanDelay(final StroomDuration dirCleanDelay) {
        this.dirCleanDelay = dirCleanDelay;
    }
}
