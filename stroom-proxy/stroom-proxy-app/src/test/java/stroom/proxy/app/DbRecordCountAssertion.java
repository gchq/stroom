package stroom.proxy.app;

import stroom.proxy.repo.dao.AggregateDao;
import stroom.proxy.repo.dao.FeedDao;
import stroom.proxy.repo.dao.ForwardAggregateDao;
import stroom.proxy.repo.dao.ForwardDestDao;
import stroom.proxy.repo.dao.ForwardSourceDao;
import stroom.proxy.repo.dao.SourceDao;
import stroom.proxy.repo.dao.SourceItemDao;
import stroom.test.common.TestUtil;

import java.time.Duration;
import java.util.function.Supplier;
import javax.inject.Inject;

public class DbRecordCountAssertion {

    @Inject
    private AggregateDao aggregateDao;
    @Inject
    private FeedDao feedDao;
    @Inject
    private ForwardAggregateDao forwardAggregateDao;
    @Inject
    private ForwardDestDao forwardDestDao;
    @Inject
    private ForwardSourceDao forwardSourceDao;
    @Inject
    private SourceDao sourceDao;
    @Inject
    private SourceItemDao sourceItemDao;

    public void assertRecordCounts(final DbRecordCounts expected) {
        final Supplier<DbRecordCounts> actual = this::getDbRecordCounts;
        TestUtil.waitForIt(
                actual,
                expected,
                () -> "Unexpected record counts",
                Duration.ofSeconds(60),
                Duration.ofMillis(50),
                Duration.ofSeconds(1));
    }

    public DbRecordCounts getDbRecordCounts() {
        return new DbRecordCounts(aggregateDao.countAggregates(),
                feedDao.countFeeds(),
                forwardAggregateDao.countForwardAggregates(),
                forwardDestDao.countForwardDest(),
                forwardSourceDao.countForwardSource(),
                sourceDao.countSources(),
                sourceDao.countDeletableSources(),
                sourceItemDao.countItems());
    }

    public record DbRecordCounts(int countAggregates,
                                 int countFeeds,
                                 int countForwardAggregates,
                                 int countForwardDest,
                                 int countForwardSource,
                                 int countSources,
                                 int countDeletableSources,
                                 int countItems) {

    }
}
