package stroom.proxy.repo;

import stroom.meta.api.AttributeMap;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.repo.dao.SourceDao;
import stroom.receive.common.StroomStreamException;
import stroom.util.shared.Clearable;

import java.util.List;
import java.util.Optional;
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

    public boolean sourceExists(final String path) {
        return sourceDao.pathExists(path);
    }

    public void addSource(final String path,
                          final String feedName,
                          final String typeName,
                          final long lastModifiedTimeMs,
                          final AttributeMap attributeMap) {
        if (feedName.isEmpty()) {
            throw new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED, attributeMap);
        }

        progressLog.increment("ProxyRepoSources - addSource");
        sourceDao.addSource(path, feedName, typeName, lastModifiedTimeMs);
    }

    public void deleteSource(final RepoSource source) {
        sourceDao.deleteSource(source.getId());
    }

    public Optional<RepoSource> getNewSource() {
        return sourceDao.getNewSource();
    }

    public Optional<RepoSource> getNewSource(final long timeout,
                                             final TimeUnit timeUnit) {
        return sourceDao.getNewSource(timeout, timeUnit);
    }

    public List<RepoSource> getDeletableSources() {
        return sourceDao.getDeletableSources();
    }

    @Override
    public void clear() {
        sourceDao.clear();
    }
}
