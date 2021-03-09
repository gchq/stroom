package stroom.proxy.repo;

import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Optional;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
            proxyRepoSources.addSource("path_" + i, System.currentTimeMillis());
        }
    }

    @Test
    void testUniquePath() {
        assertThatThrownBy(() -> {
            for (int i = 0; i < 10; i++) {
                proxyRepoSources.addSource("path", System.currentTimeMillis());
            }
        }, "Expected error");
        proxyRepoSources.clear();
        proxyRepoSources.addSource("path", System.currentTimeMillis());
    }

    @Test
    void testGetSourceId() {
        proxyRepoSources.addSource("path", System.currentTimeMillis());
        final Optional<Long> sourceId = proxyRepoSources.getSourceId("path");
        assertThat(sourceId.isPresent()).isTrue();
    }
}
