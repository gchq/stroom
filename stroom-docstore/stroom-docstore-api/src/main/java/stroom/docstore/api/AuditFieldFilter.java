package stroom.docstore.api;

import stroom.docstore.shared.Doc;

import java.util.function.Function;

public class AuditFieldFilter<D extends Doc> implements Function<D, D> {
    @Override
    public D apply(final D doc) {
        doc.setCreateTime(null);
        doc.setCreateUser(null);
        doc.setUpdateTime(null);
        doc.setUpdateUser(null);
        return doc;
    }
}
