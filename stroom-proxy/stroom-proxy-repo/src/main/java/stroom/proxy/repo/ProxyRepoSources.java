package stroom.proxy.repo;

import stroom.util.shared.Clearable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.proxy.repo.db.jooq.tables.Source.SOURCE;

@Singleton
public class ProxyRepoSources implements Clearable {

    private final SqliteJooqHelper jooq;
    private final List<ChangeListener> changeListeners = new CopyOnWriteArrayList<>();

    private final AtomicLong sourceRecordId = new AtomicLong();

    @Inject
    public ProxyRepoSources(final ProxyRepoDbConnProvider connProvider) {
        this.jooq = new SqliteJooqHelper(connProvider);

        init();
    }

    private void init() {
        final long maxSourceRecordId = jooq.getMaxId(SOURCE, SOURCE.ID).orElse(0L);
        sourceRecordId.set(maxSourceRecordId);
    }

    public Optional<Long> getSourceId(final String path) {
        return jooq.contextResult(context -> context
                .select(SOURCE.ID)
                .from(SOURCE)
                .where(SOURCE.PATH.eq(path))
                .fetchOptional(SOURCE.ID));
    }

    public long addSource(final String path, final long lastModifiedTimeMs) {
        final long sourceId = sourceRecordId.incrementAndGet();
        jooq.context(context -> context
                .insertInto(SOURCE, SOURCE.ID, SOURCE.PATH, SOURCE.LAST_MODIFIED_TIME_MS)
                .values(sourceId, path, lastModifiedTimeMs)
                .execute());

        changeListeners.forEach(listener -> listener.onChange(sourceId, path));

        return sourceId;
    }

    @Override
    public void clear() {
        jooq.deleteAll(SOURCE);

        jooq
                .getMaxId(SOURCE, SOURCE.ID)
                .ifPresent(id -> {
                    throw new RuntimeException("Unexpected ID");
                });

        init();
    }

    public void addChangeListener(final ChangeListener changeListener) {
        changeListeners.add(changeListener);
    }

    public interface ChangeListener {

        void onChange(long sourceId, String sourcePath);
    }
}
