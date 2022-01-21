package stroom.proxy.repo;

import stroom.data.shared.StreamTypeNames;
import stroom.data.zip.StroomZipFileType;
import stroom.proxy.repo.dao.SourceDao;
import stroom.proxy.repo.dao.SourceItemDao;

import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(GuiceExtension.class)
@IncludeModule(ProxyRepoTestModule.class)
public class TestSourceEntries {

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
        proxyRepoSources.addSource("path", "test", null, System.currentTimeMillis(), null);

        // Check that we have a new source.
        final Optional<RepoSource> optionalSource = sourceDao.getNewSource();
        assertThat(optionalSource.isPresent()).isTrue();
        final RepoSource source = optionalSource.get();

        addEntriesToSource(source, 1, 1);

        assertThatThrownBy(() ->
                addEntriesToSource(source, 1, 1)).isInstanceOf(DataAccessException.class);

        proxyRepoSourceEntries.clear();

        addEntriesToSource(source, 1, 1);
    }


    long addEntries() {
        proxyRepoSources.addSource("path", "test", null, System.currentTimeMillis(), null);

        // Check that we have a new source.
        final Optional<RepoSource> optionalSource = sourceDao.getNewSource();
        assertThat(optionalSource.isPresent()).isTrue();
        final RepoSource source = optionalSource.get();
        final long sourceId = source.getId();

        addEntriesToSource(source, 100, 10);

        assertThat(sourceDao.countSources()).isOne();
        assertThat(sourceItemDao.countItems()).isEqualTo(1000);
        assertThat(sourceItemDao.countEntries()).isEqualTo(3000);

        // Check that we have no new sources.
        final Optional<RepoSource> optionalSource2 = sourceDao.getNewSource(0, TimeUnit.MILLISECONDS);
        assertThat(optionalSource2.isPresent()).isFalse();

        return sourceId;
    }

    void addEntriesToSource(final RepoSource source,
                            final int loopCount,
                            final int feedCount) {
        final Map<String, RepoSourceItem.Builder> itemNameMap = new HashMap<>();
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
                    final RepoSourceItem.Builder builder = itemNameMap.computeIfAbsent(dataName, k ->
                            RepoSourceItem.builder()
                                    .source(source)
                                    .name(dataName)
                                    .feedName(feedName)
                                    .typeName(typeName));

                    builder.addEntry(RepoSourceEntry.builder()
                            .type(type)
                            .extension(type.getExtension())
                            .byteSize(1000L)
                            .build());
                }
            }
        }

        sourceItemDao.addItems(
                Paths.get(source.getSourcePath()),
                source.getId(),
                itemNameMap
                        .values()
                        .stream()
                        .map(RepoSourceItem.Builder::build)
                        .collect(Collectors.toList()));
    }
}
