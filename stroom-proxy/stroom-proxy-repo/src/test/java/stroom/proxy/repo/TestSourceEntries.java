package stroom.proxy.repo;

import stroom.data.shared.StreamTypeNames;
import stroom.data.zip.StroomZipFileType;
import stroom.db.util.JooqHelper;
import stroom.proxy.repo.db.jooq.tables.records.SourceEntryRecord;
import stroom.proxy.repo.db.jooq.tables.records.SourceItemRecord;

import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.jooq.Record2;
import org.jooq.Result;
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
import static stroom.proxy.repo.db.jooq.tables.Source.SOURCE;

@ExtendWith(GuiceExtension.class)
@IncludeModule(ProxyRepoTestModule.class)
public class TestSourceEntries {

    @Inject
    private ProxyRepoSources proxyRepoSources;
    @Inject
    private ProxyRepoSourceEntries proxyRepoSourceEntries;
    @Inject
    private ProxyRepoDbConnProvider connProvider;

    @BeforeEach
    void beforeEach() {
        proxyRepoSources.clear();
    }

    @BeforeEach
    void cleanup() {
        new JooqHelper(connProvider).deleteAll(SOURCE);
    }

    @Test
    void testAddEntries() {
        addEntries();
    }

    long addEntries() {
        proxyRepoSources.addSource("path", System.currentTimeMillis());

        // Check that we have a new source.
        final Result<Record2<Long, String>> result = proxyRepoSourceEntries.getNewSources();
        assertThat(result.size()).isOne();
        final long sourceId = result.get(0).value1();
        final String path = result.get(0).value2();

        final Map<String, SourceItemRecord> itemNameMap = new HashMap<>();
        final Map<Long, List<SourceEntryRecord>> entryMap = new HashMap<>();
        final AtomicLong sourceItemRecordId = new AtomicLong();
        final AtomicLong sourceEntryRecordId = new AtomicLong();
        final List<StroomZipFileType> types = List.of(
                StroomZipFileType.META,
                StroomZipFileType.CONTEXT,
                StroomZipFileType.DATA);

        for (int i = 0; i < 100; i++) {
            for (int j = 0; j < 10; j++) {
                final String dataName = "entry_" + i + "_" + j;
                final String feedName = "feed_" + j;
                final String typeName = StreamTypeNames.RAW_EVENTS;

                for (final StroomZipFileType type : types) {
                    long sourceItemId = -1;
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

        proxyRepoSourceEntries.addEntries(
                Paths.get(path),
                sourceId,
                itemNameMap,
                entryMap);

        assertThat(proxyRepoSourceEntries.countSources()).isOne();
        assertThat(proxyRepoSourceEntries.countItems()).isEqualTo(1000);
        assertThat(proxyRepoSourceEntries.countEntries()).isEqualTo(3000);

        // Check that we have no new sources.
        final Result<Record2<Long, String>> result2 = proxyRepoSourceEntries.getNewSources();
        assertThat(result2.size()).isZero();

        return sourceId;
    }
}
