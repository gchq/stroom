package stroom.gitrepo.impl.db;

import stroom.gitrepo.impl.GitRepoDao;

import com.google.inject.AbstractModule;

/**
 * Guice injection module
 */
public class GitRepoDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(GitRepoDao.class).to(GitRepoDaoImpl.class);
    }
}
