package stroom.proxy.repo.dao;

import stroom.data.zip.StroomZipFileType;
import stroom.proxy.repo.ProxyRepoTestModule;
import stroom.proxy.repo.dao.AggregateDao.Aggregate;
import stroom.proxy.repo.dao.SourceDao.Source;
import stroom.proxy.repo.dao.SourceEntryDao.SourceItem;
import stroom.proxy.repo.db.jooq.tables.records.SourceEntryRecord;
import stroom.proxy.repo.db.jooq.tables.records.SourceItemRecord;

import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(GuiceExtension.class)
@IncludeModule(ProxyRepoTestModule.class)
public class TestForwardAggregateDao {

    @Inject
    private SourceDao sourceDao;
    @Inject
    private SourceEntryDao sourceEntryDao;
    @Inject
    private AggregateDao aggregateDao;
    @Inject
    private ForwardAggregateDao forwardAggregateDao;
    @Inject
    private ForwardUrlDao forwardUrlDao;

    @BeforeEach
    void beforeEach() {
        sourceDao.clear();
        sourceEntryDao.clear();
        aggregateDao.clear();
        forwardAggregateDao.clear();
        forwardUrlDao.clear();
    }

    @Test
    void testForwardAggregate() {
        Optional<Long> id = sourceDao.getSourceId("test");
        assertThat(id.isPresent()).isFalse();

        Source source = sourceDao.addSource("test", "test", "test", System.currentTimeMillis());
        assertThat(source).isNotNull();

        id = sourceDao.getSourceId("test");
        assertThat(id.isPresent()).isTrue();

        assertThat(source.getSourceId()).isEqualTo(id.get());

        assertThat(sourceEntryDao.countEntries()).isZero();
        final Map<String, SourceItemRecord> itemNameMap = new HashMap<>();
        final Map<Long, List<SourceEntryRecord>> entryMap = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            final SourceItemRecord sourceItemRecord = new SourceItemRecord(
                    sourceEntryDao.nextSourceItemId(),
                    "item" + i,
                    "testFeed",
                    "Raw Events",
                    source.getSourceId(),
                    false);
            itemNameMap.put(sourceItemRecord.getName(), sourceItemRecord);

            for (int j = 0; j < 10; j++) {
                final long sourceEntryId = sourceEntryDao.nextSourceEntryId();
                entryMap
                        .computeIfAbsent(sourceItemRecord.getId(), k -> new ArrayList<>())
                        .add(new SourceEntryRecord(
                                sourceEntryId,
                                "dat",
                                StroomZipFileType.DATA.getId(),
                                100L,
                                sourceItemRecord.getId()));
            }
        }

        sourceEntryDao.addEntries(Paths.get("test"), source.getSourceId(), itemNameMap, entryMap);

        assertThat(sourceEntryDao.getDeletableSourceEntryIds().size()).isZero();
        assertThat(sourceEntryDao.getDeletableSourceItemIds().size()).isZero();
        assertThat(sourceEntryDao.deleteUnused()).isZero();
        assertThat(sourceDao.getDeletableSources(10).size()).isZero();

        for (final SourceItemRecord sourceItemRecord : itemNameMap.values()) {
            final SourceItem sourceItem = new SourceItem(
                    sourceItemRecord.getId(),
                    sourceItemRecord.getFeedName(),
                    sourceItemRecord.getTypeName(),
                    1000L);
            aggregateDao.addItem(sourceItem, 10, 10000L);
        }
        aggregateDao.closeAggregates(10, 10000L, System.currentTimeMillis());
        assertThat(sourceEntryDao.getDeletableSourceEntryIds().size()).isZero();
        assertThat(sourceEntryDao.getDeletableSourceItemIds().size()).isZero();
        assertThat(sourceEntryDao.deleteUnused()).isZero();
        assertThat(sourceDao.getDeletableSources(10).size()).isZero();

        final List<Aggregate> aggregates = aggregateDao.getCompletedAggregates(1000);
        assertThat(aggregates.size()).isEqualTo(10);
        final int forwardUrlId = forwardUrlDao.getForwardUrlId("test");
        for (final Aggregate aggregate : aggregates) {
            forwardAggregateDao.createForwardAggregateRecord(forwardUrlId, aggregate.getAggregateId(), true, null);
        }

        assertThat(sourceEntryDao.getDeletableSourceEntryIds().size()).isZero();
        assertThat(sourceEntryDao.getDeletableSourceItemIds().size()).isZero();
        assertThat(sourceEntryDao.deleteUnused()).isZero();
        assertThat(sourceDao.getDeletableSources(10).size()).isZero();

        for (final Aggregate aggregate : aggregates) {
            forwardAggregateDao.setForwardSuccess(aggregate.getAggregateId());
        }

        assertThat(sourceEntryDao.getDeletableSourceEntryIds().size()).isEqualTo(1000);
        assertThat(sourceEntryDao.getDeletableSourceItemIds().size()).isEqualTo(100);
        assertThat(sourceEntryDao.deleteUnused()).isEqualTo(1100);
        assertThat(sourceDao.getDeletableSources(10).size()).isEqualTo(1);
    }
}
