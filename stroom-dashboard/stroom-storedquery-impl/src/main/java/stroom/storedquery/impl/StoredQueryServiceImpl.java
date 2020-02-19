package stroom.storedquery.impl;

import stroom.dashboard.shared.FindStoredQueryCriteria;
import stroom.dashboard.shared.StoredQuery;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionException;
import stroom.storedquery.api.StoredQueryService;
import stroom.util.AuditUtil;
import stroom.util.shared.ResultPage;

import javax.annotation.Nonnull;
import javax.inject.Inject;

public class StoredQueryServiceImpl implements StoredQueryService {
    private final SecurityContext securityContext;
    private final StoredQueryDao dao;

    @Inject
    public StoredQueryServiceImpl(final SecurityContext securityContext, final StoredQueryDao dao) {
        this.securityContext = securityContext;
        this.dao = dao;
    }

    @Override
    public StoredQuery create(@Nonnull final StoredQuery storedQuery) {
        AuditUtil.stamp(securityContext.getUserId(), storedQuery);
        return securityContext.secureResult(() -> dao.create(storedQuery));
    }

    StoredQuery update(@Nonnull final StoredQuery storedQuery) {
        AuditUtil.stamp(securityContext.getUserId(), storedQuery);
        return securityContext.secureResult(() -> dao.update(storedQuery));
    }

    boolean delete(int id) {
        return securityContext.secureResult(() -> dao.delete(id));
    }

    StoredQuery fetch(int id) {
        final StoredQuery storedQuery = securityContext.secureResult(() -> dao.fetch(id)).orElse(null);
        if (storedQuery != null && !storedQuery.getUpdateUser().equals(securityContext.getUserId())) {
            throw new PermissionException("This retrieved stored query belongs to another user");
        }
        return storedQuery;
    }

    ResultPage<StoredQuery> find(FindStoredQueryCriteria criteria) {
        final String userId = securityContext.getUserId();
        criteria.setUserId(userId);

        return securityContext.secureResult(() -> dao.find(criteria));
    }
}
