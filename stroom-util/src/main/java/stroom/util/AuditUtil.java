package stroom.util;

import stroom.util.shared.HasAuditInfo;
import stroom.util.shared.HasAuditableUserIdentity;

public final class AuditUtil {

    private AuditUtil() {
        // Utility class.
    }

    /**
     * Stamp {@code hasAuditInfo} with the create/update user/time, with the user identity
     * provided by {@code hasAuditableUserIdentity}.
     */
    public static void stamp(final HasAuditableUserIdentity hasAuditableUserIdentity,
                             final HasAuditInfo hasAuditInfo) {
        final long now = System.currentTimeMillis();
        final String userIdentityForAudit = hasAuditableUserIdentity.getUserIdentityForAudit();

        if (hasAuditInfo.getCreateTimeMs() == null) {
            hasAuditInfo.setCreateTimeMs(now);
        }
        if (hasAuditInfo.getCreateUser() == null) {
            hasAuditInfo.setCreateUser(userIdentityForAudit);
        }
        hasAuditInfo.setUpdateTimeMs(now);
        hasAuditInfo.setUpdateUser(userIdentityForAudit);
    }
}
