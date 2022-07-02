package stroom.proxy.repo.dao;

import stroom.proxy.repo.ProxyRepoTestModule;
import stroom.proxy.repo.queue.BatchUtil;

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
    private ForwardDestDao forwardDestDao;

    @BeforeEach
    void beforeEach() {
        sourceDao.clear();
        forwardSourceDao.clear();
        forwardDestDao.clear();
    }

    @Test
    void testForwardSource() {
        assertThat(sourceDao.countSources()).isZero();
        assertThat(forwardSourceDao.countForwardSource()).isZero();
        assertThat(forwardDestDao.countForwardDest()).isZero();
//        assertThat(sourceDao.pathExists("test")).isFalse();

        sourceDao.addSource(1L, "test", "test");
        assertThat(sourceDao.countDeletableSources()).isZero();

        // Create forward sources.
        forwardDestDao.getForwardDestId("test");
        assertThat(forwardDestDao.countForwardDest()).isOne();
        BatchUtil.transfer(
                () -> sourceDao.getNewSources(0, TimeUnit.MILLISECONDS),
                batch -> forwardSourceDao.createForwardSources(batch,
                        forwardDestDao.getAllForwardDests())
        );

        // Mark all as forwarded.
        BatchUtil.transferEach(
                () -> forwardSourceDao.getNewForwardSources(0, TimeUnit.MILLISECONDS),
                forwardSource -> forwardSourceDao.update(forwardSource.copy().tries(1).success(true).build())
        );

        sourceDao.countDeletableSources();
        sourceDao.deleteSources();

        assertThat(forwardSourceDao.countForwardSource()).isZero();
        assertThat(sourceDao.countSources()).isZero();
//        assertThat(sourceDao.pathExists("test")).isFalse();
    }
}
