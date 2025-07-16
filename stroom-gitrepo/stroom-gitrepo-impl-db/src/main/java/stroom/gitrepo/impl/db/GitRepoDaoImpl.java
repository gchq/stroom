package stroom.gitrepo.impl.db;

import stroom.db.util.JooqUtil;
import stroom.gitrepo.impl.GitRepoDao;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import org.jooq.Record1;
import org.jooq.Result;

import static stroom.gitrepo.impl.db.jooq.tables.GitRepo.GIT_REPO;

/**
 * DAO implementation for GitRepoDoc state.
 * Stores state that is set on the server rather than in the UI,
 * thus avoids version issues between the server and UI.
 */
public class GitRepoDaoImpl implements GitRepoDao {

    /** Bootstraps connection */
    private final GitRepoDbConnProvider gitRepoDbConnProvider;

    /** Logger */
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GitRepoDaoImpl.class);

    /**
     * Injected constructor.
     * @param gitRepoDbConnProvider Parameter to bootstrap DB connection.
     */
    @SuppressWarnings("unused")
    @Inject
    GitRepoDaoImpl(final GitRepoDbConnProvider gitRepoDbConnProvider) {
        this.gitRepoDbConnProvider = gitRepoDbConnProvider;
    }

    /**
     * Stores the Git commit hash for a given GitRepoDoc UUID.
     * @param uuid The UUID of the doc that this matches.
     * @param hash The Git commit hash that we've obtained from Git.
     */
    @Override
    public void storeHash(final String uuid, final String hash) {
        LOGGER.info("Storing GitRepo UUID '{}' -> Git Commit Hash '{}'", uuid, hash);
        JooqUtil.context(gitRepoDbConnProvider, context -> context
                .insertInto(GIT_REPO)
                .columns(GIT_REPO.GIT_REPO_UUID, GIT_REPO.GIT_COMMIT_HASH)
                .values(uuid, hash)
                .onDuplicateKeyUpdate()
                .set(GIT_REPO.GIT_REPO_UUID, uuid)
                .set(GIT_REPO.GIT_COMMIT_HASH, hash)
                .execute()
        );
    }

    /**
     * Returns the Git commit hash for a given GitRepoDoc UUID.
     * @param uuid The UUID of the doc that we want the hash for.
     * @return The Git commit hash, or null.
     */
    @Override
    public String getHash(final String uuid) {
        final Result<Record1<String>> result = JooqUtil.contextResult(gitRepoDbConnProvider, context -> context
                .select(GIT_REPO.GIT_COMMIT_HASH)
                .from(GIT_REPO)
                .where(GIT_REPO.GIT_REPO_UUID.eq(uuid))
                .fetch());

        LOGGER.info("Retrieving Git Repo UUID '{}' -> Git Commit Hash '{}'", uuid, result.toString());
        return result.toString();
    }

}
