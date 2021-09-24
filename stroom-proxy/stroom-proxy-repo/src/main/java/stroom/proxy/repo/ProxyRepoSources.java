package stroom.proxy.repo;

import stroom.meta.api.AttributeMap;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.repo.dao.SourceDao;
import stroom.proxy.repo.dao.SourceDao.Source;
import stroom.receive.common.StroomStreamException;
import stroom.util.shared.Clearable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ProxyRepoSources implements Clearable {

    private final List<ChangeListener> changeListeners = new CopyOnWriteArrayList<>();

    private final SourceDao sourceDao;

    @Inject
    public ProxyRepoSources(final SourceDao sourceDao) {
        this.sourceDao = sourceDao;
    }


    public Optional<Long> getSourceId(final String path) {
        return sourceDao.getSourceId(path);
    }

    public Source addSource(final String path,
                            final String feedName,
                            final String typeName,
                            final long lastModifiedTimeMs,
                            final AttributeMap attributeMap) {
        if (feedName.isEmpty()) {
            throw new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED, attributeMap);
        }

        final Source source = sourceDao.addSource(
                path,
                feedName,
                typeName,
                lastModifiedTimeMs);

        changeListeners.forEach(listener -> listener.onChange(source));

        return source;
    }

    @Override
    public void clear() {
        sourceDao.clear();
    }

    public void addChangeListener(final ChangeListener changeListener) {
        changeListeners.add(changeListener);
    }

    public interface ChangeListener {

        void onChange(Source source);
    }
}
