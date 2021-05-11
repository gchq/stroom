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
public class TestSourceDao {

    @Inject
    private SourceDao sourceDao;

    @BeforeEach
    void beforeEach() {
        sourceDao.clear();
    }

    @Test
    void testSource() {
        Optional<Long> id = sourceDao.getSourceId("test");
        assertThat(id.isPresent()).isFalse();

        Source source = sourceDao.addSource("test", "test", "test", System.currentTimeMillis());
        assertThat(source).isNotNull();

        id = sourceDao.getSourceId("test");
        assertThat(id.isPresent()).isTrue();

        assertThat(source.getSourceId()).isEqualTo(id.get());

        sourceDao.setForwardSuccess(id.get());
        sourceDao.setForwardError(id.get());
        sourceDao.resetExamined();
        sourceDao.resetFailedForwards();

        assertThat(sourceDao.getNewSources(10).size()).isOne();
        assertThat(sourceDao.countSources()).isOne();
        assertThat(sourceDao.getCompletedSources(10).size()).isOne();
        assertThat(sourceDao.getDeletableSources(0).size()).isZero();

        sourceDao.deleteSource(id.get());
        assertThat(sourceDao.countSources()).isZero();

        sourceDao.deleteAll();
    }
}
