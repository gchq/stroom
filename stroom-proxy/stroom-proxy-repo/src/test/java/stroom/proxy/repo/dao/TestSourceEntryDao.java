package stroom.proxy.repo.dao;

import stroom.proxy.repo.FeedKey;
import stroom.proxy.repo.ProxyRepoTestModule;
import stroom.proxy.repo.RepoSource;
import stroom.proxy.repo.RepoSourceItem;
import stroom.proxy.repo.queue.Batch;

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
public class TestSourceEntryDao {

    @Inject
    private SqliteJooqHelper jooq;
    @Inject
    private FeedDao feedDao;
    @Inject
    private SourceDao sourceDao;
    @Inject
    private SourceItemDao sourceItemDao;

    @BeforeEach
    void beforeEach() {
        sourceDao.clear();
        sourceItemDao.clear();
    }

    @Test
    void testSourceEntry() {
        assertThat(sourceDao.countSources()).isZero();

        sourceDao.addSource(1L, "test", "test");
        sourceDao.flush();

        final Batch<RepoSource> sources = sourceDao.getNewSources(0, TimeUnit.MILLISECONDS);
        assertThat(sources.list().isEmpty()).isFalse();
        assertThat(sourceDao.countDeletableSources()).isZero();

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
        jooq.transaction(context -> sourceItemDao.deleteBySourceId(context, source.id()));
        assertThat(sourceDao.countDeletableSources()).isZero();
        jooq.transaction(context -> sourceDao.setSourceExamined(context, source.id(), true, 0));
        assertThat(sourceDao.countDeletableSources()).isOne();

        sourceDao.deleteSources();
        assertThat(sourceDao.countSources()).isZero();

        sourceDao.clear();
    }
}
