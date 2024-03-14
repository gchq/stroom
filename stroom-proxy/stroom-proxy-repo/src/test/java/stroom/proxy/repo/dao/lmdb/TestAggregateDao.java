package stroom.proxy.repo.dao.lmdb;

import stroom.proxy.repo.Aggregator;
import stroom.proxy.repo.FeedAndType;
import stroom.proxy.repo.ProxyRepoTestModule;
import stroom.proxy.repo.RepoSource;
import stroom.proxy.repo.RepoSourceItem;

import jakarta.inject.Inject;
import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(GuiceExtension.class)
@IncludeModule(ProxyRepoTestModule.class)
public class TestAggregateDao {

    @Inject
    private LmdbEnv lmdbEnv;
    @Inject
    private FeedDao feedDao;
    @Inject
    private SourceDao sourceDao;
    @Inject
    private SourceItemDao sourceItemDao;
    @Inject
    private AggregateDao aggregateDao;
    @Inject
    private Aggregator aggregator;

    @BeforeEach
    void beforeEach() {
        lmdbEnv.start();
    }

    @AfterEach
    void afterEach() {
        sourceDao.clear();
        sourceItemDao.clear();
        aggregateDao.clear();
        lmdbEnv.stop();
    }

    @Test
    void testAggregate() {
        assertThat(sourceDao.countSources()).isZero();
        assertThat(aggregateDao.countAggregates()).isZero();
//        assertThat(sourceDao.pathExists("test")).isFalse();

        sourceDao.addSource(1L, "test", "test");
        sourceDao.flush();


        final RepoSource source = sourceDao.getNextSource();
        assertThat(source).isNotNull();
        assertThat(source.fileStoreId()).isEqualTo(1L);

        final long feedId = feedDao.getId(new FeedAndType("testFeed", "Raw Events"));
        for (int i = 0; i < 100; i++) {
            final RepoSourceItem sourceItemRecord = new RepoSourceItem(
                    source,
                    i,
                    "item" + i,
                    feedId,
                    -1,
                    0,
                    "dat");
            sourceItemDao.addItem(source, sourceItemRecord);
        }
        sourceDao.setSourceExamined(source.fileStoreId(), 100);
        sourceDao.flush();
        assertThat(sourceDao.countDeletableSources()).isZero();

        sourceItemDao.flush();
        assertThat(sourceDao.countDeletableSources()).isZero();
        aggregator.aggregateAll();
        aggregator.closeOldAggregates(1, 1, System.currentTimeMillis());

        assertThat(aggregateDao.countAggregates()).isOne();
    }
}
