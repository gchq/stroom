package stroom.proxy.repo;

import stroom.meta.api.AttributeMap;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.repo.dao.SourceDao;
import stroom.proxy.repo.queue.Batch;
import stroom.receive.common.StroomStreamException;
import stroom.util.shared.Clearable;

import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RepoSources implements Clearable {

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

//    public boolean sourceExists(final String path) {
//        return sourceDao.pathExists(path);
//    }

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

    public int deleteSources(final List<RepoSource> sources) {
        return sourceDao.deleteSources(sources);
    }

    public Batch<RepoSource> getNewSources() {
        return sourceDao.getNewSources();
    }

    public Batch<RepoSource> getNewSources(final long timeout,
                                           final TimeUnit timeUnit) {
        return sourceDao.getNewSources(timeout, timeUnit);
    }

    public List<RepoSource> getDeletableSources(final int limit) {
        return sourceDao.getDeletableSources(limit);
    }

    @Override
    public void clear() {
        sourceDao.clear();
    }

    public void flush() {
        sourceDao.flush();
    }
}
