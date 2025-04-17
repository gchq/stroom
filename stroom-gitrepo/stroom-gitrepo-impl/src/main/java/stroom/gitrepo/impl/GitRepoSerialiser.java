package stroom.gitrepo.impl;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.gitrepo.shared.GitRepoDoc;

import jakarta.inject.Inject;

import java.io.IOException;
import java.util.Map;

public class GitRepoSerialiser implements DocumentSerialiser2<GitRepoDoc> {

    private final Serialiser2<GitRepoDoc> delegate;

    @Inject
    public GitRepoSerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(GitRepoDoc.class);
    }

    @Override
    public GitRepoDoc read(final Map<String, byte[]> data) throws IOException {
        return delegate.read(data);
    }

    @Override
    public Map<String, byte[]> write(final GitRepoDoc document) throws IOException {
        return delegate.write(document);
    }
}
