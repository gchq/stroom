package stroom.proxy.repo.dao;

import stroom.proxy.repo.ProxyRepoTestModule;
import stroom.proxy.repo.RepoSource;

import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(GuiceExtension.class)
@IncludeModule(ProxyRepoTestModule.class)
public class TestSourceDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestSourceDao.class);

    @Inject
    private SourceDao sourceDao;

    @BeforeEach
    void beforeEach() {
        sourceDao.clear();
    }

    @Test
    void testSource() {
        boolean exists = sourceDao.pathExists("test");
        assertThat(exists).isFalse();

        sourceDao.addSource("test", "test", "test", System.currentTimeMillis());
        exists = sourceDao.pathExists("test");
        assertThat(exists).isTrue();
        final Optional<RepoSource> sourceOptional = sourceDao.getNewSource();

        sourceOptional.ifPresent(source -> sourceDao.deleteSource(source.getId()));
        assertThat(sourceDao.countSources()).isZero();

        sourceDao.clear();
    }

    @Disabled
    @Test
    void testAddSourcePerformance() {
        sourceDao.clear();

        long now = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            sourceDao.addSource("test" + i, "test", "test", System.currentTimeMillis());
        }
        LOGGER.info(Duration.of(System.currentTimeMillis() - now, ChronoUnit.MILLIS).toString());
    }
}
