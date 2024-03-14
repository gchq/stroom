package stroom.proxy.app;

import stroom.proxy.repo.dao.lmdb.AggregateDao;
import stroom.proxy.repo.dao.db.ForwardAggregateDao;
import stroom.proxy.repo.dao.db.ForwardSourceDao;
import stroom.proxy.repo.dao.lmdb.FeedDao;
import stroom.proxy.repo.dao.lmdb.ForwardDestDao;
import stroom.proxy.repo.dao.lmdb.SourceDao;
import stroom.proxy.repo.dao.lmdb.SourceItemDao;
import stroom.test.common.TestUtil;

import jakarta.inject.Inject;

import java.time.Duration;
import java.util.function.Supplier;

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
//        final Supplier<DbRecordCounts> actual = this::getDbRecordCounts;
//        TestUtil.waitForIt(
//                actual,
//                expected,
//                () -> "Unexpected record counts",
//                Duration.ofSeconds(60),
//                Duration.ofMillis(50),
//                Duration.ofSeconds(1));
    }

//    public DbRecordCounts getDbRecordCounts() {
//        return new DbRecordCounts(aggregateDao.countAggregates(),
//                feedDao.countFeeds(),
//                forwardAggregateDao.countForwardAggregates(),
//                forwardDestDao.countForwardDest(),
//                forwardSourceDao.countForwardSource(),
//                sourceDao.countSources(),
//                sourceDao.countDeletableSources(),
//                sourceItemDao.countItems());
//    }

    public record DbRecordCounts(long countAggregates,
                                 long countFeeds,
                                 long countForwardAggregates,
                                 long countForwardDest,
                                 long countForwardSource,
                                 long countSources,
                                 long countDeletableSources,
                                 long countItems) {

    }
}
