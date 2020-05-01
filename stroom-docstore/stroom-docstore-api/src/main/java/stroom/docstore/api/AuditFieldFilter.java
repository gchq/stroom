package stroom.docstore.api;

import stroom.util.shared.HasAuditInfo;

import java.util.function.Function;

public class AuditFieldFilter<D extends HasAuditInfo> implements Function<D, D> {
    @Override
    public D apply(final D doc) {
        doc.setCreateTimeMs(null);
        doc.setCreateUser(null);
        doc.setUpdateTimeMs(null);
        doc.setUpdateUser(null);
        return doc;
    }
}
