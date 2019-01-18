package stroom.util.jooq;

import stroom.util.shared.HasAuditInfo;

public final class AuditUtil {
    private AuditUtil() {
        // Utility class.
    }

    public static void stamp(final String userId, final HasAuditInfo hasAuditInfo) {
        final long now = System.currentTimeMillis();

        if (hasAuditInfo.getCreateTimeMs() == null) {
            hasAuditInfo.setCreateTimeMs(now);
        }
        if (hasAuditInfo.getCreateUser() == null) {
            hasAuditInfo.setCreateUser(userId);
        }
        hasAuditInfo.setUpdateTimeMs(now);
        hasAuditInfo.setUpdateUser(userId);
    }
}
