package stroom.proxy.repo;

import stroom.data.shared.StreamTypeNames;
import stroom.data.zip.StroomZipFileType;
import stroom.proxy.repo.dao.FeedDao;
import stroom.proxy.repo.dao.SourceDao;
import stroom.proxy.repo.dao.SourceItemDao;
import stroom.proxy.repo.queue.Batch;

import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(GuiceExtension.class)
@IncludeModule(ProxyRepoTestModule.class)
public class TestSourceEntries {

    @Inject
    private FeedDao feedDao;
    @Inject
    private RepoSources proxyRepoSources;
    @Inject
    private RepoSourceItems proxyRepoSourceEntries;
    @Inject
    private SourceDao sourceDao;
    @Inject
    private SourceItemDao sourceItemDao;

    @BeforeEach
    void beforeEach() {
        proxyRepoSources.clear();
    }

    @BeforeEach
    void cleanup() {
        proxyRepoSourceEntries.clear();
        proxyRepoSources.clear();
    }

    @Test
    void testAddEntries() {
        addEntries();
    }

    @Test
    void testUnique() {
        proxyRepoSources.addSource(1L, "test", null, null);
        proxyRepoSources.flush();

        // Check that we have a new source.
        final Batch<RepoSource> batch = sourceDao.getNewSources();
        assertThat(batch.isEmpty()).isFalse();
        final RepoSource source = batch.list().get(0);

        addEntriesToSource(source, 1, 1);

        assertThatThrownBy(() ->
                addEntriesToSource(source, 1, 1)).isInstanceOf(DataAccessException.class);

        proxyRepoSourceEntries.clear();

        addEntriesToSource(source, 1, 1);
    }


    long addEntries() {
        proxyRepoSources.addSource(1L, "test", null, null);
        proxyRepoSources.flush();

        // Check that we have a new source.
        final Batch<RepoSource> batch = sourceDao.getNewSources(0, TimeUnit.MILLISECONDS);
        assertThat(batch.isEmpty()).isFalse();
        final RepoSource source = batch.list().get(0);
        final long sourceId = source.id();

        addEntriesToSource(source, 100, 10);

        assertThat(sourceDao.countSources()).isOne();
        assertThat(sourceItemDao.countItems()).isEqualTo(1000);

        // Check that we have no new sources.
        final Batch<RepoSource> sources2 = sourceDao.getNewSources(0, TimeUnit.MILLISECONDS);
        assertThat(sources2.list().isEmpty()).isTrue();

        return sourceId;
    }

    void addEntriesToSource(final RepoSource source,
                            final int loopCount,
                            final int feedCount) {
        final AtomicLong id = new AtomicLong();
        final Map<String, RepoSourceItem> itemNameMap = new HashMap<>();
        final List<StroomZipFileType> types = List.of(
                StroomZipFileType.META,
                StroomZipFileType.CONTEXT,
                StroomZipFileType.DATA);

        for (int i = 0; i < loopCount; i++) {
            for (int j = 0; j < feedCount; j++) {
                final String dataName = "entry_" + i + "_" + j;
                final String feedName = "feed_" + j;
                final String typeName = StreamTypeNames.RAW_EVENTS;
                final long feedId = feedDao.getId(new FeedKey(feedName, typeName));

                for (final StroomZipFileType type : types) {
                    final RepoSourceItem item = itemNameMap.computeIfAbsent(dataName, k ->
                            new RepoSourceItem(source,
                                    id.incrementAndGet(),
                                    dataName,
                                    feedId,
                                    null,
                                    0,
                                    "dat"));
                }
            }
        }

        sourceItemDao.addItems(source, itemNameMap.values());
        sourceItemDao.flush();
    }
}
