package stroom.proxy.repo;

import stroom.db.util.JooqUtil;

import org.jooq.DSLContext;

import java.util.Optional;
import javax.inject.Inject;

import static stroom.proxy.repo.db.jooq.tables.Source.SOURCE;

public class ProxyRepoSources {

    private final ProxyRepoDbConnProvider connProvider;

    @Inject
    public ProxyRepoSources(final ProxyRepoDbConnProvider connProvider) {
        this.connProvider = connProvider;
    }

    public Optional<Integer> getSource(final DSLContext context, final String path) {
        return context
                .select(SOURCE.ID)
                .from(SOURCE)
                .where(SOURCE.PATH.eq(path))
                .fetchOptional()
                .map(r -> r.get(SOURCE.ID));
    }

    public int addSource(final String path, final long lastModifiedTimeMs) {
        return JooqUtil.contextResult(connProvider, context -> context
                .insertInto(SOURCE, SOURCE.PATH, SOURCE.LAST_MODIFIED_TIME_MS)
                .values(path, lastModifiedTimeMs)
                .onDuplicateKeyIgnore()
                .returning(SOURCE.ID)
                .fetchOne()
                .getId());
    }
}
