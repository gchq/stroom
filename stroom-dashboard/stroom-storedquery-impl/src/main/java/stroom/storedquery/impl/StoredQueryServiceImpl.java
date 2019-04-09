package stroom.storedquery.impl;

import stroom.dashboard.shared.FindStoredQueryCriteria;
import stroom.dashboard.shared.StoredQuery;
import stroom.security.api.Security;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionException;
import stroom.storedquery.api.StoredQueryService;
import stroom.util.AuditUtil;
import stroom.util.shared.BaseResultList;

import javax.annotation.Nonnull;
import javax.inject.Inject;

public class StoredQueryServiceImpl implements StoredQueryService {
    private final SecurityContext securityContext;
    private final Security security;
    private final StoredQueryDao dao;

    @Inject
    public StoredQueryServiceImpl(final SecurityContext securityContext, final Security security, final StoredQueryDao dao) {
        this.securityContext = securityContext;
        this.security = security;
        this.dao = dao;
    }

    @Override
    public StoredQuery create(@Nonnull final StoredQuery storedQuery) {
        AuditUtil.stamp(securityContext.getUserId(), storedQuery);
        return security.secureResult(() -> dao.create(storedQuery));
    }

    StoredQuery update(@Nonnull final StoredQuery storedQuery) {
        AuditUtil.stamp(securityContext.getUserId(), storedQuery);
        return security.secureResult(() -> dao.update(storedQuery));
    }

    boolean delete(int id) {
        return security.secureResult(() -> dao.delete(id));
    }

    StoredQuery fetch(int id) {
        final StoredQuery storedQuery = security.secureResult(() -> dao.fetch(id)).orElse(null);
        if (storedQuery != null && !storedQuery.getUpdateUser().equals(securityContext.getUserId())) {
            throw new PermissionException("This retrieved stored query belongs to another user");
        }
        return storedQuery;
    }

    BaseResultList<StoredQuery> find(FindStoredQueryCriteria criteria) {
        final String userId = securityContext.getUserId();
        criteria.setUserId(userId);

        return security.secureResult(() -> dao.find(criteria));
    }
}
