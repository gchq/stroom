package stroom.proxy.repo.dao;

import stroom.proxy.repo.ProxyRepoTestModule;
import stroom.proxy.repo.QueueUtil;

import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(GuiceExtension.class)
@IncludeModule(ProxyRepoTestModule.class)
public class TestForwardSourceDao {

    @Inject
    private SourceDao sourceDao;
    @Inject
    private ForwardSourceDao forwardSourceDao;
    @Inject
    private ForwardUrlDao forwardUrlDao;

    @BeforeEach
    void beforeEach() {
        sourceDao.clear();
        forwardSourceDao.clear();
        forwardUrlDao.clear();
    }

    @Test
    void testForwardSource() {
        assertThat(sourceDao.countSources()).isZero();
        assertThat(forwardSourceDao.countForwardSource()).isZero();
        assertThat(forwardUrlDao.countForwardUrl()).isZero();
        assertThat(sourceDao.pathExists("test")).isFalse();

        sourceDao.addSource("test", "test", "test", System.currentTimeMillis());
        assertThat(sourceDao.getDeletableSources().size()).isZero();

        // Create forward sources.
        forwardUrlDao.getForwardUrlId("test");
        assertThat(forwardUrlDao.countForwardUrl()).isOne();
        QueueUtil.consumeAll(
                () -> sourceDao.getNewSource(0, TimeUnit.MILLISECONDS),
                s -> forwardSourceDao.createForwardSources(s.getId(),
                        forwardUrlDao.getAllForwardUrls())
        );

        // Mark all as forwarded.
        QueueUtil.consumeAll(
                () -> forwardSourceDao.getNewForwardSource(0, TimeUnit.MILLISECONDS),
                forwardSource -> forwardSourceDao.update(forwardSource.copy().tries(1).success(true).build())
        );

        sourceDao.getDeletableSources().forEach(s -> sourceDao.deleteSource(s.getId()));

        assertThat(forwardSourceDao.countForwardSource()).isZero();
        assertThat(sourceDao.countSources()).isZero();
        assertThat(sourceDao.pathExists("test")).isFalse();
    }
}
