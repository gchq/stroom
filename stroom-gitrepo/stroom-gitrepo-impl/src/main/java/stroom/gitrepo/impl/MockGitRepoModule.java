package stroom.gitrepo.impl;

import stroom.gitrepo.api.GitRepoStorageService;
import stroom.gitrepo.api.GitRepoStore;

import com.google.inject.AbstractModule;

public class MockGitRepoModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(GitRepoStore.class).to(GitRepoStoreImpl.class);
        bind(GitRepoStorageService.class).to(GitRepoStorageServiceImpl.class);
    }
}
