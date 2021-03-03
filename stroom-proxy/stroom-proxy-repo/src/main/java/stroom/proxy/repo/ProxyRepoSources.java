package stroom.proxy.repo;

import stroom.proxy.repo.db.jooq.tables.records.SourceRecord;
import stroom.util.shared.Clearable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.proxy.repo.db.jooq.tables.Source.SOURCE;

@Singleton
public class ProxyRepoSources implements Clearable {

    private final ProxyRepoDbConnProvider connProvider;
    private final List<ChangeListener> changeListeners = new CopyOnWriteArrayList<>();

    @Inject
    public ProxyRepoSources(final ProxyRepoDbConnProvider connProvider) {
        this.connProvider = connProvider;
    }

    public Optional<Integer> getSource(final String path) {
        return SqliteJooqUtil.contextResult(connProvider, context -> context
                .select(SOURCE.ID)
                .from(SOURCE)
                .where(SOURCE.PATH.eq(path))
                .fetchOptional()
                .map(r -> r.get(SOURCE.ID)));
    }

    public int addSource(final String path, final long lastModifiedTimeMs) {
        final int sourceId = SqliteJooqUtil.contextResult(connProvider, context -> context
                .insertInto(SOURCE, SOURCE.PATH, SOURCE.LAST_MODIFIED_TIME_MS)
                .values(path, lastModifiedTimeMs)
                .onDuplicateKeyIgnore()
                .returning(SOURCE.ID)
                .fetchOptional()
                .map(SourceRecord::getId)
                .orElse(-1));

        changeListeners.forEach(listener -> listener.onChange(sourceId, path));

        return sourceId;
    }

    @Override
    public void clear() {
        SqliteJooqUtil.contextResult(connProvider, context -> context
                .deleteFrom(SOURCE)
                .execute());
    }

    public void addChangeListener(final ChangeListener changeListener) {
        changeListeners.add(changeListener);
    }

    public interface ChangeListener {

        void onChange(int sourceId, String sourcePath);
    }
}
