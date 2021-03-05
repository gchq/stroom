package stroom.proxy.repo;

import stroom.util.shared.Clearable;

import org.jooq.Record1;
import org.jooq.impl.DSL;

import java.util.List;
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

        final long maxSourceRecordId = jooq.contextResult(context -> context
                .select(DSL.max(SOURCE.ID))
                .from(SOURCE)
                .fetchOptional()
                .map(Record1::value1)
                .orElse(0L));
        sourceRecordId.set(maxSourceRecordId);
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
        jooq.contextResult(context -> context
                .deleteFrom(SOURCE)
                .execute());
    }

    public void addChangeListener(final ChangeListener changeListener) {
        changeListeners.add(changeListener);
    }

    public interface ChangeListener {

        void onChange(long sourceId, String sourcePath);
    }
}
