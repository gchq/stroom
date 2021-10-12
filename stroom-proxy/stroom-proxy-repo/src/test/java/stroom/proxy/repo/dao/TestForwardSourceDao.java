package stroom.proxy.repo.dao;

import stroom.proxy.repo.ProxyRepoTestModule;
import stroom.proxy.repo.dao.SourceDao.Source;

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
        Optional<Long> id = sourceDao.getSourceId("test");
        assertThat(id.isPresent()).isFalse();

        final Source source = sourceDao
                .addSource("test", "test", "test", System.currentTimeMillis())
                .orElse(null);
        assertThat(source).isNotNull();

        id = sourceDao.getSourceId("test");
        assertThat(id.isPresent()).isTrue();

        assertThat(source.getSourceId()).isEqualTo(id.get());
        assertThat(sourceDao.getDeletableSources(10).size()).isZero();

        final int forwardUrlId = forwardUrlDao.getForwardUrlId("test");
        forwardSourceDao.createForwardSourceRecord(forwardUrlId, source.getSourceId(), true, null);
        assertThat(sourceDao.getDeletableSources(10).size()).isZero();
        forwardSourceDao.setForwardSuccess(source.getSourceId());
        assertThat(sourceDao.getDeletableSources(10).size()).isOne();
    }
}
