package stroom.proxy.repo;

import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Optional;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(GuiceExtension.class)
@IncludeModule(ProxyRepoTestModule.class)
public class TestSource {

    @Inject
    private ProxyRepoSources proxyRepoSources;

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
            proxyRepoSources.addSource("path_" + i, "test", null, System.currentTimeMillis(), null);
        }
    }

    @Test
    void testUniquePath() {
        assertThat(proxyRepoSources.addSource(
                        "path",
                        "test",
                        null,
                        System.currentTimeMillis(),
                        null)
                .orElse(null))
                .isNotNull();
        assertThat(proxyRepoSources.addSource(
                        "path",
                        "test",
                        null,
                        System.currentTimeMillis(),
                        null)
                .orElse(null))
                .isNull();
        proxyRepoSources.clear();
        proxyRepoSources.addSource("path", "test", null, System.currentTimeMillis(), null);
    }

    @Test
    void testGetSourceId() {
        proxyRepoSources.addSource("path", "test", null, System.currentTimeMillis(), null);
        final Optional<Long> sourceId = proxyRepoSources.getSourceId("path");
        assertThat(sourceId.isPresent()).isTrue();
    }
}
