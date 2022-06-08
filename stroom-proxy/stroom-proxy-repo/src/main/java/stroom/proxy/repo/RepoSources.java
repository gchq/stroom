package stroom.proxy.repo;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.repo.dao.SourceDao;
import stroom.proxy.repo.queue.Batch;
import stroom.proxy.repo.store.FileSet;
import stroom.proxy.repo.store.SequentialFileStore;
import stroom.receive.common.StroomStreamException;
import stroom.util.shared.Clearable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RepoSources implements Clearable {

    private static final Logger LOGGER = LoggerFactory.getLogger(RepoSources.class);

    private final SourceDao sourceDao;
    private final ProgressLog progressLog;

    @Inject
    public RepoSources(final SourceDao sourceDao,
                       final ProgressLog progressLog) {
        this.sourceDao = sourceDao;
        this.progressLog = progressLog;
    }

    public long getMaxFileStoreId() {
        return sourceDao.getMaxFileStoreId();
    }

    /**
     * Add sources to the DB.
     */
    public void addSources(final SequentialFileStore sequentialFileStore) {
        long lastAddedStoreId = getMaxFileStoreId();
        long currentStoreId;
        while (true) {
            currentStoreId = sequentialFileStore.awaitNew(lastAddedStoreId);
            for (long i = lastAddedStoreId + 1; i <= currentStoreId; i++) {
                addSource(i, sequentialFileStore);
            }
            lastAddedStoreId = currentStoreId;
        }
    }

    private void addSource(final long storeId, final SequentialFileStore sequentialFileStore) {
        final FileSet fileSet = sequentialFileStore.getStoreFileSet(storeId);

        // Read the meta data.
        try (final InputStream inputStream = Files.newInputStream(fileSet.getMeta())) {
            final AttributeMap attributeMap = new AttributeMap();
            AttributeMapUtil.read(inputStream, attributeMap);
            final String feedName = attributeMap.get(StandardHeaderArguments.FEED);
            final String typeName = attributeMap.get(StandardHeaderArguments.TYPE);

            // If we have added a new source to the repo then add a DB record for it.
            addSource(storeId, feedName, typeName, attributeMap);

        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    public void addSource(final long fileStoreId,
                          final String feedName,
                          final String typeName,
                          final AttributeMap attributeMap) {
        if (feedName.isEmpty()) {
            throw new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED, attributeMap);
        }

        progressLog.increment("ProxyRepoSources - addSource");
        sourceDao.addSource(fileStoreId, feedName, typeName);
    }

    public Batch<RepoSource> getNewSources() {
        return sourceDao.getNewSources();
    }

    public Batch<RepoSource> getNewSources(final long timeout,
                                           final TimeUnit timeUnit) {
        return sourceDao.getNewSources(timeout, timeUnit);
    }

    public void markDeletableSources() {
        sourceDao.markDeletableSources();
    }

    public List<RepoSource> getDeletableSources(final long minSourceId,
                                                final int limit) {
        return sourceDao.getDeletableSources(minSourceId, limit);
    }

    public int deleteSources() {
        return sourceDao.deleteSources();
    }

    @Override
    public void clear() {
        sourceDao.clear();
    }

    public void flush() {
        sourceDao.flush();
    }
}
