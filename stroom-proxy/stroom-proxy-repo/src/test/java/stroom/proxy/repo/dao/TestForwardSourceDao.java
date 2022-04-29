package stroom.proxy.repo.dao;

import stroom.proxy.repo.ProxyRepoTestModule;
import stroom.proxy.repo.queue.BatchUtil;

import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
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
//        assertThat(sourceDao.pathExists("test")).isFalse();

        sourceDao.addSource(1L, "test", "test");
        assertThat(sourceDao.getDeletableSources(1000).size()).isZero();

        // Create forward sources.
        forwardUrlDao.getForwardUrlId("test");
        assertThat(forwardUrlDao.countForwardUrl()).isOne();
        BatchUtil.transfer(
                () -> sourceDao.getNewSources(0, TimeUnit.MILLISECONDS),
                batch -> forwardSourceDao.createForwardSources(batch,
                        forwardUrlDao.getAllForwardUrls())
        );

        // Mark all as forwarded.
        BatchUtil.transferEach(
                () -> forwardSourceDao.getNewForwardSources(0, TimeUnit.MILLISECONDS),
                forwardSource -> forwardSourceDao.update(forwardSource.copy().tries(1).success(true).build())
        );

        sourceDao.getDeletableSources(1000).forEach(s -> sourceDao.deleteSources(Collections.singletonList(s)));

        assertThat(forwardSourceDao.countForwardSource()).isZero();
        assertThat(sourceDao.countSources()).isZero();
//        assertThat(sourceDao.pathExists("test")).isFalse();
    }
}
