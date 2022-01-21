package stroom.proxy.repo;

import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(GuiceExtension.class)
@IncludeModule(ProxyRepoTestModule.class)
public class TestSource {

    @Inject
    private RepoSources proxyRepoSources;

    @BeforeEach
    void beforeEach() {
        proxyRepoSources.clear();
    }

    @BeforeEach
    void cleanup() {
        proxyRepoSources.clear();
    }

    @Test
    void testAddSource() {
        for (int i = 0; i < 10; i++) {
            proxyRepoSources.addSource(
                    "path_" + i,
                    "test",
                    null,
                    System.currentTimeMillis(),
                    null);
        }
    }

    @Test
    void testUniquePath() {
        proxyRepoSources.addSource(
                "path",
                "test",
                null,
                System.currentTimeMillis(),
                null);
        proxyRepoSources.addSource(
                "path",
                "test",
                null,
                System.currentTimeMillis(),
                null);
        proxyRepoSources.clear();
        proxyRepoSources.addSource(
                "path",
                "test",
                null,
                System.currentTimeMillis(),
                null);
    }

    @Test
    void testSourceExists() {
        proxyRepoSources.addSource(
                "path",
                "test",
                null,
                System.currentTimeMillis(),
                null);
        final boolean exists = proxyRepoSources.sourceExists("path");
        assertThat(exists).isTrue();
    }
}
