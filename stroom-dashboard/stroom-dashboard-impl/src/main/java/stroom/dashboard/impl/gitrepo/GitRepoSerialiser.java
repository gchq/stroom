package stroom.dashboard.impl.gitrepo;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.gitrepo.shared.GitRepoDoc;
import stroom.util.string.EncodingUtil;

import jakarta.inject.Inject;

import java.io.IOException;
import java.util.Map;

public class GitRepoSerialiser implements DocumentSerialiser2<GitRepoDoc> {

    private static final String JS = "js";

    private final Serialiser2<GitRepoDoc> delegate;

    @Inject
    public GitRepoSerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(GitRepoDoc.class);
    }

    @Override
    public GitRepoDoc read(final Map<String, byte[]> data) throws IOException {
        final GitRepoDoc document = delegate.read(data);

        final String js = EncodingUtil.asString(data.get(JS));
        if (js != null) {
            document.setData(js);
        }
        return document;
    }

    @Override
    public Map<String, byte[]> write(final GitRepoDoc document) throws IOException {
        final String js = document.getData();
        document.setData(null);

        final Map<String, byte[]> data = delegate.write(document);

        if (js != null) {
            data.put(JS, EncodingUtil.asBytes(js));
            document.setData(js);
        }

        return data;
    }
}
