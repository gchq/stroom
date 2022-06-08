package stroom.proxy.repo.dao;

import stroom.proxy.repo.FeedKey;
import stroom.proxy.repo.ProxyRepoTestModule;
import stroom.proxy.repo.RepoSource;
import stroom.proxy.repo.RepoSourceItem;
import stroom.proxy.repo.queue.Batch;
import stroom.proxy.repo.queue.BatchUtil;

import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(GuiceExtension.class)
@IncludeModule(ProxyRepoTestModule.class)
public class TestForwardAggregateDao {

    @Inject
    private FeedDao feedDao;
    @Inject
    private SourceDao sourceDao;
    @Inject
    private SourceItemDao sourceItemDao;
    @Inject
    private AggregateDao aggregateDao;
    @Inject
    private ForwardAggregateDao forwardAggregateDao;
    @Inject
    private ForwardDestDao forwardDestDao;

    @BeforeEach
    void beforeEach() {
        sourceDao.clear();
        sourceItemDao.clear();
        aggregateDao.clear();
        forwardAggregateDao.clear();
        forwardDestDao.clear();
    }

    @Test
    void testForwardAggregate() {
        assertThat(sourceDao.countSources()).isZero();
        assertThat(aggregateDao.countAggregates()).isZero();
        assertThat(forwardAggregateDao.countForwardAggregates()).isZero();
        assertThat(forwardDestDao.countForwardDest()).isZero();
//        assertThat(sourceDao.pathExists("test")).isFalse();

        sourceDao.addSource(1L, "test", "test");
        sourceDao.flush();

        final Batch<RepoSource> sources = sourceDao.getNewSources(0, TimeUnit.MILLISECONDS);
        assertThat(sources.isEmpty()).isFalse();

        final RepoSource source = sources.list().get(0);
        assertThat(source.fileStoreId()).isEqualTo(1L);

        final long feedId = feedDao.getId(new FeedKey("testFeed", "Raw Events"));
        final Map<String, RepoSourceItem> itemNameMap = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            final RepoSourceItem sourceItemRecord = new RepoSourceItem(
                    source,
                    i,
                    "item" + i,
                    feedId,
                    null,
                    0,
                    "dat");
            itemNameMap.put(sourceItemRecord.name(), sourceItemRecord);
        }

        sourceItemDao.addItems(source, itemNameMap.values());
        sourceItemDao.flush();
        assertThat(sourceDao.countDeletableSources()).isZero();

        BatchUtil.transfer(
                sourceItemDao::getNewSourceItems,
                batch -> aggregateDao.addItems(batch, 10, 10000L));

        assertThat(aggregateDao.countAggregates()).isEqualTo(10);
        final long count = aggregateDao.closeAggregates(0,
                10000L,
                System.currentTimeMillis(),
                1000);
        assertThat(count).isEqualTo(10);
        assertThat(aggregateDao.countAggregates()).isEqualTo(10);

        // Create forward aggregates.
        forwardDestDao.getForwardDestId("test");
        assertThat(forwardDestDao.countForwardDest()).isOne();
        BatchUtil.transfer(
                () -> aggregateDao.getNewAggregates(0, TimeUnit.MILLISECONDS),
                batch -> forwardAggregateDao.createForwardAggregates(batch,
                        forwardDestDao.getAllForwardDests())
        );
        forwardAggregateDao.flush();
        assertThat(forwardAggregateDao.countForwardAggregates()).isEqualTo(10);

        // Mark all as forwarded.
        BatchUtil.transferEach(
                () -> forwardAggregateDao.getNewForwardAggregates(0, TimeUnit.MILLISECONDS),
                forwardAggregate -> forwardAggregateDao.update(forwardAggregate.copy().tries(1).success(true).build())
        );

        sourceDao.countDeletableSources();
        sourceDao.deleteSources();

        assertThat(forwardAggregateDao.countForwardAggregates()).isZero();
        assertThat(aggregateDao.countAggregates()).isZero();
        assertThat(sourceItemDao.countItems()).isZero();
        assertThat(sourceDao.countSources()).isZero();
    }
}

