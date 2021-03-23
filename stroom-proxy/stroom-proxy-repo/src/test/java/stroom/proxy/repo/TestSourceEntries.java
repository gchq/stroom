package stroom.proxy.repo;

import stroom.data.shared.StreamTypeNames;
import stroom.data.zip.StroomZipFileType;
import stroom.proxy.repo.dao.SourceDao;
import stroom.proxy.repo.dao.SourceDao.Source;
import stroom.proxy.repo.dao.SourceEntryDao;
import stroom.proxy.repo.db.jooq.tables.records.SourceEntryRecord;
import stroom.proxy.repo.db.jooq.tables.records.SourceItemRecord;

import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(GuiceExtension.class)
@IncludeModule(ProxyRepoTestModule.class)
public class TestSourceEntries {

    @Inject
    private ProxyRepoSources proxyRepoSources;
    @Inject
    private ProxyRepoSourceEntries proxyRepoSourceEntries;
    @Inject
    private SourceDao sourceDao;
    @Inject
    private SourceEntryDao sourceEntryDao;

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
        proxyRepoSources.addSource("path", "test", null, System.currentTimeMillis());

        // Check that we have a new source.
        final List<Source> sources = sourceDao.getNewSources(1000);
        assertThat(sources.size()).isOne();
        final Source source = sources.get(0);
        final long sourceId = source.getSourceId();
        final String path = source.getSourcePath();

        addEntriesToSource(sourceId, path, 1, 1);

        assertThatThrownBy(() ->
                addEntriesToSource(sourceId, path, 1, 1)).isInstanceOf(DataAccessException.class);

        proxyRepoSourceEntries.clear();

        addEntriesToSource(sourceId, path, 1, 1);
    }


    long addEntries() {
        proxyRepoSources.addSource("path", "test", null, System.currentTimeMillis());

        // Check that we have a new source.
        final List<Source> sources = sourceDao.getNewSources(1000);
        assertThat(sources.size()).isOne();
        final Source source = sources.get(0);
        final long sourceId = source.getSourceId();
        final String path = source.getSourcePath();

        addEntriesToSource(sourceId, path, 100, 10);

        assertThat(sourceDao.countSources()).isOne();
        assertThat(sourceEntryDao.countItems()).isEqualTo(1000);
        assertThat(sourceEntryDao.countEntries()).isEqualTo(3000);

        // Check that we have no new sources.
        final List<Source> sources2 = sourceDao.getNewSources(1000);
        assertThat(sources2.size()).isZero();

        return sourceId;
    }

    void addEntriesToSource(final long sourceId,
                            final String path,
                            final int loopCount,
                            final int feedCount) {
        final Map<String, SourceItemRecord> itemNameMap = new HashMap<>();
        final Map<Long, List<SourceEntryRecord>> entryMap = new HashMap<>();
        final AtomicLong sourceItemRecordId = new AtomicLong();
        final AtomicLong sourceEntryRecordId = new AtomicLong();
        final List<StroomZipFileType> types = List.of(
                StroomZipFileType.META,
                StroomZipFileType.CONTEXT,
                StroomZipFileType.DATA);

        for (int i = 0; i < loopCount; i++) {
            for (int j = 0; j < feedCount; j++) {
                final String dataName = "entry_" + i + "_" + j;
                final String feedName = "feed_" + j;
                final String typeName = StreamTypeNames.RAW_EVENTS;

                for (final StroomZipFileType type : types) {
                    long sourceItemId;
                    int extensionType = -1;
                    if (StroomZipFileType.META.equals(type)) {
                        extensionType = 1;
                    } else if (StroomZipFileType.CONTEXT.equals(type)) {
                        extensionType = 2;
                    } else if (StroomZipFileType.DATA.equals(type)) {
                        extensionType = 3;
                    }

                    SourceItemRecord sourceItemRecord = itemNameMap.get(dataName);
                    if (sourceItemRecord == null) {
                        sourceItemId = sourceItemRecordId.incrementAndGet();
                        sourceItemRecord = new SourceItemRecord(
                                sourceItemId,
                                dataName,
                                feedName,
                                typeName,
                                sourceId,
                                false);
                        itemNameMap.put(dataName, sourceItemRecord);
                    } else {
                        sourceItemId = sourceItemRecord.getId();
                    }

                    entryMap
                            .computeIfAbsent(sourceItemId, k -> new ArrayList<>())
                            .add(new SourceEntryRecord(
                                    sourceEntryRecordId.incrementAndGet(),
                                    type.getExtension(),
                                    extensionType,
                                    1000L,
                                    sourceItemId));
                }
            }
        }

        sourceEntryDao.addEntries(
                Paths.get(path),
                sourceId,
                itemNameMap,
                entryMap);
    }
}
