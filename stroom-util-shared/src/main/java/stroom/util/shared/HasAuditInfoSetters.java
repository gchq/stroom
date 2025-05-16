package stroom.util.shared;

public interface HasAuditInfoSetters {

    void setCreateTimeMs(Long createTimeMs);

    void setCreateUser(String createUser);

    void setUpdateTimeMs(Long updateTimeMs);

    void setUpdateUser(String updateUser);


    static void set(final HasAuditInfoSetters hasAuditInfoSetters, final String user) {
        if (hasAuditInfoSetters != null) {
            final long now = System.currentTimeMillis();
            hasAuditInfoSetters.setCreateTimeMs(now);
            hasAuditInfoSetters.setUpdateTimeMs(now);
            hasAuditInfoSetters.setCreateUser(user);
            hasAuditInfoSetters.setUpdateUser(user);
        }
    }
}
