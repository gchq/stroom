package stroom.proxy.repo;

import stroom.proxy.repo.dao.AggregateDao;
import stroom.proxy.repo.dao.ForwardDestDao;
import stroom.proxy.repo.dao.SourceDao;
import stroom.proxy.repo.dao.SourceItemDao;
import stroom.proxy.repo.queue.Batch;

import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(GuiceExtension.class)
@IncludeModule(ProxyRepoTestModule.class)
public class TestAggregator {

    @Inject
    private SourceDao sourceDao;
    @Inject
    private SourceItemDao sourceItemDao;
    @Inject
    private RepoSources proxyRepoSources;
    @Inject
    private RepoSourceItems proxyRepoSourceEntries;
    @Inject
    private AggregateDao aggregateDao;
    @Inject
    private Aggregator aggregator;
    @Inject
    private AggregateForwarder aggregateForwarder;
    @Inject
    private ForwardDestDao forwardDestDao;
    @Inject
    private MockForwardDestinations mockForwardDestinations;
    @Inject
    private TestSourceEntries testSourceEntries;

    @BeforeEach
    void beforeEach() {
        aggregateForwarder.clear();
        aggregator.clear();
        proxyRepoSourceEntries.clear();
        proxyRepoSources.clear();
        mockForwardDestinations.clear();
    }

    @Test
    void testCloseOldAggregates() {
        aggregator.closeOldAggregates();
    }

    @Test
    void testWithSourceEntries() {
        assertThat(sourceItemDao.countItems()).isZero();
        assertThat(aggregateDao.countAggregates()).isZero();
        ensureNonDeletable();

        // Add entries.
        testSourceEntries.addEntries();
        ensureNonDeletable();

        // Check that there is now something to aggregate.
        assertThat(sourceItemDao.countItems()).isEqualTo(1000);

        // Make sure we have no existing aggregates.
        aggregator.closeOldAggregates(1, 1, System.currentTimeMillis());

        // Aggregate.
        aggregator.aggregateAll();

        // Check that we now have nothing left to aggregate.
        assertThat(sourceItemDao.getNewSourceItems(0, TimeUnit.MILLISECONDS).isEmpty()).isTrue();
        ensureNonDeletable();

        // Check that the new aggregates are not yet completed.
        assertThat(aggregateDao.getNewAggregates(0, TimeUnit.MILLISECONDS).isEmpty()).isTrue();
        // Close all aggregates.
        aggregator.closeOldAggregates(1, 1, System.currentTimeMillis());
        // Check that we now have some aggregates.
        assertThat(aggregateDao.countAggregates()).isEqualTo(10);
        ensureNonDeletable();

        // Now forward the completed aggregates.
        final String forwardUrl = "http://test-url.com";
        forwardDestDao.getForwardDestId(forwardUrl);
        aggregateForwarder.createAllForwardAggregates();
        aggregateForwarder.flush();

        // Check we still can't delete sources.
        ensureNonDeletable();

        aggregateForwarder.forwardAll();

        // We should now have no source entries but some sources we can delete.
        assertThat(sourceItemDao.countItems()).isZero();
        assertThat(aggregateDao.countAggregates()).isZero();
        assertThat(sourceDao.countDeletableSources()).isOne();
    }

    private void ensureNonDeletable() {
        assertThat(sourceDao.countDeletableSources()).isZero();
    }

    @Test
    void testAddItem() {
        // Make sure we have no existing aggregates.
        assertThat(aggregateDao.countAggregates()).isZero();

        final List<RepoSourceItemRef> list = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            // Add an item but make sure no aggregation takes place.
            list.add(new RepoSourceItemRef(1, 1, 10));
        }

        aggregator.addItems(new Batch<>(list, true));

        // Now force aggregation and ensure we end up with 1 aggregate.
        aggregator.closeOldAggregates(1, 1, System.currentTimeMillis());
        assertThat(aggregateDao.countAggregates()).isOne();
    }
}
