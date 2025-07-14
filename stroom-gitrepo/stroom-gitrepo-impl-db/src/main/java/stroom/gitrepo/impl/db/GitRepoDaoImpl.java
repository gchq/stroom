package stroom.gitrepo.impl.db;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;

public class GitRepoDaoImpl implements GitRepoDao {

    /** Bootstraps connection */
    private final GitRepoDbConnProvider gitRepoDbConnProvider;

    /** Logger */
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GitRepoDaoImpl.class);

    /**
     * Injected constructor.
     * @param gitRepoDbConnProvider Parameter to bootstrap DB connection.
     */
    @Inject
    GitRepoDaoImpl(final GitRepoDbConnProvider gitRepoDbConnProvider) {
        this.gitRepoDbConnProvider = gitRepoDbConnProvider;
    }

    @Override
    public void storeHash(final String uuid, final String hash) {
        // TODO
        /*JooqUtil.contextResult(gitRepoDbConnProvider, context -> context
            .select()
                .where()
        */
    }

    @Override
    public String getHash(final String uuid) {
        // TODO
        return "";
    }
}
