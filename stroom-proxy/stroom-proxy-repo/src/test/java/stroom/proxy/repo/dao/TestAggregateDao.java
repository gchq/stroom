package stroom.proxy.repo.dao;

import stroom.data.zip.StroomZipFileType;
import stroom.proxy.repo.Aggregator;
import stroom.proxy.repo.ProxyRepoTestModule;
import stroom.proxy.repo.RepoSource;
import stroom.proxy.repo.RepoSourceEntry;
import stroom.proxy.repo.RepoSourceItem;

import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(GuiceExtension.class)
@IncludeModule(ProxyRepoTestModule.class)
public class TestAggregateDao {

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
        sourceDao.clear();
        sourceItemDao.clear();
        aggregateDao.clear();
    }

    @Test
    void testAggregate() {
        assertThat(sourceDao.countSources()).isZero();
        assertThat(sourceItemDao.countEntries()).isZero();
        assertThat(aggregateDao.countAggregates()).isZero();
        assertThat(sourceDao.pathExists("test")).isFalse();

        sourceDao.addSource("test", "test", "test", System.currentTimeMillis());

        final Optional<RepoSource> optionalSource = sourceDao.getNewSource(0, TimeUnit.MILLISECONDS);
        assertThat(optionalSource.isPresent()).isTrue();

        final RepoSource source = optionalSource.get();
        assertThat(source.getSourcePath()).isEqualTo("test");

        final Map<String, RepoSourceItem> itemNameMap = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            final RepoSourceItem sourceItemRecord = RepoSourceItem
                    .builder()
                    .source(source)
                    .name("item" + i)
                    .feedName("testFeed")
                    .typeName("Raw Events")
                    .build();
            itemNameMap.put(sourceItemRecord.getName(), sourceItemRecord);

            for (int j = 0; j < 10; j++) {
                final RepoSourceEntry entry = RepoSourceEntry
                        .builder()
                        .type(StroomZipFileType.DATA)
                        .extension("dat")
                        .byteSize(100L)
                        .build();
                sourceItemRecord.addEntry(entry);
            }
        }

        sourceItemDao.addItems(Paths.get("test"), source.getId(), itemNameMap.values());
        assertThat(sourceDao.getDeletableSources().size()).isZero();
        aggregator.aggregateAll();
        aggregator.closeOldAggregates(System.currentTimeMillis());

        assertThat(aggregateDao.countAggregates()).isOne();
    }
}
