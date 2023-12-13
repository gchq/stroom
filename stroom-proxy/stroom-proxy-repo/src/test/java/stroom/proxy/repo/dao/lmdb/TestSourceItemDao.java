package stroom.proxy.repo.dao.lmdb;

import stroom.proxy.repo.FeedKey;
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
public class TestSourceItemDao {

    @Inject
    private FeedDao feedDao;
    @Inject
    private SourceDao sourceDao;
    @Inject
    private SourceItemDao sourceItemDao;
    @Inject
    private LmdbEnv lmdbEnv;

    @BeforeEach
    void beforeEach() {
        lmdbEnv.start();
    }

    @AfterEach
    void afterEach() {
        sourceDao.clear();
        sourceItemDao.clear();
        lmdbEnv.stop();
    }

    @Test
    void testSourceEntry() {
        assertThat(sourceDao.countSources()).isZero();

        sourceDao.addSource(1L, "test", "test");
        sourceDao.flush();
        lmdbEnv.sync();

        final RepoSource source = sourceDao.getNextSource();
        assertThat(source).isNotNull();
        assertThat(sourceDao.countDeletableSources()).isZero();
        assertThat(source.fileStoreId()).isEqualTo(1L);

        final long feedId = feedDao.getId(new FeedKey("testFeed", "Raw Events"));
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
        lmdbEnv.sync();

//        assertThat(sourceDao.countDeletableSources()).isZero();
//        jooq.transaction(context -> sourceItemDao.deleteBySourceId(context, source.id()));
//        assertThat(sourceDao.countDeletableSources()).isZero();
//        jooq.transaction(context -> sourceDao.setSourceExamined(context, source.id(), true, 0));
//        assertThat(sourceDao.countDeletableSources()).isOne();
//
//        sourceDao.deleteSources();
//        assertThat(sourceDao.countSources()).isZero();
//
//        sourceDao.clear();
    }
}
